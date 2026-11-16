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
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
        setContent { ToonJackApp() }
    }
}

@Composable
private fun ToonJackApp() {
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
    val noteNames = listOf("C", "C♯", "D", "D♯", "E", "F", "F♯", "G", "G♯", "A", "A♯", "B")
    val coroutineScope = rememberCoroutineScope()

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
            val file = File(recordingsDir, "ToonJack_$stamp.m4a")
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
            text = { Text("This permanently deletes ${deleteTarget?.nameWithoutExtension ?: "this take"} from Toon Jack on this phone. This cannot be undone.") },
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
            Column(
                modifier = Modifier.fillMaxSize().padding(horizontal = 18.dp, vertical = 18.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("TOON JACK  /  PRIVATE STUDIO", color = Teal, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
                        Text("Make something real.", color = Ink, fontSize = 27.sp, fontWeight = FontWeight.Bold)
                        Text("Your voice. Your songs. Your space.", color = Muted, fontSize = 12.sp)
                    }
                    Text("🎙️", fontSize = 34.sp)
                }

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
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.weight(1f)) {
                        items(recordings, key = { it.name }) { file ->
                            Card(colors = CardDefaults.cardColors(containerColor = Panel), shape = RoundedCornerShape(15.dp)) {
                                Column(Modifier.fillMaxWidth().padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Column(Modifier.weight(1f)) {
                                            Text(file.nameWithoutExtension.replace("ToonJack_", ""), color = Ink, fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
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
                                                    message = "Tuning vocal… keep Toon Jack open."
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
                                                                com.toonjack.studio.audio.OfflineAudioRenderer.renderDreamVoice(file, output)
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

private fun formatTime(seconds: Long): String = "%02d:%02d".format(seconds / 60, seconds % 60)
private fun formatMillis(milliseconds: Int): String = "%02d:%02d".format(milliseconds / 60000, (milliseconds / 1000) % 60)
private fun formatBytes(bytes: Long): String = if (bytes < 1024) "$bytes B" else String.format(Locale.US, "%.1f MB", bytes / (1024.0 * 1024.0))
