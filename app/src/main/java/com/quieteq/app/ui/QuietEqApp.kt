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
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.quieteq.app.R
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
import java.text.NumberFormat
import java.util.Locale
import java.util.UUID
import kotlin.math.roundToInt

private fun hz(i: Int) = when (val f = BAND_FREQUENCIES_HZ[i]) { in 1000..16000 -> "${f / 1000}k"; else -> "$f" }
private fun db(gain: Float, locale: Locale): String =
    String.format(locale, if (gain > 0f) "%+.1f" else "%.1f", gain)

@Composable
private fun rememberSettingsScope(): CoroutineScope {
    val base = rememberCoroutineScope()
    val context = LocalContext.current
    return remember(base, context) {
        val handler = CoroutineExceptionHandler { _, error ->
            val detail = error.localizedMessage ?: context.getString(R.string.unknown_error)
            android.widget.Toast.makeText(context, context.getString(R.string.save_failed, detail), android.widget.Toast.LENGTH_LONG).show()
        }
        CoroutineScope(base.coroutineContext + SupervisorJob(base.coroutineContext[Job]) + handler)
    }
}

@Composable
fun QuietEqApp(repository: DataStoreSettingsRepository, engine: AudioController, onExport: () -> Unit, onImport: () -> Unit, onBluetoothPermission: () -> Unit, onStart: () -> Unit) {
    val settings by repository.settings.collectAsState()
    val status by engine.status.collectAsState()
    val bluetoothStatus by engine.bluetoothStatus.collectAsState()
    val scope = rememberSettingsScope()
    var optimisticAudio by remember { mutableStateOf(settings.audio) }
    var pendingWrites by remember { mutableIntStateOf(0) }
    LaunchedEffect(settings.audio, pendingWrites) { if (pendingWrites == 0) optimisticAudio = settings.audio }
    var page by rememberSaveable { mutableIntStateOf(0) }
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
    MaterialTheme(colorScheme = quietEqColorScheme(settings.palette, dark)) {
        Scaffold(containerColor = MaterialTheme.colorScheme.background, bottomBar = {
            NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
                listOf(R.string.nav_sound, R.string.nav_profiles, R.string.nav_settings).forEachIndexed { index, label ->
                    NavigationBarItem(selected = page == index, onClick = { page = index }, icon = { Icon(when(index) { 0 -> Icons.Default.GraphicEq; 1 -> Icons.Default.Bookmarks; else -> Icons.Default.Settings }, null) }, label = { Text(stringResource(label)) })
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
                else -> SettingsScreen(settings.copy(audio = optimisticAudio), repository, dark, Modifier.padding(padding), update)
            }
        }
    }
}

@Composable private fun Screen(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(16.dp), content = { Spacer(Modifier.height(8.dp)); content(); Spacer(Modifier.height(12.dp)) })
}
@Composable private fun Header(kicker: String, title: String, subtitle: String) {
    val locale = LocalConfiguration.current.locales[0]
    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Text(kicker.uppercase(locale), color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelMedium, letterSpacing = 1.6.sp, fontWeight = FontWeight.Bold)
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
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, Modifier.weight(1f), fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.titleMedium)
        if (detail != null) Text(detail, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelMedium)
    }
}

@Composable
private fun presetName(profile: Profile): String {
    if (defaultProfiles.none { it.id == profile.id && it.name == profile.name }) return profile.name
    return when (profile.id) {
        "flat" -> stringResource(R.string.preset_flat)
        "bass-boost" -> stringResource(R.string.preset_bass_boost)
        "treble-boost" -> stringResource(R.string.preset_treble_boost)
        "vocal" -> stringResource(R.string.preset_vocal)
        "balanced" -> stringResource(R.string.preset_balanced)
        else -> profile.name
    }
}

@Composable
private fun statusMessage(status: AudioStatus): String = when (status.phase) {
    EnginePhase.STOPPED -> stringResource(R.string.status_stopped)
    EnginePhase.STARTING -> stringResource(R.string.status_starting)
    EnginePhase.WAITING -> stringResource(R.string.status_waiting)
    EnginePhase.ACTIVE -> if (status.globalAttachment) {
        stringResource(R.string.status_global)
    } else {
        pluralStringResource(R.plurals.status_player_sessions, status.sessions, status.sessions)
    }
    EnginePhase.BYPASSED -> stringResource(R.string.status_bypassed)
    EnginePhase.DEGRADED -> stringResource(R.string.status_degraded)
    EnginePhase.CONTROL_LOST -> stringResource(R.string.status_control_lost)
    EnginePhase.ERROR -> stringResource(if (status.startFailed) R.string.status_start_failed else R.string.status_attach_failed)
}

