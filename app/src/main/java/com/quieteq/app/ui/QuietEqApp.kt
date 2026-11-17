package com.quieteq.app.ui

import android.Manifest
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.ContextWrapper
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioManager
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.quieteq.app.core.*
import com.quieteq.app.data.DataStoreSettingsRepository
import com.quieteq.app.data.defaultProfiles
import com.quieteq.app.data.BluetoothMonitorState
import com.quieteq.app.audio.AudioController
import kotlinx.coroutines.launch
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import java.util.UUID
import kotlin.math.roundToInt

private val Mint = Color(0xFF8DE4D4)
private val Dark = darkColorScheme(primary = Mint, onPrimary = Color(0xFF063B34), primaryContainer = Color(0xFF24534B), onPrimaryContainer = Color(0xFFB1F1E5), secondary = Color(0xFFACD1C7), onSecondary = Color(0xFF183B34), secondaryContainer = Color(0xFF304D47), onSecondaryContainer = Color(0xFFD2EDE5), tertiary = Color(0xFFA9CFD1), onTertiary = Color(0xFF163B3E), tertiaryContainer = Color(0xFF304F51), onTertiaryContainer = Color(0xFFC5EAEB), background = Color(0xFF101515), onBackground = Color(0xFFE6EEEB), surface = Color(0xFF191F1E), surfaceTint = Mint, surfaceVariant = Color(0xFF252D2B), surfaceContainerHighest = Color(0xFF303A37), onSurface = Color(0xFFE6EEEB), onSurfaceVariant = Color(0xFFABBAB5), outline = Color(0xFF718680), outlineVariant = Color(0xFF43534E))
private val Light = lightColorScheme(primary = Color(0xFF006C5F), onPrimary = Color.White, primaryContainer = Color(0xFFADF1E2), onPrimaryContainer = Color(0xFF00201A), secondary = Color(0xFF49665E), onSecondary = Color.White, secondaryContainer = Color(0xFFCBECE1), onSecondaryContainer = Color(0xFF062018), tertiary = Color(0xFF49696A), onTertiary = Color.White, tertiaryContainer = Color(0xFFCCE8E8), onTertiaryContainer = Color(0xFF042021), background = Color(0xFFF5FAF7), onBackground = Color(0xFF17211E), surface = Color(0xFFFFFFFF), surfaceTint = Color(0xFF006C5F), surfaceVariant = Color(0xFFE1EDE7), surfaceContainerHighest = Color(0xFFDFEAE4), onSurface = Color(0xFF17211E), onSurfaceVariant = Color(0xFF43544E), outline = Color(0xFF71847C), outlineVariant = Color(0xFFC0D0C8))
private fun hz(i: Int) = when (val f = BAND_FREQUENCIES_HZ[i]) { in 1000..16000 -> "${f / 1000}k"; else -> "$f" }
private fun db(v: Float) = if (v > 0) "+${"%.1f".format(v)}" else "%.1f".format(v)

