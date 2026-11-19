package com.toonjack.studio

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin

@Composable
internal fun GuitarPracticePreview(title: String, style: String, chordsText: String, tempoText: String, capo: String, technique: String, lyrics: String, onBack: () -> Unit) {
    val ink = Color(0xFFEAF5F3); val muted = Color(0xFF9CB2AE); val teal = Color(0xFF20B5A8); val panel = Color(0xFF172724)
    val chords = remember(chordsText) { chordsText.split("–", "-", "—").map { it.trim() }.filter { it.isNotBlank() }.distinct() }
    val baseTempo = remember(tempoText) { Regex("\\d+").find(tempoText)?.value?.toIntOrNull()?.coerceIn(50, 160) ?: 90 }
    var tempo by remember(baseTempo) { mutableFloatStateOf(baseTempo.toFloat()) }
    var playing by remember { mutableStateOf(false) }; var status by remember { mutableStateOf("Ready · synthesized plucked-string preview") }
    var track by remember { mutableStateOf<AudioTrack?>(null) }; val scope = rememberCoroutineScope()
    DisposableEffect(Unit) { onDispose { try { track?.stop(); track?.release() } catch (_: Exception) {}; track = null } }
    fun stop() { try { track?.stop(); track?.release() } catch (_: Exception) {}; track = null; playing = false; status = "Preview stopped" }
    fun play() {
        if (playing) { stop(); return }
        playing = true; status = "Generating preview…"
        scope.launch {
            val pcm = withContext(Dispatchers.Default) { guitarPreviewPcm(chords.ifEmpty { listOf("G","D","Em","C") }, tempo.toInt()) }
            try {
                val min = AudioTrack.getMinBufferSize(44100, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT)
                val audio = AudioTrack.Builder().setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build())
                    .setAudioFormat(AudioFormat.Builder().setSampleRate(44100).setChannelMask(AudioFormat.CHANNEL_OUT_MONO).setEncoding(AudioFormat.ENCODING_PCM_16BIT).build())
                    .setBufferSizeInBytes(maxOf(min, pcm.size * 2)).setTransferMode(AudioTrack.MODE_STATIC).build()
                audio.write(pcm, 0, pcm.size); track = audio
                audio.setNotificationMarkerPosition(pcm.size)
                audio.setPlaybackPositionUpdateListener(object : AudioTrack.OnPlaybackPositionUpdateListener {
                    override fun onMarkerReached(player: AudioTrack) { try { player.stop(); player.release() } catch (_: Exception) {}; track = null; playing = false; status = "Preview complete" }
                    override fun onPeriodicNotification(player: AudioTrack) {}
                })
                audio.play(); status = "Playing chord progression at " + tempo.toInt() + " BPM"
            } catch (e: Exception) { playing = false; status = "Playback unavailable: " + (e.localizedMessage ?: "audio error") }
        }
    }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        OutlinedButton(onClick = { stop(); onBack() }) { Text("← Back to Lyric Book") }
        Text("GUITAR PRACTICE / PREVIEW", color = teal, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.4.sp)
        Text(title, color = ink, fontSize = 26.sp, fontWeight = FontWeight.Bold); Text(style, color = muted, fontSize = 12.sp)
        Card(colors = CardDefaults.cardColors(containerColor = panel), shape = RoundedCornerShape(18.dp)) {
            Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("AUDIO PREVIEW", color = teal, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                Text("Synthesized plucked-string demonstration of the chord progression. This is a practice reference, not a recorded acoustic guitar performance.", color = muted, fontSize = 11.sp)
                Button(onClick = { play() }, modifier = Modifier.fillMaxWidth()) { Text(if (playing) "STOP PREVIEW" else "▶ PLAY GUITAR PREVIEW") }
                Text(status, color = muted, fontSize = 10.sp)
                Text("Practice tempo: " + tempo.toInt() + " BPM", color = ink, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                Slider(value = tempo, onValueChange = { tempo = it }, valueRange = 50f..160f, enabled = !playing)
                Text("Original tempo: " + tempoText, color = muted, fontSize = 10.sp)
            }
        }
        Card(colors = CardDefaults.cardColors(containerColor = panel), shape = RoundedCornerShape(18.dp)) {
            Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                Text("CHORD PROGRESSION", color = teal, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                    chords.forEachIndexed { i, chord ->
                        Card(Modifier.weight(1f), colors = CardDefaults.cardColors(containerColor = Color(0xFF203A35)), shape = RoundedCornerShape(10.dp)) {
                            Column(Modifier.fillMaxWidth().padding(vertical = 12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                Text((i + 1).toString(), color = muted, fontSize = 9.sp); Text(chord, color = ink, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
                Text("Repeat this progression through the verse and chorus unless the arrangement indicates a change.", color = muted, fontSize = 10.sp)
            }
        }
        Card(colors = CardDefaults.cardColors(containerColor = panel), shape = RoundedCornerShape(18.dp)) {
            Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("GUITAR GUIDE", color = teal, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                Text("Capo: " + capo, color = ink, fontSize = 12.sp); Text("Technique: " + technique, color = ink, fontSize = 12.sp)
                Text("Common chord-shape references · strings listed low E to high e:", color = muted, fontSize = 10.sp)
                chords.forEach { chord -> ChordShapeLine(chord, ink, teal) }
            }
        }
        Card(colors = CardDefaults.cardColors(containerColor = panel), shape = RoundedCornerShape(18.dp)) {
            Column(Modifier.fillMaxWidth().padding(14.dp)) { Text("LYRICS / SECTION CUES", color = teal, fontSize = 11.sp, fontWeight = FontWeight.Bold); Spacer(Modifier.height(8.dp)); Text(lyrics, color = ink, fontSize = 13.sp, lineHeight = 21.sp) }
        }
    }
}

@Composable
private fun ChordShapeLine(chord: String, ink: Color, teal: Color) {
    val shapes = mapOf("C" to "x 3 2 0 1 0", "G" to "3 2 0 0 0 3", "D" to "x x 0 2 3 2", "A" to "x 0 2 2 2 0", "Am" to "x 0 2 2 1 0", "Em" to "0 2 2 0 0 0", "E" to "0 2 2 1 0 0", "F" to "1 3 3 2 1 1", "Bm" to "x 2 4 4 3 2", "B7" to "x 2 1 2 0 2")
    Text(chord + "   " + (shapes[chord] ?: "Use an appropriate voicing"), color = teal, fontSize = 11.sp)
}

private fun guitarPreviewPcm(chords: List<String>, bpm: Int): ShortArray {
    val rate = 44100; val beat = 60.0 / bpm.coerceIn(50, 160); val chordDuration = beat * 4.0
    val notes = mapOf("C" to doubleArrayOf(130.81,164.81,196.0,261.63,329.63), "G" to doubleArrayOf(98.0,146.83,196.0,246.94,392.0), "D" to doubleArrayOf(146.83,220.0,293.66,369.99), "A" to doubleArrayOf(110.0,164.81,220.0,277.18), "Am" to doubleArrayOf(110.0,164.81,220.0,261.63), "Em" to doubleArrayOf(82.41,123.47,164.81,196.0,246.94), "E" to doubleArrayOf(82.41,123.47,164.81,207.65,246.94), "F" to doubleArrayOf(87.31,130.81,174.61,220.0,261.63), "Bm" to doubleArrayOf(123.47,185.0,246.94,293.66), "B7" to doubleArrayOf(123.47,155.56,185.0,246.94))
    val length = (rate * chordDuration * chords.size * 2).toInt().coerceAtMost(rate * 30); val out = ShortArray(length); val pattern = intArrayOf(0,2,1,3,2,1,3,2)
    chords.forEachIndexed { ci, chord ->
        val chordNotes = notes[chord] ?: notes["G"]!!; val start = (ci * chordDuration * 2 * rate).toInt(); val section = (chordDuration * 2 * rate).toInt()
        for (step in 0 until 8) {
            val frequency = chordNotes[pattern[step] % chordNotes.size]; val noteStart = start + step * section / 8; val noteLength = section / 3
            for (i in 0 until noteLength) {
                val p = noteStart + i; if (p >= out.size) break
                val t = i.toDouble() / rate; val env = exp(-t * 4.8) * (1.0 - exp(-t * 180.0))
                val wave = sin(2.0 * PI * frequency * t) + 0.38 * sin(2.0 * PI * frequency * 2.01 * t) + 0.16 * sin(2.0 * PI * frequency * 3.97 * t)
                val sample = (wave * env * 4800.0).toInt()
                out[p] = (out[p].toInt() + sample).coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
            }
        }
    }
    return out
}
