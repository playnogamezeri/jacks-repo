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
import androidx.compose.ui.unit.roundToPx
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
    var dreamWarmth by remember { mutableStateOf(0.28f) }
    var dreamSoftness by remember { mutableStateOf(0.42f) }
    var dreamAir by remember { mutableStateOf(0.32f) }
    var dreamCompression by remember { mutableStateOf(0.34f) }
    var dreamDeEss by remember { mutableStateOf(0.35f) }
    var dreamAmbience by remember { mutableStateOf(0.16f) }
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
            if (showLyrics) {
                LyricsScreen(onBack = { showLyrics = false }, onRecordingSaved = { file -> recordings.add(0, file) })
            } else {
            Column(
                modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 18.dp, vertical = 18.dp),
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
            Text("TOON JACK  /  LYRIC BOOK", color = Teal, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.3.sp)
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
        Text("Lyric drafts are stored privately inside Toon Jack on this device.", color = Muted, fontSize = 10.sp)
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
            val file = File(recordingsDir, "ToonJack_Karaoke_$stamp.m4a")
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
            status = "Take saved in your Toon Jack recordings."
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
                Text("Scroll speed: " + scrollSpeed.toInt() + " dp/second", color = Muted, fontSize = 10.sp)
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