@Composable
fun QuietEqApp(repository: DataStoreSettingsRepository, engine: AudioController, onExport: () -> Unit, onImport: () -> Unit, onBluetoothPermission: () -> Unit, onStart: () -> Unit) {
    val settings by repository.settings.collectAsState()
    val status by engine.status.collectAsState()
    val bluetoothStatus by engine.bluetoothStatus.collectAsState()
    val baseScope = rememberCoroutineScope()
    val context = LocalContext.current
    val scope = remember(baseScope, context) { CoroutineScope(baseScope.coroutineContext + SupervisorJob(baseScope.coroutineContext[Job]) + CoroutineExceptionHandler { _, error -> android.widget.Toast.makeText(context, error.message ?: "Could not save changes", android.widget.Toast.LENGTH_LONG).show() }) }
    var optimisticAudio by remember { mutableStateOf(settings.audio) }
    var pendingWrites by remember { mutableIntStateOf(0) }
    LaunchedEffect(settings.audio, pendingWrites) { if (pendingWrites == 0) optimisticAudio = settings.audio }
    var page by remember { mutableIntStateOf(0) }
    val dark = when (settings.theme) { ThemeMode.DARK -> true; ThemeMode.LIGHT -> false; ThemeMode.SYSTEM -> androidx.compose.foundation.isSystemInDarkTheme() }
    val view = LocalView.current
    SideEffect {
        var current: Context? = view.context
        while (current is ContextWrapper && current !is Activity) current = current.baseContext
        (current as? Activity)?.window?.let { window ->
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = !dark
                isAppearanceLightNavigationBars = !dark
            }
        }
    }
    MaterialTheme(colorScheme = if (dark) Dark else Light) {
        Scaffold(containerColor = MaterialTheme.colorScheme.background, bottomBar = {
            NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
                listOf("Sound", "Profiles", "Settings").forEachIndexed { index, label ->
                    NavigationBarItem(selected = page == index, onClick = { page = index }, icon = { Icon(when(index) { 0 -> Icons.Default.GraphicEq; 1 -> Icons.Default.Bookmarks; else -> Icons.Default.Settings }, null) }, label = { Text(label) })
                }
            }
        }) { padding ->
            val update: (AudioSettings) -> Unit = { value ->
                optimisticAudio = value.normalized()
                pendingWrites++
                scope.launch { try { repository.updateAudio(value.normalized()) } finally { pendingWrites-- } }
            }
            when (page) {
                0 -> SoundScreen(settings.copy(audio = optimisticAudio), status, Modifier.padding(padding), update, { if (status.phase == EnginePhase.STOPPED || status.phase == EnginePhase.ERROR) onStart() else engine.stop() })
                1 -> ProfilesScreen(settings.copy(audio = optimisticAudio), repository, onExport, onImport, Modifier.padding(padding), update, onBluetoothPermission, bluetoothStatus)
                else -> SettingsScreen(settings.copy(audio = optimisticAudio), repository, Modifier.padding(padding), update)
            }
        }
    }
}

@Composable private fun Screen(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(16.dp), content = { Spacer(Modifier.height(8.dp)); content(); Spacer(Modifier.height(12.dp)) })
}
@Composable private fun Header(kicker: String, title: String, subtitle: String) {
    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Text(kicker.uppercase(), color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelMedium, letterSpacing = 1.6.sp, fontWeight = FontWeight.Bold)
        Text(title, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.SemiBold)
        Text(subtitle, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
    }
}
@Composable private fun Panel(content: @Composable ColumnScope.() -> Unit) {
    Surface(color = MaterialTheme.colorScheme.surface, shape = RoundedCornerShape(22.dp), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp), content = content)
    }
}
@Composable private fun SectionTitle(title: String, detail: String? = null) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
        Text(title, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.titleMedium)
        if (detail != null) Text(detail, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelMedium)
    }
}

