package com.toonjack.studio

import android.Manifest
import android.content.pm.PackageManager
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import kotlinx.coroutines.delay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val Ink = Color(0xFFEAF5F3)
private val Muted = Color(0xFF9CB2AE)
private val Teal = Color(0xFF20B5A8)
private val Panel = Color(0xFF172724)
private val Background = Color(0xFF0B1413)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { JackTunesApp() }
    }
}

@Composable
private fun JackTunesApp() {
    val context = androidx.compose.ui.platform.LocalContext.current
    val recordingsDir = remember { File(context.filesDir, "recordings").apply { mkdirs() } }
    val recordings = remember {
        mutableStateListOf<File>().apply {
            addAll(recordingsDir.listFiles()?.filter { it.extension == "m4a" }?.sortedByDescending { it.lastModified() } ?: emptyList())
        }
    }
    var recorder by remember { mutableStateOf<MediaRecorder?>(null) }
    var player by remember { mutableStateOf<MediaPlayer?>(null) }
    var recordingFile by remember { mutableStateOf<File?>(null) }
    var isRecording by remember { mutableStateOf(false) }
    var recordingStarted by remember { mutableStateOf(0L) }
    var elapsed by remember { mutableStateOf(0L) }
    var playingName by remember { mutableStateOf<String?>(null) }
    var isPlaying by remember { mutableStateOf(false) }
    var playerPosition by remember { mutableStateOf(0) }
    var playerDuration by remember { mutableStateOf(0) }
    var deleteTarget by remember { mutableStateOf<File?>(null) }
    var message by remember { mutableStateOf("Your private recording space") }
    var renderingFileName by remember { mutableStateOf<String?>(null) }
    var autoTuneEnabled by remember { mutableStateOf(true) }
    var autoTuneStrength by remember { mutableStateOf(0.65f) }
    var autoTuneRoot by remember { mutableStateOf(0) }
    var autoTuneMajor by remember { mutableStateOf(true) }
    var dreamWarmth by remember { mutableStateOf(0.28f) }
    var dreamSoftness by remember { mutableStateOf(0.42f) }
    var dreamAir by remember { mutableStateOf(0.32f) }
    var dreamCompression by remember { mutableStateOf(0.34f) }
    var dreamDeEss by remember { mutableStateOf(0.35f) }
    var dreamAmbience by remember { mutableStateOf(0.16f) }
    var karaokePreset by remember { mutableStateOf("Dreamy Male") }
    var karaokeIntensity by remember { mutableStateOf(0.75f) }
    val noteNames = listOf("C", "C♯", "D", "D♯", "E", "F", "F♯", "G", "G♯", "A", "A♯", "B")
    val coroutineScope = rememberCoroutineScope()
    var showLyrics by remember { mutableStateOf(false) }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        message = if (granted) "Microphone ready. Tap Record to begin." else "Microphone permission is needed to record."
    }

    fun refreshRecordings() {
        recordings.clear()
        recordings.addAll(recordingsDir.listFiles()?.filter { it.extension == "m4a" }?.sortedByDescending { it.lastModified() } ?: emptyList())
    }

    fun startRecording() {
        try {
            val stamp = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.US).format(Date())
            val file = File(recordingsDir, "JackTunes_$stamp.m4a")
            val newRecorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) MediaRecorder(context) else @Suppress("DEPRECATION") MediaRecorder()
            newRecorder.setAudioSource(MediaRecorder.AudioSource.MIC)
            newRecorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            newRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            newRecorder.setAudioEncodingBitRate(192000)
            newRecorder.setAudioSamplingRate(48000)
            newRecorder.setOutputFile(file.absolutePath)
            newRecorder.prepare()
            newRecorder.start()
            recorder = newRecorder
            recordingFile = file
            recordingStarted = System.currentTimeMillis()
            elapsed = 0
            isRecording = true
            message = "Recording locally on this phone"
        } catch (error: Exception) {
            try { recorder?.release() } catch (_: Exception) {}
            recorder = null
            isRecording = false
            message = "Could not start recording: ${error.localizedMessage ?: "Audio unavailable"}"
        }
    }

    fun stopRecording() {
        try {
            recorder?.stop()
            recordingFile?.let { if (it.exists() && it.length() > 0) recordings.add(0, it) }
            message = "Recording saved on this phone"
        } catch (_: Exception) {
            recordingFile?.delete()
            message = "That take was too short. Try recording again."
        } finally {
            recorder?.release()
            recorder = null
            recordingFile = null
            isRecording = false
            refreshRecordings()
        }
    }

    fun play(file: File) {
        try {
            player?.release()
            player = null

            val nextPlayer = MediaPlayer()
            nextPlayer.setDataSource(file.absolutePath)
            nextPlayer.setOnCompletionListener {
                it.seekTo(0)
                it.pause()
                isPlaying = false
                playerPosition = 0
            }
            nextPlayer.prepare()
            playerDuration = nextPlayer.duration.coerceAtLeast(0)
            playerPosition = 0
            player = nextPlayer
            playingName = file.name

            nextPlayer.start()
            isPlaying = true
        } catch (error: Exception) {
            player?.release()
            player = null
            playingName = null
            isPlaying = false
            message = "Playback failed: ${error.localizedMessage ?: "Unknown error"}"
        }
    }

    fun stopPlayback() {
        try { player?.release() } catch (_: Exception) {}
        player = null

        playingName = null
        isPlaying = false
        playerPosition = 0
        playerDuration = 0
    }

    DisposableEffect(Unit) {
        onDispose {
            try { recorder?.stop() } catch (_: Exception) {}
            try { recorder?.release() } catch (_: Exception) {}
            try { player?.release() } catch (_: Exception) {}

        }
    }

    LaunchedEffect(isRecording) {
        while (isRecording) {
            elapsed = (System.currentTimeMillis() - recordingStarted) / 1000
            delay(250)
        }
    }

    LaunchedEffect(player, isPlaying) {
        while (player != null && isPlaying) {
            try { playerPosition = player?.currentPosition ?: 0 } catch (_: Exception) {}
            delay(200)
        }
    }

    if (deleteTarget != null) {
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text("Delete this recording?") },
            text = { Text("This permanently deletes ${deleteTarget?.nameWithoutExtension ?: "this take"} from Toon Studio on this phone. This cannot be undone.") },
            confirmButton = {
                Button(
                    onClick = {
                        val file = deleteTarget
                        if (file != null) {
                            if (playingName == file.name) stopPlayback()
                            val deleted = try { file.delete() } catch (_: Exception) { false }
                            if (deleted) message = "Recording deleted" else message = "Could not delete this recording"
                            refreshRecordings()
                        }
                        deleteTarget = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFB84D55))
                ) { Text("Delete permanently") }
            },
            dismissButton = { OutlinedButton(onClick = { deleteTarget = null }) { Text("Cancel") } }
        )
    }

    MaterialTheme(colorScheme = darkColorScheme(
        primary = Teal, onPrimary = Color(0xFF00201D),
        background = Background, surface = Panel, onSurface = Ink
    )) {
        Surface(modifier = Modifier.fillMaxSize(), color = Background) {
            if (showLyrics) {
                LyricsScreen(onBack = { showLyrics = false }, onRecordingSaved = { file -> recordings.add(0, file) })
            } else {
            Column(
                modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 18.dp, vertical = 18.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("TOON STUDIO  /  PRIVATE STUDIO", color = Teal, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
                        Text("Make something real.", color = Ink, fontSize = 27.sp, fontWeight = FontWeight.Bold)
                        Text("Your voice. Your songs. Your space.", color = Muted, fontSize = 12.sp)
                    }
                    Text("🎙️", fontSize = 34.sp)
                }

                OutlinedButton(
                    onClick = { showLyrics = true },
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    shape = RoundedCornerShape(14.dp)
                ) { Text("♫  OPEN LYRIC BOOK", fontWeight = FontWeight.Bold) }

                Card(colors = CardDefaults.cardColors(containerColor = Panel), shape = RoundedCornerShape(24.dp)) {
                    Column(Modifier.fillMaxWidth().padding(18.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("RECORDING STUDIO", color = Muted, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
                        Spacer(Modifier.height(12.dp))
                        Text(formatTime(elapsed), color = Ink, fontSize = 48.sp, fontWeight = FontWeight.Light)
                        Text(if (isRecording) "Recording in progress" else "Session timer", color = Muted, fontSize = 12.sp)
                        Text(message, color = Muted, fontSize = 11.sp)
                        Spacer(Modifier.height(14.dp))
                        Button(
                            onClick = {
                                if (isRecording) stopRecording()
                                else if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) startRecording()
                                else permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                            },
                            modifier = Modifier.fillMaxWidth().height(54.dp),
                            shape = RoundedCornerShape(18.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = if (isRecording) Color(0xFFB84D55) else Teal)
                        ) { Text(if (isRecording) "STOP & SAVE TAKE" else "●  START RECORDING", fontWeight = FontWeight.Bold, fontSize = 14.sp) }
                    }
                }

                if (playingName != null) {
                    Card(colors = CardDefaults.cardColors(containerColor = Panel), shape = RoundedCornerShape(18.dp)) {
                        Column(Modifier.fillMaxWidth().padding(14.dp)) {
                            Text("NOW PLAYING", color = Teal, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
                            Text(playingName!!.removeSuffix(".m4a"), color = Ink, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                            Slider(
                                value = playerPosition.coerceIn(0, playerDuration.coerceAtLeast(1)).toFloat(),
                                onValueChange = { value ->
                                    playerPosition = value.toInt()
                                    try { player?.seekTo(playerPosition) } catch (_: Exception) {}
                                },
                                valueRange = 0f..playerDuration.coerceAtLeast(1).toFloat(),
                                enabled = playerDuration > 0
                            )
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(formatMillis(playerPosition), color = Muted, fontSize = 11.sp, modifier = Modifier.weight(1f))
                                Text(formatMillis(playerDuration), color = Muted, fontSize = 11.sp)
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                Button(onClick = {
                                    try {
                                        if (isPlaying) player?.pause() else player?.start()
                                        isPlaying = !isPlaying
                                    } catch (_: Exception) {}
                                }) { Text(if (isPlaying) "Pause" else "Resume") }
                                OutlinedButton(onClick = { try { player?.seekTo(0); player?.start(); isPlaying = true; playerPosition = 0 } catch (_: Exception) {} }) { Text("Restart") }
                                OutlinedButton(onClick = { stopPlayback() }) { Text("Close player") }
                            }
                        }
                    }
                }

                Card(colors = CardDefaults.cardColors(containerColor = Panel), shape = RoundedCornerShape(20.dp)) {
                    Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                        Text("AUTOTUNE  /  TEEN POP TENOR", color = Teal, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text("Natural, expressive correction", color = Ink, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                                Text(if (autoTuneEnabled) "Autotune enabled" else "Autotune bypassed", color = if (autoTuneEnabled) Teal else Muted, fontSize = 10.sp)
                            }
                            OutlinedButton(onClick = { autoTuneEnabled = !autoTuneEnabled }) {
                                Text(if (autoTuneEnabled) "ON" else "OFF")
                            }
                        }
                        Text("Choose the song key and scale. Export a tuned copy from any take below; your dry original stays untouched.", color = Muted, fontSize = 11.sp)
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("KEY", color = Muted, fontSize = 10.sp)
                            OutlinedButton(onClick = { autoTuneRoot = (autoTuneRoot + 11) % 12 }) { Text("−") }
                            Text(noteNames[autoTuneRoot], color = Ink, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                            OutlinedButton(onClick = { autoTuneRoot = (autoTuneRoot + 1) % 12 }) { Text("+") }
                            Spacer(Modifier.weight(1f))
                            OutlinedButton(onClick = { autoTuneMajor = !autoTuneMajor }) {
                                Text(if (autoTuneMajor) "Major" else "Chromatic")
                            }
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Correction", color = Muted, fontSize = 11.sp, modifier = Modifier.weight(1f))
                            Text("${(autoTuneStrength * 100).toInt()}%", color = Teal, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                        Slider(value = autoTuneStrength, onValueChange = { autoTuneStrength = it }, valueRange = 0.35f..0.85f)
                        Text("Starter setting: 65% · smooth and natural. If you don't know the key, choose Chromatic.", color = Muted, fontSize = 10.sp)
                    }
                }

                Card(colors = CardDefaults.cardColors(containerColor = Panel), shape = RoundedCornerShape(20.dp)) {
                    Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                        Text("KARAOKE VOICE STUDIO", color = Teal, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp)
                        Text("Pitch correction plus a vocal-effects preset", color = Ink, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                        Text("Creates a separate processed copy. Your original recording is never changed.", color = Muted, fontSize = 10.sp)
                        listOf("Dreamy Male", "Velvet Voice", "Studio Pop", "Echo Karaoke", "Deep Voice").chunked(2).forEach { group ->
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                group.forEach { preset ->
                                    OutlinedButton(onClick = { karaokePreset = preset }, modifier = Modifier.weight(1f)) {
                                        Text(if (karaokePreset == preset) "✓ $preset" else preset, fontSize = 10.sp)
                                    }
                                }
                                if (group.size == 1) Spacer(Modifier.weight(1f))
                            }
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Effect intensity", color = Muted, fontSize = 11.sp, modifier = Modifier.weight(1f))
                            Text("${(karaokeIntensity * 100).toInt()}%", color = Teal, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                        Slider(value = karaokeIntensity, onValueChange = { karaokeIntensity = it }, valueRange = 0.2f..1f)
                        Text("Deep Voice is a tonal preset, not AI voice conversion or guaranteed formant/gender transformation.", color = Muted, fontSize = 10.sp)
                    }
                }

                Card(colors = CardDefaults.cardColors(containerColor = Panel), shape = RoundedCornerShape(20.dp)) {
                    Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                        Text("DREAM VOICE  /  TONE STUDIO", color = Teal, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp)
                        Text("Shape the tone of your processed copy", color = Ink, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                        Text("These controls adjust tonal polish and ambience. They do not change vocal identity or formants.", color = Muted, fontSize = 10.sp)
                        ToneControl("Warmth", dreamWarmth) { dreamWarmth = it }
                        ToneControl("Softness", dreamSoftness) { dreamSoftness = it }
                        ToneControl("Air", dreamAir) { dreamAir = it }
                        ToneControl("Compression", dreamCompression) { dreamCompression = it }
                        ToneControl("De-essing", dreamDeEss) { dreamDeEss = it }
                        ToneControl("Ambience", dreamAmbience) { dreamAmbience = it }
                        OutlinedButton(onClick = {
                            dreamWarmth = 0.28f; dreamSoftness = 0.42f; dreamAir = 0.32f
                            dreamCompression = 0.34f; dreamDeEss = 0.35f; dreamAmbience = 0.16f
                        }, modifier = Modifier.fillMaxWidth()) { Text("Reset Dream Voice") }
                    }
                }

                RecordingBackupCard(recordingsDir)

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("YOUR TAKES", color = Ink, fontSize = 18.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                    Text("${recordings.size} saved", color = Muted, fontSize = 12.sp)
                }

                if (recordings.isEmpty()) {
                    Card(colors = CardDefaults.cardColors(containerColor = Panel), shape = RoundedCornerShape(18.dp)) {
                        Column(Modifier.fillMaxWidth().padding(18.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("♫", color = Teal, fontSize = 28.sp)
                            Text("Your recordings will appear here", color = Muted, fontSize = 12.sp)
                        }
                    }
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        recordings.forEach { file ->
                            Card(colors = CardDefaults.cardColors(containerColor = Panel), shape = RoundedCornerShape(15.dp)) {
                                Column(Modifier.fillMaxWidth().padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Column(Modifier.weight(1f)) {
                                            Text(file.nameWithoutExtension.replace("JackTunes_", ""), color = Ink, fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
                                            Text(formatBytes(file.length()), color = Muted, fontSize = 10.sp)
                                        }
                                        if (renderingFileName == file.name) {
                                            Text("Rendering…", color = Teal, fontSize = 11.sp)
                                        }
                                    }
                                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                                        OutlinedButton(onClick = {
                                            if (playingName == file.name) {
                                                if (isPlaying) {
                                                    player?.pause()
                                                    isPlaying = false
                                                } else {
                                                    player?.start()
                                                    isPlaying = true
                                                }
                                            } else play(file)
                                        }, modifier = Modifier.weight(1f)) {
                                            Text(if (playingName == file.name) if (isPlaying) "Pause" else "Resume" else "Play")
                                        }
                                        OutlinedButton(
                                            onClick = {
                                                if (renderingFileName == null && autoTuneEnabled) {
                                                    renderingFileName = file.name
                                                    message = "Tuning vocal… keep Toon Studio open."
                                                    val selectedRoot = autoTuneRoot
                                                    val selectedStrength = autoTuneStrength
                                                    val selectedScale = if (autoTuneMajor)
                                                        com.toonjack.studio.audio.ToonJackDsp.Scale.MAJOR
                                                    else com.toonjack.studio.audio.ToonJackDsp.Scale.CHROMATIC
                                                    coroutineScope.launch {
                                                        val result = withContext(Dispatchers.IO) {
                                                            try {
                                                                val base = file.nameWithoutExtension
                                                                var output = File(recordingsDir, "${base}_AutoTune.m4a")
                                                                var suffix = 2
                                                                while (output.exists()) {
                                                                    output = File(recordingsDir, "${base}_AutoTune_${suffix}.m4a")
                                                                    suffix++
                                                                }
                                                                com.toonjack.studio.audio.OfflineAudioRenderer.renderAutoTune(
                                                                    file, output, selectedRoot, selectedScale, selectedStrength
                                                                )
                                                                Result.success(output)
                                                            } catch (error: Exception) {
                                                                Result.failure<File>(error)
                                                            }
                                                        }
                                                        result.onSuccess {
                                                            message = "Autotune export saved. Original take preserved."
                                                            refreshRecordings()
                                                        }.onFailure {
                                                            message = "Autotune failed: ${it.localizedMessage ?: "Audio processing error"}"
                                                        }
                                                        renderingFileName = null
                                                    }
                                                }
                                            },
                                            enabled = renderingFileName == null && autoTuneEnabled,
                                            modifier = Modifier.weight(1.2f)
                                        ) { Text("Autotune", fontSize = 11.sp) }
                                        OutlinedButton(
                                            onClick = {
                                                if (renderingFileName == null) {
                                                    renderingFileName = file.name
                                                    message = "Creating a separate Dream Voice version…"
                                                    coroutineScope.launch {
                                                        val result = withContext(Dispatchers.IO) {
                                                            try {
                                                                val base = file.nameWithoutExtension
                                                                var output = File(recordingsDir, "${base}_DreamVoice.m4a")
                                                                var suffix = 2
                                                                while (output.exists()) {
                                                                    output = File(recordingsDir, "${base}_DreamVoice_${suffix}.m4a")
                                                                    suffix++
                                                                }
                                                                com.toonjack.studio.audio.OfflineAudioRenderer.renderDreamVoice(file, output, dreamWarmth, dreamSoftness, dreamAir, dreamCompression, dreamDeEss, dreamAmbience)
                                                                Result.success(output)
                                                            } catch (error: Exception) {
                                                                Result.failure<File>(error)
                                                            }
                                                        }
                                                        result.onSuccess {
                                                            message = "Dream Voice export saved. Your original is untouched."
                                                            refreshRecordings()
                                                        }.onFailure {
                                                            message = "Voice render failed: ${it.localizedMessage ?: "Audio processing error"}"
                                                        }
                                                        renderingFileName = null
                                                    }
                                                }
                                            },
                                            enabled = renderingFileName == null,
                                            modifier = Modifier.weight(1.35f)
                                        ) { Text("Dream Voice", fontSize = 11.sp) }
                                        OutlinedButton(onClick = { deleteTarget = file }) {
                                            Text("Delete", color = Color(0xFFFF8585))
                                        }
                                    }
                                    if (playingName == file.name) {
                                        Slider(
                                            value = playerPosition.coerceIn(0, playerDuration.coerceAtLeast(1)).toFloat(),
                                            onValueChange = { value ->
                                                playerPosition = value.toInt()
                                                try { player?.seekTo(playerPosition) } catch (_: Exception) {}
                                            },
                                            valueRange = 0f..playerDuration.coerceAtLeast(1).toFloat(),
                                            enabled = playerDuration > 0,
                                            modifier = Modifier.fillMaxWidth()
                                        )
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text(formatMillis(playerPosition), color = Muted, fontSize = 10.sp, modifier = Modifier.weight(1f))
                                            Text(if (isPlaying) "PLAYING" else "PAUSED", color = Teal, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                            Spacer(Modifier.weight(1f))
                                            Text(formatMillis(playerDuration), color = Muted, fontSize = 10.sp)
                                        }
                                    }
                                    OutlinedButton(
                                        onClick = {
                                            if (renderingFileName == null) {
                                                renderingFileName = file.name
                                                message = "Rendering $karaokePreset karaoke vocal…"
                                                val root = autoTuneRoot
                                                val scale = if (autoTuneMajor) com.toonjack.studio.audio.ToonJackDsp.Scale.MAJOR else com.toonjack.studio.audio.ToonJackDsp.Scale.CHROMATIC
                                                val strength = autoTuneStrength
                                                val preset = karaokePreset
                                                val intensity = karaokeIntensity
                                                coroutineScope.launch {
                                                    val result = withContext(Dispatchers.IO) {
                                                        try {
                                                            val tag = preset.replace(" ", "")
                                                            var output = File(recordingsDir, "${file.nameWithoutExtension}_${tag}.m4a")
                                                            var suffix = 2
                                                            while (output.exists()) {
                                                                output = File(recordingsDir, "${file.nameWithoutExtension}_${tag}_${suffix}.m4a")
                                                                suffix++
                                                            }
                                                            com.toonjack.studio.audio.OfflineAudioRenderer.renderKaraokeVoice(file, output, root, scale, strength, preset, intensity)
                                                            Result.success(output)
                                                        } catch (error: Exception) { Result.failure<File>(error) }
                                                    }
                                                    result.onSuccess { message = "$preset export saved. Original take preserved."; refreshRecordings() }
                                                        .onFailure { message = "Karaoke processing failed: ${it.localizedMessage ?: "Audio error"}" }
                                                    renderingFileName = null
                                                }
                                            }
                                        },
                                        enabled = renderingFileName == null && autoTuneEnabled,
                                        modifier = Modifier.fillMaxWidth()
                                    ) { Text("Karaoke Voice • $karaokePreset") }
                                }
                            }
                    }
                    }
                }
                Text("Original takes are preserved. Processed exports are saved as separate files.", color = Muted, fontSize = 10.sp)
            }
            }
        }
    }
}


private data class PracticeSong(val title: String, val style: String, val focus: String, val chords: String, val lyrics: String, val tempo: String = "", val key: String = "", val capo: String = "", val strumming: String = "")

private val acousticPracticeSongs = listOf(
    PracticeSong("Stay Until the Morning", "Gentle acoustic pop", "Soft tenor and breath control", "G – D – Em – C", """[Verse 1]
The night is falling over town
The world is quiet, lights go down
I hear the rain against the glass
And wish that this could always last

[Pre-Chorus]
There's something I can't explain
A little warmth inside the rain

[Chorus]
Stay until the morning
Stay until the light
Let me hear your heartbeat
Through the quiet night
We don't need forever
We don't need to know
Just stay until the morning
Before you have to go

[Verse 2]
The stars are fading one by one
But I don't want this night undone
There's nowhere else I'd rather be
Than where your shadow rests with me

[Bridge]
And if tomorrow takes you away
I'll keep this moment anyway

[Final Chorus]
Stay until the morning
Stay until the light
Let me hear your heartbeat
Through the quiet night"""),
    PracticeSong("Paper Hearts", "Emotional acoustic ballad", "Emotional expression and vibrato", "C – G – Am – F", """[Verse 1]
We built a house from paper hearts
And watched it fall apart
We wrote our dreams across the walls
But never saw the cracks at all

[Pre-Chorus]
I tried to hold what couldn't stay
But love can slowly fade away

[Chorus]
We're only paper hearts in the rain
Trying to hold on through the pain
Every little piece that falls apart
Still carries something from the start
And even when the colors disappear
I can still remember you were here

[Verse 2]
I found your letter yesterday
The words were fading into gray
I read it once, then read it twice
Some memories don't need advice

[Bridge]
Maybe we were never meant to last
But that doesn't make it less beautiful

[Final Chorus]
We're only paper hearts in the rain
Trying to hold on through the pain"""),
    PracticeSong("Open Road", "Upbeat acoustic folk-pop", "Rhythm and clear articulation", "D – A – Bm – G", """[Verse 1]
Put my old shoes by the door
I don't need them anymore
Got a little cash and a restless soul
And a thousand miles before I go

[Chorus]
Take me down that open road
Anywhere the wild winds blow
I don't need a map tonight
Just a little hope and a little light
If the world keeps spinning round
I'll keep my feet above the ground
There's a whole life left to know
Waiting down that open road

[Verse 2]
Every town has something new
Every sky has a different blue
I'll make mistakes along the way
But I won't let fear decide my day

[Bridge]
Oh, the road is long
But my heart is strong

[Final Chorus]
Take me down that open road
Anywhere the wild winds blow"""),
    PracticeSong("The Things I Never Said", "Fingerpicked acoustic ballad", "Quiet verses and emotional crescendos", "Am – F – C – G", """[Verse 1]
I had a thousand words for you
But never found the time
I kept them hidden in my chest
Like secrets in a rhyme

[Verse 2]
I should have told you that you mattered
I should have let you know
That every little thing you did
Meant more than you could know

[Chorus]
These are the things I never said
The words that stayed inside my head
If I could turn the years around
I'd let them out, I'd make a sound
But all I have is this melody
And everything you meant to me

[Bridge]
If you could hear me now
Would you understand somehow?

[Final Chorus]
These are the things I never said
The words that stayed inside my head"""),
    PracticeSong("Golden Hour", "Warm acoustic pop", "Bright tone and higher-register transitions", "G – C – Em – D", """[Verse 1]
The sunlight dances on your face
The afternoon slows down its pace
We're laughing like we have no plans
With summer slipping through our hands

[Chorus]
Meet me in the golden hour
Where the sky becomes a flower
Where the world is soft and slow
And there's nowhere else to go
If this moment disappears
I'll remember you were here
Meet me in the golden hour
Stay with me a little longer

[Verse 2]
The city lights are coming on
But I don't feel the day is gone
Your smile is brighter than the sky
And I could watch it all night

[Bridge]
No promises, no perfect plans
Just your fingers in my hands

[Final Chorus]
Meet me in the golden hour
Where the sky becomes a flower"""),
    PracticeSong("Breathe Again", "Inspirational acoustic ballad", "Long phrases and breath support", "Em – C – G – D", """[Verse 1]
I've been walking through the silence
With a storm inside my mind
Trying to find a little courage
Trying to leave the past behind

[Pre-Chorus]
But every sunrise tells me
There's another chance to begin

[Chorus]
I will breathe again
I will rise again
Even if the road is breaking underneath my feet
I will find my way
I will face the day
There's a little more of life waiting for me
I will breathe again

[Verse 2]
Every scar becomes a story
Every fall becomes a start
I don't have to know the future
I just have to trust my heart

[Bridge]
Let the wind carry the sorrow
Let the sunlight find tomorrow

[Final Chorus]
I will breathe again
I will rise again
There's a little more of life waiting for me"""),
    PracticeSong("Midnight Conversations", "Acoustic indie-pop", "Conversational phrasing and low register", "D – Bm – G – A", """[Verse 1]
It's two AM, we're talking slow
About the things we used to know
You tell a joke, I start to smile
And suddenly it's been a while

[Chorus]
Midnight conversations
Little revelations
Talking about the world
Like we can change it all
No destination
No expectations
Just another night
Before the morning calls

[Verse 2]
You say the future feels unknown
I say you're never on your own
We laugh about the things we've done
And wonder where the years have gone

[Bridge]
Maybe these are ordinary days
That we'll remember in a thousand ways

[Final Chorus]
Midnight conversations
Little revelations
Just another night
Before the morning calls"""),
    PracticeSong("Run Toward the Sun", "Acoustic folk-rock", "Projection and energetic chorus", "A – E – F#m – D", """[Verse 1]
I've been waiting for a sign
Watching every passing light
But the answer isn't coming
So I'll make the choice tonight

[Pre-Chorus]
I won't wait for perfect weather
I won't wait for wings to fly

[Chorus]
I'm gonna run toward the sun
Until the day is done
With my hands up in the air
And the wind inside my hair
I don't know where I'll belong
But I'll keep moving on
I'm gonna run toward the sun
Until my journey's done

[Verse 2]
Every mountain has a summit
Every river finds the sea
Every dream begins with someone
Who believes in what could be

[Bridge]
Let the thunder call my name
I won't ever be the same

[Final Chorus]
I'm gonna run toward the sun
Until the day is done"""),
    PracticeSong("If You Were Here", "Minimal acoustic heartbreak ballad", "Controlled emotion and gentle high notes", "C – Am – F – G", """[Verse 1]
There's an empty chair beside the window
Where you used to watch the rain
And I still catch myself turning
When I hear somebody say your name

[Chorus]
If you were here
I'd tell you everything
About the little things
And all the songs I sing
If you were here
The room would feel alive
I'd give anything
To see your smile tonight

[Verse 2]
I still remember how you laughed
At things nobody understood
And sometimes when the world gets heavy
I wish I could go back if I could

[Bridge]
I know that time won't turn around
But your memory is still around

[Final Chorus]
If you were here
I'd tell you everything
About the little things
And all the songs I sing"""),
    PracticeSong("Home Is a Feeling", "Warm acoustic closing ballad", "Full range and expressive dynamics", "G – D – C – Em", """[Verse 1]
I've seen a thousand different places
I've watched a million sunsets fall
I've searched for something I could hold
But never found it after all

[Pre-Chorus]
Then somewhere in the middle
Of everything I thought I knew

[Chorus]
Home is a feeling
Not a place on a map
It's the warmth of a moment
That you wish would last
It's the voice that reminds you
You're never alone
Home is a feeling
And it follows you home

[Verse 2]
The years will change the roads we travel
The faces change along the way
But some things stay inside our hearts
No matter how far we stray

[Bridge]
And when the world gets cold
And every light grows dim
I'll remember where I'm from
And find my way again

[Final Chorus]
Home is a feeling
Not a place on a map
It's the warmth of a moment
That you wish would last
Home is a feeling
And it follows you home

[Outro]
Home is a feeling
And it follows you home.""")
    ,
    PracticeSong("Where the River Goes", "Acoustic folk", "Open-chord transitions and steady eighth-note strumming", "G – D – Em – C", """[Verse 1]
I watched the river cross the land
Like silver running through my hands
It never asked where it should go
It only moved with what it knows

[Chorus]
Where the river goes, I'll follow
Through the sunshine, through tomorrow
Even when the road is winding
There is something worth the finding
Where the river goes, I'll be
Finding out what makes me free

[Verse 2]
The mountains stand, the seasons change
The world outside is never the same
But every mile that passes by
Leaves another reason why

[Bridge]
Let the water carry me
To the person I could be""", "88 BPM", "G major", "None", "Down, Down-Up, Up-Down-Up"),
    PracticeSong("Coffee and the Rain", "Acoustic pop", "Relaxed eighth-note strumming and clean chord changes", "C – G – Am – F", """[Verse 1]
Coffee cooling by the window
Raindrops dancing on the glass
There's a thousand things to worry
But today I'll let them pass

[Chorus]
Coffee and the rain
Wash away the yesterday
I don't need a perfect morning
I just need a little space
Coffee and the rain
Let me breathe and start again

[Verse 2]
Little birds are finding shelter
People hurry down the street
I can hear the world awakening
To a rhythm soft and sweet

[Bridge]
Maybe happiness is hiding
In the ordinary things""", "96 BPM", "C major", "None", "Gentle eighth-note strumming"),
    PracticeSong("Wildflower Heart", "Acoustic country", "Country boom-chuck bass-and-chord coordination", "G – D – A – Bm", """[Verse 1]
You were growing by the roadside
Where nobody stopped to see
With your colors in the sunshine
And your roots beneath the weeds

[Chorus]
You've got a wildflower heart
Beautiful just as you are
You don't need somebody's garden
You were born to reach the stars
Let the whole world fall apart
You've got a wildflower heart

[Verse 2]
Every storm became a teacher
Every winter made you strong
You were never made for hiding
You were always meant to bloom

[Bridge]
Grow wherever life may take you
Let your colors fill the sky""", "104 BPM", "D major (capo 2 shapes)", "2nd fret", "Country boom-chuck"),
    PracticeSong("The Weight of Yesterday", "Acoustic ballad", "Slow fingerpicked arpeggios and even dynamics", "Em – C – G – D", """[Verse 1]
I carried every broken moment
Like a stone inside my chest
Tried to make the past forgiving
Never giving myself rest

[Chorus]
I can't change yesterday
I can't make the years rewind
But I'll put these stones behind me
Leave their shadows far behind
I am more than what has happened
I am more than yesterday

[Verse 2]
There were words I should have spoken
There were doors I should have closed
But the future keeps on waiting
For a heart that still has hope

[Bridge]
One breath, one step
One chance to begin again""", "72 BPM", "E minor", "None", "Fingerpicked arpeggios"),
    PracticeSong("Dancing in the Kitchen", "Acoustic indie pop", "Bright rhythmic strumming and upbeat timing", "G – C – Em – D", """[Verse 1]
There's a song upon the radio
And the sunlight's on the floor
You come running from the hallway
Like we've never danced before

[Chorus]
We're dancing in the kitchen
With our shadows on the wall
Nothing really matters
When we're laughing through it all
Let the dishes wait until tomorrow
Let the whole world disappear
We're dancing in the kitchen
And I'm happy that you're here

[Verse 2]
Barefoot on the wooden floorboards
Singing every melody
We don't need a grand occasion
Just a little company

[Bridge]
Turn it up and spin around
Let the music shake the ground""", "112 BPM", "G major", "None", "Bright down-up strumming"),
    PracticeSong("A Thousand Open Roads", "Acoustic folk rock", "Driving eighth-note strumming and chord endurance", "D – A – Bm – G", """[Verse 1]
Packed my dreams inside a suitcase
Left my worries at the door
There are places I haven't seen yet
There are songs I haven't sung before

[Chorus]
A thousand open roads
A million stories left untold
I'll take the long way home tonight
And chase the fading golden light
A thousand open roads
And everywhere my spirit goes

[Verse 2]
Every stranger has a story
Every town has something new
I don't know where I'll be tomorrow
But today I'll see it through

[Bridge]
No destination owns my soul
The journey makes me whole""", "118 BPM", "D major", "None", "Driving eighth-note strumming"),
    PracticeSong("Underneath the Same Moon", "Acoustic romantic ballad", "Fingerpicked verses and gentle chorus strumming", "Am – F – C – G", """[Verse 1]
When the evening starts to settle
And the stars begin to shine
I imagine you are looking
At the same old moon as mine

[Chorus]
Underneath the same moon
Somewhere you're dreaming too
Though the miles are stretching between us
I still feel connected to you
Underneath the same moon
I'll keep a little light for you

[Verse 2]
Every night becomes a memory
Every sunrise brings a day
And although I cannot hold you
There are things that distance can't take away

[Bridge]
Maybe someday we'll be standing
Where the sky meets the sea""", "78 BPM", "C major (capo 3 shapes)", "3rd fret", "Fingerpicked verses; gentle strumming chorus"),
    PracticeSong("Stronger Than the Storm", "Acoustic country rock", "Firm downstrokes and confident rhythmic pulse", "G – D – C – Em", """[Verse 1]
The clouds came rolling through the valley
And the thunder shook the trees
I was standing in the darkness
Wondering when I'd find some peace

[Chorus]
I'm stronger than the storm
Still standing, still reborn
The wind can try to break me
But it won't decide my fate
I'm stronger than the storm
And I'll rise again

[Verse 2]
Every scar became a lesson
Every fall became a climb
I discovered my own courage
One small step at a time

[Bridge]
Let the lightning fill the sky
I have learned to stay alive""", "108 BPM", "G major", "None", "Strong, steady downstrokes"),
    PracticeSong("Letters Never Sent", "Acoustic singer-songwriter", "Soft fingerpicking and expressive phrasing", "Am – G – F – E", """[Verse 1]
I wrote your name on paper
Then I folded it away
There were things I couldn't tell you
There were words I couldn't say

[Chorus]
These are letters never sent
All the time and love we spent
All the questions left unanswered
All the moments that we meant
Maybe words are not enough
To explain the things we felt

[Verse 2]
Some memories become photographs
Some disappear with time
But the things we leave unspoken
Still find a place inside

[Bridge]
I'll release what I can't change
Let the silence turn the page""", "82 BPM", "A minor", "None", "Soft fingerpicking"),
    PracticeSong("Summer Never Ends", "Acoustic pop rock", "Energetic strumming and consistent tempo", "G – D – A – D", """[Verse 1]
Golden light across the ocean
Barefoot running through the sand
We were young enough for dreaming
With the whole world in our hands

[Chorus]
Let the summer never end
Let the sunlight find us again
We'll keep laughing, we'll keep singing
Hear the happiness we're bringing
Even when the seasons change
Summer lives inside our veins

[Verse 2]
Photographs and faded postcards
Little moments we remember
Every sunset tells a story
That we carry through December

[Bridge]
Close your eyes and feel the breeze
Keep that summer memory""", "120 BPM", "A major (capo 2 shapes)", "2nd fret", "Energetic pop-rock strumming"),
    PracticeSong("Gravity of You", "Acoustic alternative pop", "Arpeggiated verses and full chorus strumming", "Em – C – G – D", """[Verse 1]
I was floating through the silence
Never knowing where to land
Then you came into my orbit
Like a universe unplanned

[Chorus]
It's the gravity of you
Pulling me into the blue
Every road becomes a circle
Every star leads back to you
I don't know what makes it true
It's the gravity of you

[Verse 2]
There are things beyond explaining
There are feelings without names
Like the ocean knows the moonlight
I keep turning just the same

[Bridge]
Maybe love is not a reason
Maybe love is just a feeling""", "90 BPM", "F♯ minor (capo 2 shapes)", "2nd fret", "Arpeggiated verses; full chorus strumming"),
    PracticeSong("The Man I Choose to Be", "Acoustic inspirational", "Steady four-beat strumming and vocal phrasing", "G – C – D – Em", """[Verse 1]
I have made a few mistakes
Taken roads I shouldn't take
But the mirror shows a man
Who can change and make a stand

[Chorus]
I'll be the man I choose to be
With an open heart and honesty
Not defined by where I've been
But by the choices made within
I'll keep learning, I'll keep growing
With a better heart to show

[Verse 2]
I don't need to win each battle
I don't need to prove I'm right
I can choose to build tomorrow
With a little more insight

[Bridge]
Every morning is a chance
To rewrite the circumstance""", "92 BPM", "G major", "None", "Steady four-beat strumming"),
    PracticeSong("Ocean Between Us", "Acoustic dream pop", "Delicate fingerpicking and sustained chord transitions", "D – A – Bm – G", """[Verse 1]
The ocean keeps our secrets
In the language of the tide
I can hear your distant laughter
In the waves that reach my side

[Chorus]
There's an ocean between us
But the stars remain the same
And the wind keeps on reminding
Me of everything we became
There's an ocean between us
And a thousand things to say

[Verse 2]
Ships are crossing through the darkness
Lights are fading in the blue
I keep watching every horizon
For a glimpse of something new

[Bridge]
If the waves could speak tonight
They would tell us we'll be alright""", "76 BPM", "D major (capo 2 shapes)", "2nd fret", "Delicate fingerpicking"),
    PracticeSong("Nothing but the Truth", "Acoustic blues", "Twelve-bar blues shuffle and dominant seventh chord", "E – A – B7", """[Verse 1]
I've been walking down the highway
With my troubles in a bag
I've been smiling for the people
While my heart was feeling bad

[Chorus]
Give me nothing but the truth
No disguise and no excuse
I would rather face the darkness
Than be blinded by a lie
Give me nothing but the truth
Let me see it with my eyes

[Verse 2]
I've heard stories, I've heard promises
I've seen fortunes come and go
But the truth is worth much more
Than the things that people show

[Bridge]
Let the hard words find their place
Honesty can save the day""", "94 BPM", "E major", "None", "Blues shuffle"),
    PracticeSong("Tomorrow Is a Song", "Acoustic folk pop", "Gentle verse dynamics and uplifting chorus strumming", "C – G – Am – F", """[Verse 1]
When the night has taken over
And the world has gone to sleep
There are dreams that keep on calling
There are promises to keep

[Chorus]
Tomorrow is a song
And we've been singing all along
Every sunrise brings a melody
A brand-new possibility
So let the old days fade away
Tomorrow is another day

[Verse 2]
There are mountains left for climbing
There are places left to see
There are people left for loving
There is more life left in me

[Bridge]
One more chord, one more beginning
Every ending has a meaning

[Final Chorus]
Tomorrow is a song
And we've been singing all along
Every sunrise brings a melody
A brand-new possibility
So let the old days fade away
Tomorrow is another day""", "100 BPM", "C major", "None", "Gentle verse; uplifting chorus strumming")

)


@Composable
private fun LyricsScreen(onBack: () -> Unit, onRecordingSaved: (File) -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val lyricsDir = remember { File(context.filesDir, "lyrics").apply { mkdirs() } }
    val drafts = remember {
        mutableStateListOf<File>().apply {
            addAll(lyricsDir.listFiles()?.filter { it.extension == "txt" }?.sortedByDescending { it.lastModified() } ?: emptyList())
        }
    }
    var selectedFile by remember { mutableStateOf<File?>(null) }
    var title by remember { mutableStateOf("") }
    var lyrics by remember { mutableStateOf("") }
    var status by remember { mutableStateOf("Your words stay saved on this phone.") }
    var showKaraoke by remember { mutableStateOf(false) }
    var showPracticeSongs by remember { mutableStateOf(true) }
    var previewSong by remember { mutableStateOf<PracticeSong?>(null) }
    if (previewSong != null) {
        val song = previewSong!!
        GuitarPracticePreview(title = song.title, style = song.style, chordsText = song.chords, tempoText = song.tempo, capo = song.capo, technique = song.strumming.ifBlank { song.focus }, lyrics = song.lyrics, onBack = { previewSong = null })
        return
    }
    if (showKaraoke) {
        KaraokeRecordingScreen(title = title, lyrics = lyrics, onBack = { showKaraoke = false }, onSaved = onRecordingSaved)
        return
    }

    fun refreshDrafts() {
        drafts.clear()
        drafts.addAll(lyricsDir.listFiles()?.filter { it.extension == "txt" }?.sortedByDescending { it.lastModified() } ?: emptyList())
    }
    fun openDraft(file: File) {
        try {
            val lines = file.readLines()
            title = lines.firstOrNull()?.removePrefix("# ") ?: file.nameWithoutExtension
            lyrics = lines.drop(2).joinToString("\n")
            selectedFile = file
            status = "Draft opened"
        } catch (error: Exception) {
            status = "Could not open draft: " + (error.localizedMessage ?: "File error")
        }
    }
    fun newDraft() {
        selectedFile = null
        title = "Untitled Song"
        lyrics = ""
        status = "New lyric draft"
    }
    fun saveDraft() {
        try {
            val cleanTitle = title.trim().ifBlank { "Untitled Song" }
            val file = selectedFile ?: File(lyricsDir, "Lyric_" + System.currentTimeMillis() + ".txt")
            file.writeText("# " + cleanTitle + "\n\n" + lyrics)
            selectedFile = file
            title = cleanTitle
            refreshDrafts()
            status = "Saved on this phone"
        } catch (error: Exception) {
            status = "Save failed: " + (error.localizedMessage ?: "Storage error")
        }
    }

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedButton(onClick = onBack) { Text("← Studio") }
            Spacer(Modifier.weight(1f))
            Text("TOON STUDIO  /  LYRIC BOOK", color = Teal, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.3.sp)
        }
        Text("Write your song.", color = Ink, fontSize = 28.sp, fontWeight = FontWeight.Bold)
        Text("A private space for hooks, verses, bridges, and unfinished ideas.", color = Muted, fontSize = 12.sp)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { newDraft() }, modifier = Modifier.weight(1f)) { Text("+ New lyric") }
            OutlinedButton(onClick = { saveDraft() }, modifier = Modifier.weight(1f), enabled = title.isNotBlank() || lyrics.isNotBlank()) { Text("Save draft") }
        }
        OutlinedTextField(value = title, onValueChange = { title = it }, modifier = Modifier.fillMaxWidth(), label = { Text("Song title") }, singleLine = true)
        OutlinedTextField(
            value = lyrics,
            onValueChange = { lyrics = it },
            modifier = Modifier.fillMaxWidth().height(390.dp),
            label = { Text("Lyrics") },
            placeholder = { Text("Verse 1\n\nPre-Chorus\n\nChorus\n\nVerse 2\n\nBridge\n\nFinal Chorus") },
            minLines = 12
        )
        Button(onClick = { showKaraoke = true }, modifier = Modifier.fillMaxWidth(), enabled = lyrics.isNotBlank()) { Text("SING & RECORD") }
        Text(status, color = Teal, fontSize = 11.sp)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("ACOUSTIC PRACTICE SONGS", color = Ink, fontSize = 14.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            TextButton(onClick = { showPracticeSongs = !showPracticeSongs }) { Text(if (showPracticeSongs) "Hide" else "Show") }
        }
        Text("25 original songs with guitar chords and practice guidance. Load one into the editor, then open Sing & Record.", color = Muted, fontSize = 11.sp)
        if (showPracticeSongs) {
            acousticPracticeSongs.forEach { song ->
                Card(colors = CardDefaults.cardColors(containerColor = Panel), shape = RoundedCornerShape(14.dp)) {
                    Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(song.title, color = Ink, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                        Text(song.style + "  •  " + song.focus, color = Muted, fontSize = 10.sp)
                        Text("Guitar: " + song.chords, color = Teal, fontSize = 11.sp, fontWeight = FontWeight.Medium)
                        if (song.tempo.isNotBlank()) Text("${song.tempo}  •  ${song.key}  •  Capo: ${song.capo}", color = Muted, fontSize = 10.sp)
                        if (song.strumming.isNotBlank()) Text("Technique: " + song.strumming, color = Muted, fontSize = 10.sp)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                            OutlinedButton(onClick = {
                                title = song.title
                                lyrics = song.lyrics
                                selectedFile = null
                                status = "Practice song loaded. Save it to keep an editable copy."
                            }, modifier = Modifier.weight(1f)) { Text("LOAD LYRICS", fontSize = 10.sp) }
                            Button(onClick = { previewSong = song }, modifier = Modifier.weight(1f)) { Text("GUITAR PREVIEW", fontSize = 10.sp) }
                        }
                    }
                }
            }
        }
        Text("SAVED LYRICS", color = Ink, fontSize = 14.sp, fontWeight = FontWeight.Bold)
        if (drafts.isEmpty()) {
            Text("No saved lyrics yet. Start a new draft and tap Save.", color = Muted, fontSize = 12.sp)
        } else {
            drafts.forEach { file ->
                Card(colors = CardDefaults.cardColors(containerColor = Panel), shape = RoundedCornerShape(14.dp)) {
                    Row(modifier = Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(file.nameWithoutExtension, color = Ink, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                            Text("Saved lyric draft", color = Muted, fontSize = 10.sp)
                        }
                        OutlinedButton(onClick = { openDraft(file) }) { Text(if (selectedFile == file) "Open ✓" else "Open") }
                        OutlinedButton(onClick = {
                            if (file == selectedFile) { selectedFile = null; title = ""; lyrics = "" }
                            file.delete()
                            refreshDrafts()
                            status = "Draft deleted"
                        }) { Text("Delete", color = Color(0xFFFF8585)) }
                    }
                }
            }
        }
        Text("Lyric drafts are stored privately inside Toon Studio on this device.", color = Muted, fontSize = 10.sp)
    }
}

