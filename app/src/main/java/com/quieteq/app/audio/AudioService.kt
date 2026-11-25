package com.quieteq.app.audio

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.res.Configuration
import android.content.res.Resources
import android.os.IBinder
import android.util.Log
import androidx.core.content.ContextCompat
import com.quieteq.app.MainActivity
import com.quieteq.app.R
import com.quieteq.app.data.BluetoothProfilesMonitor
import com.quieteq.app.data.BluetoothMonitorState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.MutableSharedFlow
import com.quieteq.app.data.DataStoreSettingsRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

class AudioService : Service() {
    companion object {
        const val START = "com.quieteq.app.START"
        const val STOP = "com.quieteq.app.STOP"
        const val BYPASS = "com.quieteq.app.BYPASS"
        const val REFRESH = "com.quieteq.app.REFRESH"
        internal val bluetoothState = MutableStateFlow(BluetoothMonitorState.STOPPED)
        internal val notificationLocaleChanged = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
        private const val CHANNEL = "equalizer"
    }
    private var bypassed = false
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var monitor: BluetoothProfilesMonitor? = null
    private var startJob: Job? = null
    private var monitorJob: Job? = null
    private var started = false
    private var failed = false
    private var generation = 0

    override fun onCreate() {
        super.onCreate()
        updateNotificationLanguage()
        scope.launch { notificationLocaleChanged.collect { updateNotificationLanguage() } }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        updateNotificationLanguage()
    }

    private fun updateNotificationLanguage() {
        val strings = ContextCompat.getContextForLanguage(this).resources
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL, strings.getString(R.string.notification_channel), NotificationManager.IMPORTANCE_LOW)
        )
        if (started) manager.notify(1, notification(strings))
    }
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            STOP -> {
                generation++
                failed = false
                started = false
                bypassed = false
                startJob?.cancel()
                monitorJob?.cancel(); monitor?.stop(); monitor = null; bluetoothState.value = BluetoothMonitorState.STOPPED
                AudioRuntime.stop()
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelfResult(startId)
            }
            REFRESH -> monitor?.refresh()
            BYPASS -> {
                bypassed = if (intent.hasExtra("bypass")) intent.getBooleanExtra("bypass", false) else !bypassed
                AudioRuntime.bypass(bypassed)
                getSystemService(NotificationManager::class.java).notify(1, notification())
            }
            START -> {
                try {
                    startForeground(1, notification())
                    if (!started) startProcessing() else { AudioRuntime.start(); monitor?.refresh() }
                } catch (e: Exception) { failStart(generation, e) }
            }
            else -> stopSelfResult(startId)
        }
        return START_NOT_STICKY
    }
    private fun startProcessing() {
        started = true
        failed = false
        val current = ++generation
        startJob = scope.launch {
            try {
                val repo = DataStoreSettingsRepository(applicationContext, scope)
                val initial = repo.loadSettings()
                AudioRuntime.initialize(applicationContext, initial.audio) { error ->
                    scope.launch { failStart(current, error) }
                }
                monitor = BluetoothProfilesMonitor(applicationContext, repo, scope).also { it.start() }
                monitorJob = scope.launch { monitor?.state?.collect { bluetoothState.value = it } }
                repo.settingsChanges.map { it.audio }.distinctUntilChanged().collect { AudioRuntime.apply(it) }
            } catch (e: CancellationException) { throw e }
              catch (e: Exception) { failStart(current, e) }
        }
    }
    private fun failStart(expectedGeneration: Int, error: Throwable) {
        if (expectedGeneration != generation) return
        Log.e("QuietEQ", "Audio service failed", error)
        generation++
        started = false
        failed = true
        startJob?.cancel()
        monitorJob?.cancel(); monitor?.stop(); monitor = null; bluetoothState.value = BluetoothMonitorState.STOPPED
        AudioRuntime.fail("Audio service could not start or load settings: ${error.message ?: error.javaClass.simpleName}")
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }
    private fun action(action: String, code: Int): PendingIntent = PendingIntent.getService(this, code, Intent(this, AudioService::class.java).setAction(action), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    private fun notification(strings: Resources = ContextCompat.getContextForLanguage(this).resources): Notification {
        val content = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        return Notification.Builder(this, CHANNEL)
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentTitle(strings.getString(R.string.notification_running))
            .setContentText(strings.getString(if (bypassed) R.string.notification_bypassed else R.string.notification_active))
            .setContentIntent(content)
            .setOngoing(true)
            .addAction(Notification.Action.Builder(null, strings.getString(if (bypassed) R.string.resume else R.string.bypass), action(BYPASS, 1)).build())
            .addAction(Notification.Action.Builder(null, strings.getString(R.string.stop), action(STOP, 2)).build())
            .build()
    }
    override fun onDestroy() {
        generation++
        startJob?.cancel()
        monitorJob?.cancel(); monitor?.stop(); bluetoothState.value = BluetoothMonitorState.STOPPED
        scope.cancel()
        if (!failed) AudioRuntime.stop()
        super.onDestroy()
    }
    override fun onBind(intent: Intent?): IBinder? = null
}