@Composable private fun SoundScreen(s: AppSettings, status: AudioStatus, modifier: Modifier, update: (AudioSettings) -> Unit, toggle: () -> Unit) {
    val a = s.audio
    var band by remember { mutableIntStateOf(5) }
    val running = status.phase != EnginePhase.STOPPED && status.phase != EnginePhase.ERROR
    Screen(modifier) {
        Header("Your sound, your way", "Quiet EQ", "Shape the sound around you.")
        var presetMenu by remember { mutableStateOf(false) }
        Box {
            OutlinedButton(onClick = { presetMenu = true }) { Icon(Icons.Default.GraphicEq, null); Spacer(Modifier.width(8.dp)); Text(s.profiles.find { it.id == s.selectedProfileId }?.name ?: defaultProfiles.find { it.settings.gains == a.gains }?.let { "${it.name} curve" } ?: "Custom sound"); Icon(Icons.Default.ArrowDropDown, null) }
            DropdownMenu(expanded = presetMenu, onDismissRequest = { presetMenu = false }) { defaultProfiles.forEach { preset -> DropdownMenuItem(text = { Text(preset.name) }, onClick = { presetMenu = false; update(a.copy(gains = preset.settings.gains)) }) } }
        }
        Panel {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(46.dp).background(MaterialTheme.colorScheme.primary.copy(alpha = .14f), RoundedCornerShape(15.dp)), contentAlignment = Alignment.Center) { Icon(Icons.Default.PowerSettingsNew, null, tint = MaterialTheme.colorScheme.primary) }
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) { Text(if (running) "Engine on" else "Engine off", fontWeight = FontWeight.SemiBold); Text(status.message, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall) }
                Switch(checked = running, onCheckedChange = { toggle() }, modifier = Modifier.semantics { contentDescription = "Audio engine" })
            }
            if (status.backend != null || status.sessions > 0) Text(listOfNotNull(status.backend, "${status.sessions} session${if (status.sessions == 1) "" else "s"}").joinToString("  ·  "), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Panel {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) { SectionTitle("Equalizer"); Text(if (status.capabilities.bandFrequenciesHz.isNotEmpty() && status.capabilities.bandFrequenciesHz != BAND_FREQUENCIES_HZ) "10-band curve mapped to ${status.capabilities.bandFrequenciesHz.size} device bands" else "10-band tone shaping", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall) }
                Switch(a.eqEnabled, { update(a.copy(eqEnabled = it)) }, modifier = Modifier.semantics { contentDescription = "Equalizer enabled" })
            }
            if (status.capabilities.bandFrequenciesHz.isNotEmpty() && status.capabilities.bandFrequenciesHz != BAND_FREQUENCIES_HZ) Text("Device bands: " + status.capabilities.bandFrequenciesHz.joinToString(", ") { if (it >= 1000) "${it / 1000f}k" else "$it" } + " Hz", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            status.capabilities.gainRangeDb?.let { range -> if (range.first > -12f || range.second < 12f) Text("Device gain range: ${db(range.first)} to ${db(range.second)} dB; values outside this range are limited.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            EqGraph(a.gains, band, { band = it }) { i, gain -> update(a.copy(gains = a.gains.toMutableList().also { it[i] = gain })) }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { BAND_FREQUENCIES_HZ.indices.forEach { i -> Text(hz(i), modifier = Modifier.clickable { band = i }.padding(vertical = 8.dp), color = if (i == band) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 10.sp) } }
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { band = (band - 1).coerceAtLeast(0) }, enabled = band > 0) { Icon(Icons.Default.ChevronLeft, "Previous frequency band") }
                Column(Modifier.weight(1f)) { Text("${hz(band)} Hz", fontWeight = FontWeight.SemiBold); Text("Selected band", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                IconButton(onClick = { band = (band + 1).coerceAtMost(9) }, enabled = band < 9) { Icon(Icons.Default.ChevronRight, "Next frequency band") }
                Text("${db(a.gains[band])} dB", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
            }
            Slider(value = a.gains[band], onValueChange = { v -> update(a.copy(gains = a.gains.toMutableList().also { it[band] = (v * 2).roundToInt() / 2f })) }, valueRange = -12f..12f, modifier = Modifier.semantics { contentDescription = "${BAND_FREQUENCIES_HZ[band]} hertz gain" })
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { update(a.copy(gains = a.gains.toMutableList().also { it[band] = (it[band] - .5f).coerceAtLeast(-12f) })) }, modifier = Modifier.weight(1f)) { Text("−  0.5 dB") }
                OutlinedButton(onClick = { update(a.copy(gains = a.gains.toMutableList().also { it[band] = (it[band] + .5f).coerceAtMost(12f) })) }, modifier = Modifier.weight(1f)) { Text("+  0.5 dB") }
            }
            TextButton(onClick = { update(a.copy(gains = List(BAND_COUNT) { 0f })) }, modifier = Modifier.align(Alignment.End)) { Text("Reset equalizer") }
        }
        Panel {
            SectionTitle("Sound enhancements", "DEVICE DEPENDENT")
            LevelControl("Bass", a.bass.toFloat(), 0f..100f, "${a.bass}%", status.capabilities.bass, { update(a.copy(bass = it.roundToInt())) })
            LevelControl("Loudness", a.loudness.toFloat(), 0f..100f, "${a.loudness}%", status.capabilities.loudness, { update(a.copy(loudness = it.roundToInt())) })
            LevelControl("Balance", a.balance, -1f..1f, if (a.balance == 0f) "Center" else if (a.balance < 0) "L ${(a.balance * -100).roundToInt()}" else "R ${(a.balance * 100).roundToInt()}", status.capabilities.balance, { update(a.copy(balance = it)) })
            if (status.capabilities.virtualizer) {
                if (status.capabilities.virtualizerStrength) LevelControl("Virtualizer", a.virtualizer.toFloat(), 0f..100f, "${a.virtualizer}%", true, { update(a.copy(virtualizer = it.roundToInt())) })
                else SettingSwitch("Virtualizer", "This device supports on or off only.", a.virtualizer > 0) { update(a.copy(virtualizer = if (it) 100 else 0)) }
            }
            if (!running) Text("Start the engine to check which enhancements are available.", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
            if (s.showReverb) Text("Reverb is unavailable: playback apps must provide their own reverb send.", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
        }
        if (s.showVolume) VolumePanel()
    }
}

@Composable private fun EqGraph(gains: List<Float>, selected: Int, onSelect: (Int) -> Unit, onGain: (Int, Float) -> Unit) {
    val currentSelect by rememberUpdatedState(onSelect)
    val currentGain by rememberUpdatedState(onGain)
    val primary = MaterialTheme.colorScheme.primary
    val grid = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = .24f)
    Canvas(Modifier.fillMaxWidth().height(150.dp).semantics { contentDescription = "Equalizer curve, select a frequency below to adjust" }.pointerInput(Unit) { detectTapGestures { p -> currentSelect(((p.x / size.width) * 9).roundToInt().coerceIn(0, 9)) } }.pointerInput(Unit) {
        var active = 0
        detectDragGestures(onDragStart = { p -> active = ((p.x / size.width) * 9).roundToInt().coerceIn(0, 9); currentSelect(active) }, onDrag = { change, _ ->
            val inset = 8.dp.toPx(); val height = (size.height - 2 * inset).coerceAtLeast(1f)
            val value = ((12f - ((change.position.y - inset) / height) * 24f) * 2).roundToInt() / 2f
            currentGain(active, value.coerceIn(-12f, 12f)); change.consume()
        })
    }) {
        val inset = 8.dp.toPx(); val w = size.width - 2 * inset; val h = size.height - 2 * inset
        for (i in 0..4) { val y = inset + h * i / 4; drawLine(grid, Offset(inset, y), Offset(size.width - inset, y), strokeWidth = 1.dp.toPx()) }
        val points = (0..9).map { i -> Offset(inset + w * i / 9, inset + h * (12f - gains.getOrElse(i) { 0f }.coerceIn(-12f, 12f)) / 24f) }
        val path = Path().apply { moveTo(points[0].x, points[0].y); points.drop(1).forEach { lineTo(it.x, it.y) } }
        val fill = Path().apply { moveTo(points[0].x, inset + h); points.forEach { lineTo(it.x, it.y) }; lineTo(points.last().x, inset + h); close() }
        drawPath(fill, primary.copy(alpha = .06f))
        drawPath(path, primary, style = Stroke(width = 2.5.dp.toPx()))
        points.forEachIndexed { i, p -> drawCircle(primary, if (i == selected) 6.dp.toPx() else 3.dp.toPx(), p) }
    }
}
@Composable private fun LevelControl(label: String, value: Float, range: ClosedFloatingPointRange<Float>, display: String, enabled: Boolean, onChange: (Float) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(0.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text(label, style = MaterialTheme.typography.bodyMedium); Text(if (enabled) display else "Unavailable", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        Slider(value.coerceIn(range.start, range.endInclusive), onChange, valueRange = range, enabled = enabled, modifier = Modifier.semantics { contentDescription = label })
    }
}
@Composable private fun VolumePanel() {
    val context = LocalContext.current
    val am = remember { context.getSystemService(Context.AUDIO_SERVICE) as AudioManager }
    var volume by remember { mutableFloatStateOf(am.getStreamVolume(AudioManager.STREAM_MUSIC).toFloat()) }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(context, lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_RESUME) volume = am.getStreamVolume(AudioManager.STREAM_MUSIC).toFloat() }
        lifecycleOwner.lifecycle.addObserver(observer)
        val receiver = object : android.content.BroadcastReceiver() { override fun onReceive(c: Context?, intent: Intent?) { volume = am.getStreamVolume(AudioManager.STREAM_MUSIC).toFloat() } }
        val filter = android.content.IntentFilter("android.media.VOLUME_CHANGED_ACTION")
        if (Build.VERSION.SDK_INT >= 33) context.registerReceiver(receiver, filter, Context.RECEIVER_EXPORTED) else @Suppress("UnspecifiedRegisterReceiverFlag") context.registerReceiver(receiver, filter)
        onDispose { context.unregisterReceiver(receiver); lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    val max = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC).toFloat().coerceAtLeast(1f)
    Panel { SectionTitle("Media volume", "SYSTEM"); LevelControl("Volume", volume, 0f..max, "${(volume / max * 100).roundToInt()}%", true) { volume = it; am.setStreamVolume(AudioManager.STREAM_MUSIC, it.roundToInt(), 0) } }
}

