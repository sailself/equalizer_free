package com.quieteq.app

import android.os.Bundle
import android.os.Build
import android.Manifest
import android.content.pm.PackageManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.collectAsState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.activity.result.contract.ActivityResultContracts
import com.quieteq.app.audio.AudioController
import com.quieteq.app.data.DataStoreSettingsRepository
import com.quieteq.app.ui.QuietEqApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {
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
                withContext(Dispatchers.IO) { contentResolver.openOutputStream(uri)?.use { it.write(json.toByteArray(Charsets.UTF_8)) } ?: error("Unable to open document") }
                toast("Backup exported")
            } catch (e: Exception) { toast("Export failed: ${e.message ?: "unknown error"}") }
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
                            require(output.size() + count <= 1_048_576) { "Backup is too large" }
                            output.write(buffer, 0, count)
                        }
                        output.toString("UTF-8")
                    } ?: error("Unable to open document")
                }
                repository.importBackup(json)
                if (repository.loadError.value != null) repository.retryLoading()
                toast("Backup imported")
            } catch (e: Exception) { toast("Import failed: ${e.message ?: "invalid backup"}") }
        }
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        repository = DataStoreSettingsRepository(applicationContext, appScope)
        controller = AudioController(applicationContext)
        setContent {
            val loaded by repository.isLoaded.collectAsState()
            val loadError by repository.loadError.collectAsState()
            if (loadError != null) androidx.compose.material3.AlertDialog(onDismissRequest = {}, title = { androidx.compose.material3.Text("Could not load settings") }, text = { androidx.compose.material3.Text(loadError ?: "") }, confirmButton = { androidx.compose.material3.TextButton(onClick = { repository.retryLoading() }) { androidx.compose.material3.Text("Retry") } }, dismissButton = { androidx.compose.material3.TextButton(onClick = { importDocument.launch(arrayOf("application/json", "text/plain", "*/*")) }) { androidx.compose.material3.Text("Import backup") } })
            else if (loaded) QuietEqApp(repository, controller, { exportDocument.launch("quiet-eq-backup.json") }, { importDocument.launch(arrayOf("application/json", "text/plain", "*/*")) }, { controller.refreshBluetooth() }, { startEngine() })
            else Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        }
    }
    override fun onDestroy() { appScope.cancel(); super.onDestroy() }
    private fun toast(message: String) = Toast.makeText(this, message, Toast.LENGTH_LONG).show()
}