@Composable
private fun ToneControl(label: String, value: Float, onValueChange: (Float) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = Muted, fontSize = 11.sp, modifier = Modifier.weight(1f))
        Text("${(value * 100).toInt()}%", color = Teal, fontSize = 11.sp, fontWeight = FontWeight.Bold)
    }
    Slider(value = value, onValueChange = onValueChange, valueRange = 0f..1f)
}

private fun formatTime(seconds: Long): String = "%02d:%02d".format(seconds / 60, seconds % 60)
private fun formatMillis(milliseconds: Int): String = "%02d:%02d".format(milliseconds / 60000, (milliseconds / 1000) % 60)
private fun formatBytes(bytes: Long): String = if (bytes < 1024) "$bytes B" else String.format(Locale.US, "%.1f MB", bytes / (1024.0 * 1024.0))


@Composable
private fun KaraokeRecordingScreen(title: String, lyrics: String, onBack: () -> Unit, onSaved: (File) -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val recordingsDir = remember { File(context.filesDir, "recordings").apply { mkdirs() } }
    val scrollState = rememberScrollState()
    val density = androidx.compose.ui.platform.LocalDensity.current
    var recorder by remember { mutableStateOf<MediaRecorder?>(null) }
    var recordingFile by remember { mutableStateOf<File?>(null) }
    var isRecording by remember { mutableStateOf(false) }
    var startedAt by remember { mutableStateOf(0L) }
    var elapsed by remember { mutableStateOf(0L) }
    var autoScroll by remember { mutableStateOf(true) }
    var scrollSpeed by remember { mutableStateOf(42f) }
    var status by remember { mutableStateOf("Ready. Use headphones if you have separate playback.") }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        status = if (granted) "Microphone ready. Tap Record to begin." else "Microphone permission is required."
    }
    fun startTake() {
        try {
            val stamp = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.US).format(Date())
            val file = File(recordingsDir, "JackTunes_Karaoke_$stamp.m4a")
            val newRecorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) MediaRecorder(context) else @Suppress("DEPRECATION") MediaRecorder()
            newRecorder.setAudioSource(MediaRecorder.AudioSource.MIC)
            newRecorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            newRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            newRecorder.setAudioEncodingBitRate(192000)
            newRecorder.setAudioSamplingRate(48000)
            newRecorder.setOutputFile(file.absolutePath)
            newRecorder.prepare()
            newRecorder.start()
            recorder = newRecorder
            recordingFile = file
            startedAt = System.currentTimeMillis()
            elapsed = 0L
            isRecording = true
            status = "Recording your singing and guitar through the microphone."
        } catch (error: Exception) {
            try { recorder?.release() } catch (_: Exception) {}
            recorder = null
            isRecording = false
            status = "Could not start recording: " + (error.localizedMessage ?: "Audio unavailable")
        }
    }
    fun stopTake() {
        try {
            recorder?.stop()
            recordingFile?.let { if (it.exists() && it.length() > 0) onSaved(it) }
            status = "Take saved in your Toon Studio recordings."
        } catch (_: Exception) {
            recordingFile?.delete()
            status = "Recording was too short. Try again."
        } finally {
            try { recorder?.release() } catch (_: Exception) {}
            recorder = null
            recordingFile = null
            isRecording = false
        }
    }
    DisposableEffect(Unit) {
        onDispose {
            try { recorder?.stop() } catch (_: Exception) {}
            try { recorder?.release() } catch (_: Exception) {}
        }
    }
    LaunchedEffect(isRecording) {
        while (isRecording) {
            elapsed = (System.currentTimeMillis() - startedAt) / 1000L
            delay(250)
        }
    }
    LaunchedEffect(autoScroll, scrollSpeed) {
        if (autoScroll) {
            val pixelsPerTick = with(density) { (scrollSpeed * 0.1f).dp.roundToPx() }.coerceAtLeast(1)
            while (autoScroll && scrollState.value < scrollState.maxValue) {
                scrollState.scrollTo((scrollState.value + pixelsPerTick).coerceAtMost(scrollState.maxValue))
                delay(100)
            }
        }
    }
    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedButton(onClick = onBack, enabled = !isRecording) { Text("Back to lyrics") }
            Spacer(Modifier.weight(1f))
            Text("KARAOKE RECORD", color = Teal, fontSize = 10.sp, fontWeight = FontWeight.Bold)
        }
        Text(title.ifBlank { "Untitled Song" }, color = Ink, fontSize = 24.sp, fontWeight = FontWeight.Bold)
        Card(colors = CardDefaults.cardColors(containerColor = Panel), shape = RoundedCornerShape(18.dp)) {
            Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(if (isRecording) "RECORDING" else "READY TO RECORD", color = if (isRecording) Color(0xFFFF7777) else Teal, fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                    Text(formatTime(elapsed), color = Ink, fontSize = 22.sp)
                }
                Button(onClick = {
                    if (isRecording) stopTake()
                    else if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) startTake()
                    else permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                }, modifier = Modifier.fillMaxWidth().height(50.dp), colors = ButtonDefaults.buttonColors(containerColor = if (isRecording) Color(0xFFB84D55) else Teal)) {
                    Text(if (isRecording) "STOP & SAVE TAKE" else "START SINGING & RECORD")
                }
                Text(status, color = Muted, fontSize = 10.sp)
            }
        }
        Card(colors = CardDefaults.cardColors(containerColor = Panel), shape = RoundedCornerShape(16.dp)) {
            Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("AUTO-SCROLL LYRICS", color = Ink, fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                    Switch(checked = autoScroll, onCheckedChange = { autoScroll = it })
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Scroll speed: " + scrollSpeed.toInt() + " dp/second", color = Muted, fontSize = 10.sp, modifier = Modifier.weight(1f))
                    OutlinedButton(
                        onClick = { scrollSpeed = (scrollSpeed - 5f).coerceAtLeast(10f) },
                        enabled = autoScroll && scrollSpeed > 10f,
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 2.dp)
                    ) { Text("−", fontSize = 18.sp) }
                    OutlinedButton(
                        onClick = { scrollSpeed = (scrollSpeed + 5f).coerceAtMost(120f) },
                        enabled = autoScroll && scrollSpeed < 120f,
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 2.dp)
                    ) { Text("+", fontSize = 18.sp) }
                }
                Slider(value = scrollSpeed, onValueChange = { scrollSpeed = it }, valueRange = 10f..120f, enabled = autoScroll)
                Text("Turn auto-scroll off to move through lyrics manually.", color = Muted, fontSize = 10.sp)
            }
        }
        Card(Modifier.weight(1f).fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = Color(0xFF101D1B)), shape = RoundedCornerShape(18.dp)) {
            Column(Modifier.fillMaxSize().verticalScroll(scrollState).padding(horizontal = 20.dp, vertical = 24.dp)) {
                Text(lyrics, color = Ink, fontSize = 23.sp, lineHeight = 36.sp, fontWeight = FontWeight.Medium)
                Spacer(Modifier.height(220.dp))
            }
        }
        Text("Records microphone audio, including your singing and acoustic guitar. Use headphones for separate playback.", color = Muted, fontSize = 10.sp)
    }
}