@Composable private fun ProfilesScreen(s: AppSettings, repository: DataStoreSettingsRepository, onExport: () -> Unit, onImport: () -> Unit, modifier: Modifier, update: (AudioSettings) -> Unit, onBluetoothPermission: () -> Unit, bluetoothStatus: BluetoothMonitorState) {
    val baseScope = rememberCoroutineScope()
    val context = LocalContext.current
    val scope = remember(baseScope, context) { CoroutineScope(baseScope.coroutineContext + SupervisorJob(baseScope.coroutineContext[Job]) + CoroutineExceptionHandler { _, error -> android.widget.Toast.makeText(context, error.message ?: "Could not save changes", android.widget.Toast.LENGTH_LONG).show() }) }
    var editing by remember { mutableStateOf<Profile?>(null) }
    var creating by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf<Profile?>(null) }
    var assigning by remember { mutableStateOf<Profile?>(null) }
    Screen(modifier) {
        Header("Make it yours", "Profiles", "Save a sound for every moment.")
        Panel {
            SectionTitle("Starting points", "PRESETS")
            Text("Choose a curve, then make it your own.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            defaultProfiles.forEach { preset ->
                val name = preset.name; val gains = preset.settings.gains
                Row(Modifier.fillMaxWidth().clickable { update(s.audio.copy(gains = gains)) }.heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.GraphicEq, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp)); Spacer(Modifier.width(12.dp)); Text(name, Modifier.weight(1f)); Icon(Icons.Default.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        Panel {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) { Column(Modifier.weight(1f)) { SectionTitle("Your profiles"); Text("Keep your favorite settings close.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }; FilledTonalButton(onClick = { creating = true }) { Icon(Icons.Default.Add, null); Spacer(Modifier.width(4.dp)); Text("Save") } }
            if (s.profiles.none { p -> defaultProfiles.any { it.id == p.id }.not() }) Text("No saved profiles yet. Save your current sound to get started.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(vertical = 12.dp))
            s.profiles.filterNot { p -> defaultProfiles.any { it.id == p.id } }.forEach { p ->
                var menu by remember(p.id) { mutableStateOf(false) }
                Row(Modifier.fillMaxWidth().heightIn(min = 56.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f).clickable { scope.launch { repository.selectProfile(p.id) } }.padding(vertical = 8.dp)) {
                        Text(p.name, fontWeight = FontWeight.Medium)
                        Text(if (s.selectedProfileId == p.id) "Selected" else "Tap to load", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                    }
                    Box { IconButton(onClick = { menu = true }) { Icon(Icons.Default.MoreVert, "Options for ${p.name}") }; DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        DropdownMenuItem(text = { Text("Rename") }, onClick = { menu = false; editing = p })
                        DropdownMenuItem(text = { Text("Duplicate") }, onClick = { menu = false; scope.launch { repository.duplicateProfile(p.id, "${p.name.take(75)} copy") } })
                        DropdownMenuItem(text = { Text("Assign to Bluetooth device") }, onClick = { menu = false; assigning = p })
                        DropdownMenuItem(text = { Text("Delete") }, onClick = { menu = false; deleting = p })
                    } }
                }
            }
        }
        Panel {
            SectionTitle("Bluetooth profiles")
            Text(when (bluetoothStatus) {
                BluetoothMonitorState.STOPPED -> "Turn on the engine to use device assignments."
                BluetoothMonitorState.PERMISSION_REQUIRED -> "Nearby devices permission is required for automatic profiles."
                BluetoothMonitorState.WAITING -> "Checking connected audio devices…"
                BluetoothMonitorState.NO_DEVICE -> "No paired audio device is connected."
                BluetoothMonitorState.MULTIPLE_DEVICES -> "Multiple audio devices are connected; automatic selection is paused."
                BluetoothMonitorState.CONNECTED -> "An audio device is connected. Its assigned profile loads automatically."
            }, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
            Text("Use the menu on a saved profile to assign it to a paired device.", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
        }
        Panel { SectionTitle("Backup"); Text("Move your profiles and settings to another device.", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium); Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { OutlinedButton(onClick = onExport, modifier = Modifier.weight(1f)) { Text("Export") }; OutlinedButton(onClick = onImport, modifier = Modifier.weight(1f)) { Text("Import") } } }
    }
    if (creating || editing != null) {
        var name by remember(creating, editing?.id) { mutableStateOf(editing?.name ?: "") }
        AlertDialog(onDismissRequest = { creating = false; editing = null }, title = { Text(if (creating) "Save profile" else "Rename profile") }, text = { OutlinedTextField(value = name, onValueChange = { name = it.take(80) }, label = { Text("Profile name") }, singleLine = true) }, confirmButton = { TextButton(enabled = name.isNotBlank(), onClick = { val editingId = editing?.id; val p = editing?.copy(name = name.trim()) ?: Profile(UUID.randomUUID().toString(), name.trim(), s.audio); scope.launch { if (p.id == editingId) repository.renameProfile(p.id, p.name) else repository.saveProfile(p) }; creating = false; editing = null }) { Text("Save") } }, dismissButton = { TextButton(onClick = { creating = false; editing = null }) { Text("Cancel") } })
    }
    deleting?.let { p -> AlertDialog(onDismissRequest = { deleting = null }, title = { Text("Delete ${p.name}?") }, text = { Text("This removes the saved profile and its device assignments.") }, confirmButton = { TextButton(onClick = { scope.launch { repository.deleteProfile(p.id) }; deleting = null }) { Text("Delete") } }, dismissButton = { TextButton(onClick = { deleting = null }) { Text("Cancel") } }) }
    assigning?.let { p -> DeviceDialog(p, s, repository, onBluetoothPermission) { assigning = null } }
}

@Composable private fun DeviceDialog(profile: Profile, settings: AppSettings, repo: SettingsRepository, onBluetoothPermission: () -> Unit, dismiss: () -> Unit) {
    val baseScope = rememberCoroutineScope()
    val context = LocalContext.current
    val scope = remember(baseScope, context) { CoroutineScope(baseScope.coroutineContext + SupervisorJob(baseScope.coroutineContext[Job]) + CoroutineExceptionHandler { _, error -> android.widget.Toast.makeText(context, error.message ?: "Could not save changes", android.widget.Toast.LENGTH_LONG).show() }) }
    var refresh by remember { mutableIntStateOf(0) }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { refresh++; onBluetoothPermission() }
    val allowed = Build.VERSION.SDK_INT < 31 || context.checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED
    val devices = remember(allowed, refresh) { if (!allowed) emptyList() else try { val manager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager; manager.adapter?.bondedDevices?.map { it.address to (it.name ?: "Bluetooth device") }?.sortedBy { it.second } ?: emptyList() } catch (_: SecurityException) { emptyList() } }
    AlertDialog(onDismissRequest = dismiss, title = { Text("Assign ${profile.name}") }, text = {
        Column(Modifier.heightIn(max = 400.dp).verticalScroll(rememberScrollState())) {
            Text("Load this profile when a paired device connects while the engine is on.", style = MaterialTheme.typography.bodyMedium)
            if (!allowed) TextButton(onClick = { if (Build.VERSION.SDK_INT >= 31) permission.launch(Manifest.permission.BLUETOOTH_CONNECT) }) { Text("Allow nearby devices") }
            else if (devices.isEmpty()) Text("No paired Bluetooth devices found.", modifier = Modifier.padding(top = 12.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
            devices.forEach { (key, name) -> Row(Modifier.fillMaxWidth().heightIn(min = 56.dp), verticalAlignment = Alignment.CenterVertically) { Column(Modifier.weight(1f)) { Text(name); Text(key, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }; Switch(settings.assignments[key] == profile.id, { checked -> scope.launch { repo.assignProfile(key, if (checked) profile.id else null) } }, modifier = Modifier.semantics { contentDescription = "Assign $name" }) } }
        }
    }, confirmButton = { TextButton(onClick = dismiss) { Text("Done") } })
}

@Composable private fun SettingsScreen(s: AppSettings, repository: DataStoreSettingsRepository, modifier: Modifier, update: (AudioSettings) -> Unit) {
    val baseScope = rememberCoroutineScope()
    val context = LocalContext.current
    val scope = remember(baseScope, context) { CoroutineScope(baseScope.coroutineContext + SupervisorJob(baseScope.coroutineContext[Job]) + CoroutineExceptionHandler { _, error -> android.widget.Toast.makeText(context, error.message ?: "Could not save changes", android.widget.Toast.LENGTH_LONG).show() }) }; val a = s.audio
    fun prefs(transform: (AppSettings) -> AppSettings) { scope.launch { repository.modifyPreferences(transform) } }
    Screen(modifier) {
        Header("Fine tune", "Settings", "Make Quiet EQ feel like yours.")
        Panel {
            SectionTitle("Appearance")
            Text("Theme", style = MaterialTheme.typography.bodyMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { ThemeMode.entries.forEach { theme -> FilterChip(selected = s.theme == theme, onClick = { prefs { it.copy(theme = theme) } }, label = { Text(theme.name.lowercase().replaceFirstChar { it.uppercase() }) }) } }
            SettingSwitch("Show media volume", "Control the system media volume on Sound.", s.showVolume) { prefs { current -> current.copy(showVolume = it) } }
            SettingSwitch("Show reverb information", "See why reverb is unavailable.", s.showReverb) { prefs { current -> current.copy(showReverb = it) } }
        }
        Panel {
            SectionTitle("Audio engine")
            Text("Attachment mode", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
            Text("Auto is recommended. Global attachment is experimental and may not affect every player.", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
            AttachmentMode.entries.forEach { mode -> Row(Modifier.fillMaxWidth().clickable { update(a.copy(attachmentMode = mode)) }.heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically) { RadioButton(a.attachmentMode == mode, onClick = { update(a.copy(attachmentMode = mode)) }); Spacer(Modifier.width(8.dp)); Text(when(mode) { AttachmentMode.AUTO -> "Auto"; AttachmentMode.PLAYERS_ONLY -> "Players only"; AttachmentMode.GLOBAL -> "Global (experimental)" }) } }
            SettingSwitch("Prefer legacy effects", "Use the legacy equalizer when available.", a.preferLegacy) { update(a.copy(preferLegacy = it)) }
            SettingSwitch("Auto headroom", "Reduce clipping when boosting frequencies.", a.autoHeadroom) { update(a.copy(autoHeadroom = it)) }
            LevelControl("Processing frame", a.frameDurationMs, 5f..100f, "${a.frameDurationMs.roundToInt()} ms", true) { update(a.copy(frameDurationMs = it.roundToInt().toFloat())) }
        }
        Panel {
            SectionTitle("System")
            Text("Android controls notifications and battery restrictions.", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
            TextButton(onClick = { try { context.startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)) } catch (_: Exception) { context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, android.net.Uri.parse("package:${context.packageName}"))) } }) { Text("Notification settings") }
            TextButton(onClick = { context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, android.net.Uri.parse("package:${context.packageName}"))) }) { Text("App and battery settings") }
        }
    }
}
@Composable private fun SettingSwitch(title: String, description: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min = 64.dp), verticalAlignment = Alignment.CenterVertically) { Column(Modifier.weight(1f)) { Text(title, fontWeight = FontWeight.Medium, style = MaterialTheme.typography.bodyMedium); Text(description, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall) }; Switch(checked, onChange, modifier = Modifier.semantics { contentDescription = title }) }
}
