package com.quieteq.app.data

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothLeAudio
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

enum class BluetoothMonitorState { STOPPED, PERMISSION_REQUIRED, WAITING, NO_DEVICE, MULTIPLE_DEVICES, CONNECTED }

data class PairedDevice(val key: String, val name: String)

/** Tracks connected audio profiles only while its owner is active. No discovery is performed. */
class BluetoothProfilesMonitor(context: Context, private val repository: DataStoreSettingsRepository, private val scope: CoroutineScope) {
    private val app = context.applicationContext
    private val adapter: BluetoothAdapter? = app.getSystemService(BluetoothManager::class.java)?.adapter
    private val proxies = mutableMapOf<Int, BluetoothProfile>()
    private val pending = mutableSetOf<Int>()
    private var running = false
    private var lastSelection: Pair<String, String?>? = null
    private var assignmentJob: Job? = null
    private var checkJob: Job? = null
    private val profiles = listOf(BluetoothProfile.A2DP) + if (Build.VERSION.SDK_INT >= 33) listOf(BluetoothProfile.LE_AUDIO) else emptyList()
    private val mutableState = MutableStateFlow(BluetoothMonitorState.STOPPED)
    val state = mutableState.asStateFlow()
    fun hasPermission(): Boolean = Build.VERSION.SDK_INT < 31 || app.checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED
    fun pairedDevices(): List<PairedDevice> {
        if (!hasPermission()) return emptyList()
        return try { adapter?.bondedDevices.orEmpty().map { PairedDevice(it.address, it.name ?: it.address) }.sortedBy { it.name.lowercase() } } catch (_: SecurityException) { emptyList() }
    }
    private val listener = object : BluetoothProfile.ServiceListener {
        override fun onServiceConnected(profile: Int, proxy: BluetoothProfile) {
            if (!running) { try { adapter?.closeProfileProxy(profile, proxy) } catch (_: SecurityException) {}; return }
            proxies[profile] = proxy
            pending.remove(profile)
            scheduleCheck()
        }
        override fun onServiceDisconnected(profile: Int) {
            proxies.remove(profile)
            // An unavailable profile prevents an unambiguous conclusion.
            pending.add(profile)
            mutableState.value = BluetoothMonitorState.WAITING
            lastSelection = null
        }
    }
    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == BluetoothAdapter.ACTION_STATE_CHANGED && intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.ERROR) == BluetoothAdapter.STATE_ON && hasPermission()) {
                try {
                    val missing = profiles.filterNot { it in proxies }
                    pending.addAll(missing)
                    for (profile in missing) if (adapter?.getProfileProxy(app, listener, profile) != true) pending.remove(profile)
                } catch (_: SecurityException) { mutableState.value = BluetoothMonitorState.PERMISSION_REQUIRED }
            }
            scheduleCheck()
        }
    }
    fun start() {
        if (running) return
        if (!hasPermission()) { mutableState.value = BluetoothMonitorState.PERMISSION_REQUIRED; return }
        if (adapter == null) { mutableState.value = BluetoothMonitorState.NO_DEVICE; return }
        mutableState.value = BluetoothMonitorState.WAITING
        running = true
        val filter = IntentFilter().apply {
            addAction(BluetoothAdapter.ACTION_STATE_CHANGED)
            addAction("android.bluetooth.a2dp.profile.action.CONNECTION_STATE_CHANGED")
            if (Build.VERSION.SDK_INT >= 33) addAction(BluetoothLeAudio.ACTION_LE_AUDIO_CONNECTION_STATE_CHANGED)
        }
        if (Build.VERSION.SDK_INT >= 33) app.registerReceiver(receiver, filter, Context.RECEIVER_EXPORTED) else @Suppress("UnspecifiedRegisterReceiverFlag") app.registerReceiver(receiver, filter)
        assignmentJob = scope.launch { repository.settingsChanges.map { it.assignments }.distinctUntilChanged().collect { scheduleCheck() } }
        try {
            pending.addAll(profiles)
            for (profile in profiles) {
                if (!adapter.getProfileProxy(app, listener, profile)) pending.remove(profile)
            }
            scheduleCheck()
        } catch (_: SecurityException) { stop(); mutableState.value = BluetoothMonitorState.PERMISSION_REQUIRED }
    }
    /** Call after a permission grant if the monitor was already started without permission. */
    fun refresh() { if (!running) start() else scheduleCheck() }
    private fun scheduleCheck() {
        checkJob?.cancel()
        checkJob = scope.launch {
            delay(300)
            if (!running) return@launch
            if (!hasPermission()) { mutableState.value = BluetoothMonitorState.PERMISSION_REQUIRED; return@launch }
            if (pending.isNotEmpty()) { mutableState.value = BluetoothMonitorState.WAITING; return@launch }
            val connected = try { proxies.values.flatMap { it.connectedDevices }.map(BluetoothDevice::getAddress).toSet() } catch (_: SecurityException) { mutableState.value = BluetoothMonitorState.PERMISSION_REQUIRED; return@launch }
            val device = connected.singleOrNull()
            if (device == null) { lastSelection = null; mutableState.value = if (connected.isEmpty()) BluetoothMonitorState.NO_DEVICE else BluetoothMonitorState.MULTIPLE_DEVICES; return@launch }
            mutableState.value = BluetoothMonitorState.CONNECTED
            val assignment = repository.loadSettings().assignments[device]
            val selection = device to assignment
            if (selection != lastSelection) {
                if (assignment != null) repository.applyDeviceProfile(device)
                lastSelection = selection
            }
        }
    }
    fun stop() {
        if (!running) return
        running = false
        mutableState.value = BluetoothMonitorState.STOPPED
        checkJob?.cancel()
        assignmentJob?.cancel()
        try { app.unregisterReceiver(receiver) } catch (_: IllegalArgumentException) {}
        for ((profile, proxy) in proxies) try { adapter?.closeProfileProxy(profile, proxy) } catch (_: SecurityException) {}
        proxies.clear(); pending.clear(); lastSelection = null
    }
}
