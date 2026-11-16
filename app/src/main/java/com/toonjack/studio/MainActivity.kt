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
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import kotlinx.coroutines.delay
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
    val recordingsDir = remember {
        File(context.filesDir, "recordings").apply { mkdirs() }
    }
    val recordings = remember { mutableStateListOf<File>().apply { addAll(recordingsDir.listFiles()?.filter { it.extension == "m4a" }?.sortedByDescending { it.lastModified() } ?: emptyList()) } }
    var recorder by remember { mutableStateOf<MediaRecorder?>(null) }
    var player by remember { mutableStateOf<MediaPlayer?>(null) }
    var recordingFile by remember { mutableStateOf<File?>(null) }
    var isRecording by remember { mutableStateOf(false) }
    var recordingStarted by remember { mutableStateOf(0L) }
    var elapsed by remember { mutableStateOf(0L) }
    var playingName by remember { mutableStateOf<String?>(null) }
    var message by remember { mutableStateOf("Your private recording space") }

    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) message = "Microphone ready. Tap Record to begin."
        else message = "Microphone permission is needed to record."
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
            recorder?.release()
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
            player = MediaPlayer().apply {
                setDataSource(file.absolutePath)
                setOnCompletionListener {
                    it.release()
                    player = null
                    playingName = null
                }
                prepare()
                start()
            }
            playingName = file.name
        } catch (error: Exception) {
            message = "Playback failed: ${error.localizedMessage ?: "Unknown error"}"
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            try { recorder?.stop() } catch (_: Exception) {}
            recorder?.release()
            player?.release()
        }
    }

    LaunchedEffect(isRecording) {
        while (isRecording) {
            elapsed = (System.currentTimeMillis() - recordingStarted) / 1000
            delay(250)
        }
    }

    MaterialTheme(colorScheme = darkColorScheme(
        primary = Teal, onPrimary = Color(0xFF00201D),
        background = Background, surface = Panel, onSurface = Ink
    )) {
        Surface(modifier = Modifier.fillMaxSize(), color = Background) {
            Column(
                modifier = Modifier.fillMaxSize().padding(horizontal = 22.dp, vertical = 24.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("TOON JACK", color = Teal, fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 3.sp)
                        Text("Private Studio", color = Ink, fontSize = 28.sp, fontWeight = FontWeight.Bold)
                    }
                    Text("🎙️", fontSize = 34.sp)
                }

                Card(colors = CardDefaults.cardColors(containerColor = Panel), shape = RoundedCornerShape(24.dp)) {
                    Column(Modifier.fillMaxWidth().padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("RECORDING SESSION", color = Muted, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
                        Spacer(Modifier.height(18.dp))
                        Text(if (isRecording) "●" else "◉", color = if (isRecording) Color(0xFFFF6666) else Teal, fontSize = 48.sp)
                        Text(formatTime(elapsed), color = Ink, fontSize = 38.sp, fontWeight = FontWeight.Light)
                        Spacer(Modifier.height(8.dp))
                        Text(message, color = Muted, fontSize = 13.sp)
                        Spacer(Modifier.height(20.dp))
                        Button(
                            onClick = {
                                if (isRecording) stopRecording()
                                else if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) startRecording()
                                else permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                            },
                            modifier = Modifier.fillMaxWidth().height(58.dp),
                            shape = RoundedCornerShape(18.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = if (isRecording) Color(0xFFB84D55) else Teal)
                        ) {
                            Text(if (isRecording) "STOP & SAVE TAKE" else "●  START RECORDING", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                        }
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("YOUR TAKES", color = Ink, fontSize = 18.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                    Text("${recordings.size} saved", color = Muted, fontSize = 12.sp)
                }

                if (recordings.isEmpty()) {
                    Card(colors = CardDefaults.cardColors(containerColor = Panel), shape = RoundedCornerShape(18.dp)) {
                        Column(Modifier.fillMaxWidth().padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("♫", color = Teal, fontSize = 30.sp)
                            Text("Your recordings will appear here", color = Muted, fontSize = 13.sp)
                        }
                    }
                } else {
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(9.dp), modifier = Modifier.weight(1f)) {
                        items(recordings, key = { it.name }) { file ->
                            Card(colors = CardDefaults.cardColors(containerColor = Panel), shape = RoundedCornerShape(15.dp)) {
                                Row(Modifier.fillMaxWidth().padding(13.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Column(Modifier.weight(1f)) {
                                        Text(file.nameWithoutExtension.replace("ToonJack_", ""), color = Ink, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                                        Text(formatBytes(file.length()), color = Muted, fontSize = 11.sp)
                                    }
                                    OutlinedButton(onClick = { if (playingName == file.name) { player?.pause(); player?.seekTo(0); player?.release(); player = null; playingName = null } else play(file) }) {
                                        Text(if (playingName == file.name) "Stop" else "Play")
                                    }
                                }
                            }
                        }
                    }
                }
                Text("Original takes stay on this device. Vocal effects are coming next.", color = Muted, fontSize = 11.sp)
            }
        }
    }
}

private fun formatTime(seconds: Long): String = "%02d:%02d".format(seconds / 60, seconds % 60)
private fun formatBytes(bytes: Long): String = if (bytes < 1024) "$bytes B" else String.format(Locale.US, "%.1f MB", bytes / (1024.0 * 1024.0))