@Composable
private fun backendName(backend: AudioBackend): String = stringResource(when (backend) {
    AudioBackend.DYNAMICS_PROCESSING -> R.string.backend_dynamics
    AudioBackend.LEGACY_EQUALIZER -> R.string.backend_legacy
    AudioBackend.MIXED -> R.string.backend_mixed
})

@Composable private fun SoundScreen(s: AppSettings, status: AudioStatus, modifier: Modifier, update: (AudioSettings) -> Unit, toggle: () -> Unit) {
    val a = s.audio
    val locale = LocalConfiguration.current.locales[0]
    var band by rememberSaveable { mutableIntStateOf(5) }
    val running = status.phase != EnginePhase.STOPPED && status.phase != EnginePhase.ERROR
    Screen(modifier) {
        Header(stringResource(R.string.sound_kicker), stringResource(R.string.app_name), stringResource(R.string.sound_subtitle))
        var presetMenu by remember { mutableStateOf(false) }
        Box {
            OutlinedButton(onClick = { presetMenu = true }) {
                Icon(Icons.Default.GraphicEq, null)
                Spacer(Modifier.width(8.dp))
                val selected = s.profiles.find { it.id == s.selectedProfileId }
                val matchingCurve = defaultProfiles.find { it.settings.gains == a.gains }
                Text(when {
                    selected != null -> presetName(selected)
                    matchingCurve != null -> stringResource(R.string.preset_curve, presetName(matchingCurve))
                    else -> stringResource(R.string.custom_sound)
                })
                Icon(Icons.Default.ArrowDropDown, null)
            }
            DropdownMenu(expanded = presetMenu, onDismissRequest = { presetMenu = false }) {
                defaultProfiles.forEach { preset ->
                    DropdownMenuItem(
                        text = { Text(presetName(preset)) },
                        onClick = { presetMenu = false; update(a.copy(gains = preset.settings.gains)) },
                    )
                }
            }
        }
        Panel {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(46.dp).background(MaterialTheme.colorScheme.primary.copy(alpha = .14f), RoundedCornerShape(15.dp)), contentAlignment = Alignment.Center) { Icon(Icons.Default.PowerSettingsNew, null, tint = MaterialTheme.colorScheme.primary) }
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(stringResource(if (running) R.string.engine_on else R.string.engine_off), fontWeight = FontWeight.SemiBold)
                    Text(statusMessage(status), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                }
                val engineLabel = stringResource(R.string.audio_engine)
                Switch(checked = running, onCheckedChange = { toggle() }, modifier = Modifier.semantics { contentDescription = engineLabel })
            }
            if (status.backend != null || status.sessions > 0) {
                val sessions = pluralStringResource(R.plurals.session_count, status.sessions, status.sessions)
                val backend = status.backend?.let { backendName(it) }
                Text(listOfNotNull(backend, sessions).joinToString("  ·  "), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Panel {
            val deviceBands = status.capabilities.bandFrequenciesHz
            val mappedBands = deviceBands.isNotEmpty() && deviceBands != BAND_FREQUENCIES_HZ
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    SectionTitle(stringResource(R.string.equalizer))
                    Text(
                        if (mappedBands) pluralStringResource(R.plurals.eq_mapped_bands, deviceBands.size, deviceBands.size)
                        else stringResource(R.string.eq_subtitle),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                val eqLabel = stringResource(R.string.equalizer_enabled)
                Switch(a.eqEnabled, { update(a.copy(eqEnabled = it)) }, modifier = Modifier.semantics { contentDescription = eqLabel })
            }
            if (mappedBands) {
                val numbers = remember(locale) {
                    NumberFormat.getNumberInstance(locale).apply { isGroupingUsed = false }
                }
                val bands = deviceBands.joinToString(", ") { if (it >= 1000) "${numbers.format(it / 1000.0)}k" else numbers.format(it) }
                Text(stringResource(R.string.device_bands, bands), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            status.capabilities.gainRangeDb?.let { (min, max) ->
                if (min > -12f || max < 12f) {
                    Text(stringResource(R.string.device_gain_range, db(min, locale), db(max, locale)), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            EqGraph(a.gains, band, { band = it }) { i, gain -> update(a.copy(gains = a.gains.toMutableList().also { it[i] = gain })) }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { BAND_FREQUENCIES_HZ.indices.forEach { i -> Text(hz(i), modifier = Modifier.clickable { band = i }.padding(vertical = 8.dp), color = if (i == band) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 10.sp) } }
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { band = (band - 1).coerceAtLeast(0) }, enabled = band > 0) { Icon(Icons.Default.ChevronLeft, stringResource(R.string.previous_band)) }
                Column(Modifier.weight(1f)) {
                    val frequency = BAND_FREQUENCIES_HZ[band]
                    val label = if (frequency >= 1000) stringResource(R.string.frequency_khz, frequency / 1000) else stringResource(R.string.frequency_hz, frequency)
                    Text(label, fontWeight = FontWeight.SemiBold)
                    Text(stringResource(R.string.selected_band), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                IconButton(onClick = { band = (band + 1).coerceAtMost(9) }, enabled = band < 9) { Icon(Icons.Default.ChevronRight, stringResource(R.string.next_band)) }
                Text(stringResource(R.string.gain_db, db(a.gains[band], locale)), color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
            }
            val gainLabel = stringResource(R.string.gain_slider, BAND_FREQUENCIES_HZ[band])
            Slider(value = a.gains[band], onValueChange = { v -> update(a.copy(gains = a.gains.toMutableList().also { it[band] = (v * 2).roundToInt() / 2f })) }, valueRange = -12f..12f, modifier = Modifier.semantics { contentDescription = gainLabel })
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { update(a.copy(gains = a.gains.toMutableList().also { it[band] = (it[band] - .5f).coerceAtLeast(-12f) })) }, modifier = Modifier.weight(1f)) { Text(stringResource(R.string.gain_db, db(-0.5f, locale))) }
                OutlinedButton(onClick = { update(a.copy(gains = a.gains.toMutableList().also { it[band] = (it[band] + .5f).coerceAtMost(12f) })) }, modifier = Modifier.weight(1f)) { Text(stringResource(R.string.gain_db, db(0.5f, locale))) }
            }
            TextButton(onClick = { update(a.copy(gains = List(BAND_COUNT) { 0f })) }, modifier = Modifier.align(Alignment.End)) { Text(stringResource(R.string.reset_equalizer)) }
        }
        Panel {
            SectionTitle(stringResource(R.string.sound_enhancements), stringResource(R.string.device_dependent))
            LevelControl(stringResource(R.string.bass), a.bass.toFloat(), 0f..100f, stringResource(R.string.percent, a.bass), status.capabilities.bass) { update(a.copy(bass = it.roundToInt())) }
            LevelControl(stringResource(R.string.loudness), a.loudness.toFloat(), 0f..100f, stringResource(R.string.percent, a.loudness), status.capabilities.loudness) { update(a.copy(loudness = it.roundToInt())) }
            val balance = when {
                a.balance == 0f -> stringResource(R.string.center)
                a.balance < 0f -> stringResource(R.string.balance_left, (a.balance * -100).roundToInt())
                else -> stringResource(R.string.balance_right, (a.balance * 100).roundToInt())
            }
            LevelControl(stringResource(R.string.balance), a.balance, -1f..1f, balance, status.capabilities.balance) { update(a.copy(balance = it)) }
            if (status.capabilities.virtualizer) {
                val label = stringResource(R.string.virtualizer)
                if (status.capabilities.virtualizerStrength) LevelControl(label, a.virtualizer.toFloat(), 0f..100f, stringResource(R.string.percent, a.virtualizer), true) { update(a.copy(virtualizer = it.roundToInt())) }
                else SettingSwitch(label, stringResource(R.string.virtualizer_toggle_only), a.virtualizer > 0) { update(a.copy(virtualizer = if (it) 100 else 0)) }
            }
            if (!running) Text(stringResource(R.string.enhancements_start_engine), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
            if (s.showReverb) Text(stringResource(R.string.reverb_unavailable), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
        }
        if (s.showVolume) VolumePanel()
    }
}

@Composable private fun EqGraph(gains: List<Float>, selected: Int, onSelect: (Int) -> Unit, onGain: (Int, Float) -> Unit) {
    val currentSelect by rememberUpdatedState(onSelect)
    val currentGain by rememberUpdatedState(onGain)
    val primary = MaterialTheme.colorScheme.primary
    val grid = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = .24f)
    val label = stringResource(R.string.eq_curve_description)
    Canvas(Modifier.fillMaxWidth().height(150.dp).semantics { contentDescription = label }.pointerInput(Unit) { detectTapGestures { p -> currentSelect(((p.x / size.width) * 9).roundToInt().coerceIn(0, 9)) } }.pointerInput(Unit) {
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
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text(label, Modifier.weight(1f, fill = false), style = MaterialTheme.typography.bodyMedium); Text(if (enabled) display else stringResource(R.string.unavailable), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
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
    Panel {
        SectionTitle(stringResource(R.string.media_volume), stringResource(R.string.system_tag))
        LevelControl(stringResource(R.string.volume), volume, 0f..max, stringResource(R.string.percent, (volume / max * 100).roundToInt()), true) {
            volume = it
            am.setStreamVolume(AudioManager.STREAM_MUSIC, it.roundToInt(), 0)
        }
    }
}

@Composable private fun ProfilesScreen(s: AppSettings, repository: DataStoreSettingsRepository, onExport: () -> Unit, onImport: () -> Unit, modifier: Modifier, update: (AudioSettings) -> Unit, onBluetoothPermission: () -> Unit, bluetoothStatus: BluetoothMonitorState) {
    val scope = rememberSettingsScope()
    val context = LocalContext.current
    var editing by remember { mutableStateOf<Profile?>(null) }
    var creating by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf<Profile?>(null) }
    var assigning by remember { mutableStateOf<Profile?>(null) }
    Screen(modifier) {
        Header(stringResource(R.string.profiles_kicker), stringResource(R.string.nav_profiles), stringResource(R.string.profiles_subtitle))
        Panel {
            SectionTitle(stringResource(R.string.starting_points), stringResource(R.string.presets_tag))
            Text(stringResource(R.string.presets_description), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            defaultProfiles.forEach { preset ->
                val name = presetName(preset); val gains = preset.settings.gains
                Row(Modifier.fillMaxWidth().clickable { update(s.audio.copy(gains = gains)) }.heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.GraphicEq, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp)); Spacer(Modifier.width(12.dp)); Text(name, Modifier.weight(1f)); Icon(Icons.Default.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        Panel {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Column(Modifier.weight(1f)) {
                    SectionTitle(stringResource(R.string.your_profiles))
                    Text(stringResource(R.string.your_profiles_description), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                FilledTonalButton(onClick = { creating = true }) {
                    Icon(Icons.Default.Add, null)
                    Spacer(Modifier.width(4.dp))
                    Text(stringResource(R.string.save))
                }
            }
            if (s.profiles.none { p -> defaultProfiles.any { it.id == p.id }.not() }) Text(stringResource(R.string.no_profiles), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(vertical = 12.dp))
            s.profiles.filterNot { p -> defaultProfiles.any { it.id == p.id } }.forEach { p ->
                var menu by remember(p.id) { mutableStateOf(false) }
                Row(Modifier.fillMaxWidth().heightIn(min = 56.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f).clickable { scope.launch { repository.selectProfile(p.id) } }.padding(vertical = 8.dp)) {
                        Text(p.name, fontWeight = FontWeight.Medium)
                        Text(stringResource(if (s.selectedProfileId == p.id) R.string.profile_selected else R.string.tap_to_load), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                    }
                    Box {
                        IconButton(onClick = { menu = true }) { Icon(Icons.Default.MoreVert, stringResource(R.string.profile_options, p.name)) }
                        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                            DropdownMenuItem(text = { Text(stringResource(R.string.rename)) }, onClick = { menu = false; editing = p })
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.duplicate)) },
                                onClick = {
                                    menu = false
                                    val emptyName = context.getString(R.string.profile_copy, "")
                                    val copyName = context.getString(R.string.profile_copy, p.name.take(80 - emptyName.length))
                                    scope.launch { repository.duplicateProfile(p.id, copyName) }
                                },
                            )
                            DropdownMenuItem(text = { Text(stringResource(R.string.assign_bluetooth)) }, onClick = { menu = false; assigning = p })
                            DropdownMenuItem(text = { Text(stringResource(R.string.delete)) }, onClick = { menu = false; deleting = p })
                        }
                    }
                }
            }
        }
        Panel {
            SectionTitle(stringResource(R.string.bluetooth_profiles))
            val message = when (bluetoothStatus) {
                BluetoothMonitorState.STOPPED -> R.string.bluetooth_stopped
                BluetoothMonitorState.PERMISSION_REQUIRED -> R.string.bluetooth_permission_required
                BluetoothMonitorState.WAITING -> R.string.bluetooth_waiting
                BluetoothMonitorState.NO_DEVICE -> R.string.bluetooth_no_device
                BluetoothMonitorState.MULTIPLE_DEVICES -> R.string.bluetooth_multiple_devices
                BluetoothMonitorState.CONNECTED -> R.string.bluetooth_connected
            }
            Text(stringResource(message), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
            Text(stringResource(R.string.bluetooth_assignment_tip), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
        }
        Panel {
            SectionTitle(stringResource(R.string.backup))
            Text(stringResource(R.string.backup_description), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onExport, modifier = Modifier.weight(1f)) { Text(stringResource(R.string.export_backup_button)) }
                OutlinedButton(onClick = onImport, modifier = Modifier.weight(1f)) { Text(stringResource(R.string.import_backup_button)) }
            }
        }
    }
    if (creating || editing != null) {
        var name by remember(creating, editing?.id) { mutableStateOf(editing?.name ?: "") }
        val dismiss = { creating = false; editing = null }
        AlertDialog(
            onDismissRequest = dismiss,
            title = { Text(stringResource(if (creating) R.string.save_profile else R.string.rename_profile)) },
            text = {
                OutlinedTextField(value = name, onValueChange = { name = it.take(80) }, label = { Text(stringResource(R.string.profile_name)) }, singleLine = true)
            },
            confirmButton = {
                TextButton(enabled = name.isNotBlank(), onClick = {
                    val current = editing
                    val trimmed = name.trim()
                    scope.launch {
                        if (current != null) repository.renameProfile(current.id, trimmed)
                        else repository.saveProfile(Profile(UUID.randomUUID().toString(), trimmed, s.audio))
                    }
                    dismiss()
                }) { Text(stringResource(R.string.save)) }
            },
            dismissButton = { TextButton(onClick = dismiss) { Text(stringResource(R.string.cancel)) } },
        )
    }
    deleting?.let { profile ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text(stringResource(R.string.delete_profile_title, profile.name)) },
            text = { Text(stringResource(R.string.delete_profile_description)) },
            confirmButton = {
                TextButton(onClick = { scope.launch { repository.deleteProfile(profile.id) }; deleting = null }) { Text(stringResource(R.string.delete)) }
            },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text(stringResource(R.string.cancel)) } },
        )
    }
    assigning?.let { p -> DeviceDialog(p, s, repository, onBluetoothPermission) { assigning = null } }
}

@Composable private fun DeviceDialog(profile: Profile, settings: AppSettings, repo: SettingsRepository, onBluetoothPermission: () -> Unit, dismiss: () -> Unit) {
    val scope = rememberSettingsScope()
    val context = LocalContext.current
    var refresh by remember { mutableIntStateOf(0) }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { refresh++; onBluetoothPermission() }
    val allowed = Build.VERSION.SDK_INT < 31 || context.checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED
    val fallbackName = stringResource(R.string.bluetooth_device)
    val devices = remember(allowed, refresh, fallbackName) {
        if (!allowed) emptyList() else try {
            val manager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
            manager.adapter?.bondedDevices?.map { it.address to (it.name ?: fallbackName) }?.sortedBy { it.second }.orEmpty()
        } catch (_: SecurityException) {
            emptyList()
        }
    }
    AlertDialog(onDismissRequest = dismiss, title = { Text(stringResource(R.string.assign_profile, profile.name)) }, text = {
        Column(Modifier.heightIn(max = 400.dp).verticalScroll(rememberScrollState())) {
            Text(stringResource(R.string.assign_profile_description), style = MaterialTheme.typography.bodyMedium)
            if (!allowed) TextButton(onClick = { if (Build.VERSION.SDK_INT >= 31) permission.launch(Manifest.permission.BLUETOOTH_CONNECT) }) { Text(stringResource(R.string.allow_nearby_devices)) }
            else if (devices.isEmpty()) Text(stringResource(R.string.no_paired_devices), modifier = Modifier.padding(top = 12.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
            devices.forEach { (key, name) ->
                Row(Modifier.fillMaxWidth().heightIn(min = 56.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(name)
                        Text(key, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    val label = stringResource(R.string.assign_device, name)
                    Switch(
                        settings.assignments[key] == profile.id,
                        { checked -> scope.launch { repo.assignProfile(key, if (checked) profile.id else null) } },
                        modifier = Modifier.semantics { contentDescription = label },
                    )
                }
            }
        }
    }, confirmButton = { TextButton(onClick = dismiss) { Text(stringResource(R.string.done)) } })
}

@Composable private fun SettingsScreen(s: AppSettings, repository: DataStoreSettingsRepository, dark: Boolean, modifier: Modifier, update: (AudioSettings) -> Unit) {
    val scope = rememberSettingsScope()
    val context = LocalContext.current
    val a = s.audio
    fun prefs(transform: (AppSettings) -> AppSettings) { scope.launch { repository.modifyPreferences(transform) } }
    Screen(modifier) {
        Header(stringResource(R.string.settings_kicker), stringResource(R.string.nav_settings), stringResource(R.string.settings_subtitle))
        Panel {
            SectionTitle(stringResource(R.string.appearance))
            ThemeOptions(
                theme = s.theme,
                palette = s.palette,
                dark = dark,
                onThemeChange = { theme -> prefs { it.copy(theme = theme) } },
                onPaletteChange = { palette -> prefs { it.copy(palette = palette) } },
            )
            LanguagePicker()
            SettingSwitch(stringResource(R.string.show_media_volume), stringResource(R.string.show_media_volume_description), s.showVolume) {
                prefs { current -> current.copy(showVolume = it) }
            }
            SettingSwitch(stringResource(R.string.show_reverb), stringResource(R.string.show_reverb_description), s.showReverb) {
                prefs { current -> current.copy(showReverb = it) }
            }
        }
        Panel {
            SectionTitle(stringResource(R.string.audio_engine))
            Text(stringResource(R.string.attachment_mode), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
            Text(stringResource(R.string.attachment_mode_description), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
            AttachmentMode.entries.forEach { mode ->
                val select = { update(a.copy(attachmentMode = mode)) }
                val label = when (mode) {
                    AttachmentMode.AUTO -> R.string.attachment_auto
                    AttachmentMode.PLAYERS_ONLY -> R.string.attachment_players
                    AttachmentMode.GLOBAL -> R.string.attachment_global
                }
                Row(Modifier.fillMaxWidth().clickable(onClick = select).heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(a.attachmentMode == mode, onClick = select)
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(label))
                }
            }
            SettingSwitch(stringResource(R.string.prefer_legacy), stringResource(R.string.prefer_legacy_description), a.preferLegacy) { update(a.copy(preferLegacy = it)) }
            SettingSwitch(stringResource(R.string.auto_headroom), stringResource(R.string.auto_headroom_description), a.autoHeadroom) { update(a.copy(autoHeadroom = it)) }
            LevelControl(stringResource(R.string.processing_frame), a.frameDurationMs, 5f..100f, stringResource(R.string.milliseconds, a.frameDurationMs.roundToInt()), true) {
                update(a.copy(frameDurationMs = it.roundToInt().toFloat()))
            }
        }
        Panel {
            SectionTitle(stringResource(R.string.system))
            Text(stringResource(R.string.system_description), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
            TextButton(onClick = { try { context.startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)) } catch (_: Exception) { context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, android.net.Uri.parse("package:${context.packageName}"))) } }) { Text(stringResource(R.string.notification_settings)) }
            TextButton(onClick = { context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, android.net.Uri.parse("package:${context.packageName}"))) }) { Text(stringResource(R.string.app_battery_settings)) }
        }
    }
}
@Composable private fun SettingSwitch(title: String, description: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min = 64.dp), verticalAlignment = Alignment.CenterVertically) { Column(Modifier.weight(1f)) { Text(title, fontWeight = FontWeight.Medium, style = MaterialTheme.typography.bodyMedium); Text(description, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall) }; Switch(checked, onChange, modifier = Modifier.semantics { contentDescription = title }) }
}
