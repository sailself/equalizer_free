package com.quieteq.app

import android.os.Bundle
import android.os.Build
import android.Manifest
import android.content.pm.PackageManager
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.collectAsState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.res.stringResource
import androidx.activity.result.contract.ActivityResultContracts
import com.quieteq.app.audio.AudioController
import com.quieteq.app.audio.AudioService
import com.quieteq.app.data.DataStoreSettingsRepository
import com.quieteq.app.ui.QuietEqApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : AppCompatActivity() {
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private lateinit var repository: DataStoreSettingsRepository
    private lateinit var controller: AudioController
    private val notificationPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { controller.start() }
    private fun startEngine() {
        val prefs = getPreferences(MODE_PRIVATE)
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED && !prefs.getBoolean("notification_requested", false)) {
            prefs.edit().putBoolean("notification_requested", true).apply()
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else controller.start()
    }
    private val exportDocument = registerForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) appScope.launch {
            try {
                val json = repository.exportBackup()
                withContext(Dispatchers.IO) {
                    contentResolver.openOutputStream(uri)?.use { it.write(json.toByteArray(Charsets.UTF_8)) }
                        ?: error(getString(R.string.open_document_failed))
                }
                toast(getString(R.string.backup_exported))
            } catch (e: Exception) {
                toast(getString(R.string.export_failed, e.localizedMessage ?: getString(R.string.unknown_error)))
            }
        }
    }
    private val importDocument = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) appScope.launch {
            try {
                val json = withContext(Dispatchers.IO) {
                    contentResolver.openInputStream(uri)?.use { stream ->
                        val output = java.io.ByteArrayOutputStream()
                        val buffer = ByteArray(8192)
                        while (true) {
                            val count = stream.read(buffer)
                            if (count < 0) break
                            require(output.size() + count <= 1_048_576) { getString(R.string.backup_too_large) }
                            output.write(buffer, 0, count)
                        }
                        output.toString("UTF-8")
                    } ?: error(getString(R.string.open_document_failed))
                }
                repository.importBackup(json)
                if (repository.loadError.value != null) repository.retryLoading()
                toast(getString(R.string.backup_imported))
            } catch (e: Exception) {
                toast(getString(R.string.import_failed, e.localizedMessage ?: getString(R.string.invalid_backup)))
            }
        }
    }
    private fun openBackup() = importDocument.launch(arrayOf("application/json", "text/plain", "*/*"))

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        repository = DataStoreSettingsRepository(applicationContext, appScope)
        controller = AudioController(applicationContext)
        AudioService.notificationLocaleChanged.tryEmit(Unit)
        setContent {
            val loaded by repository.isLoaded.collectAsState()
            val loadError by repository.loadError.collectAsState()
            if (loadError != null) {
                AlertDialog(
                    onDismissRequest = {},
                    title = { Text(stringResource(R.string.load_settings_failed)) },
                    text = { Text(loadError.orEmpty()) },
                    confirmButton = {
                        TextButton(onClick = repository::retryLoading) { Text(stringResource(R.string.retry)) }
                    },
                    dismissButton = {
                        TextButton(onClick = ::openBackup) { Text(stringResource(R.string.import_backup)) }
                    },
                )
            } else if (loaded) {
                QuietEqApp(
                    repository, controller,
                    onExport = { exportDocument.launch("quiet-eq-backup.json") },
                    onImport = ::openBackup,
                    onBluetoothPermission = controller::refreshBluetooth,
                    onStart = ::startEngine,
                )
            } else {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            }
        }
    }
    override fun onDestroy() { appScope.cancel(); super.onDestroy() }
    private fun toast(message: String) = Toast.makeText(this, message, Toast.LENGTH_LONG).show()
}
