package com.toonjack.studio

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

@Composable
fun RecordingBackupCard(recordingsDir: File) {
    val context = LocalContext.current
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    val recordings = remember(recordingsDir) {
        recordingsDir.listFiles()?.filter { it.isFile && it.extension.equals("m4a", true) } ?: emptyList()
    }
    var status by remember { mutableStateOf("Export your takes before uninstalling or changing phones.") }

    val createBackup = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/zip")
    ) { uri: Uri? ->
        if (uri != null) {
            scope.launch {
                status = try {
                    withContext(Dispatchers.IO) {
                        context.contentResolver.openOutputStream(uri)?.use { output ->
                            ZipOutputStream(BufferedOutputStream(output)).use { zip ->
                                recordingsDir.listFiles()
                                    ?.filter { it.isFile && it.extension.equals("m4a", true) }
                                    ?.forEach { file ->
                                        zip.putNextEntry(ZipEntry(file.name))
                                        file.inputStream().buffered().use { input -> input.copyTo(zip) }
                                        zip.closeEntry()
                                    }
                            }
                        } ?: error("Could not open the selected backup location")
                    }
                    "Backup saved: ${recordings.size} recording(s). Keep the ZIP somewhere safe."
                } catch (error: Exception) {
                    "Backup failed: ${error.localizedMessage ?: "Storage error"}"
                }
            }
        }
    }

    val restoreBackup = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            scope.launch {
                status = try {
                    withContext(Dispatchers.IO) {
                        context.contentResolver.openInputStream(uri)?.use { input ->
                            ZipInputStream(BufferedInputStream(input)).use { zip ->
                                var entry = zip.nextEntry
                                var restored = 0
                                while (entry != null) {
                                    val safeName = File(entry.name).name
                                    if (!entry.isDirectory && safeName.endsWith(".m4a", true)) {
                                        val target = File(recordingsDir, safeName)
                                        if (!target.exists()) {
                                            target.outputStream().buffered().use { output -> zip.copyTo(output) }
                                            restored++
                                        }
                                    }
                                    zip.closeEntry()
                                    entry = zip.nextEntry
                                }
                                restored
                            }
                        } ?: error("Could not read the selected backup")
                    }.let { "Restore complete: ${it} recording(s) added. Existing files were kept." }
                } catch (error: Exception) {
                    "Restore failed: ${error.localizedMessage ?: "Invalid backup"}"
                }
            }
        }
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(18.dp)
    ) {
        Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("RECORDING BACKUP", style = MaterialTheme.typography.titleMedium)
            Text("${recordings.size} recording(s) found. Backups are ZIP files saved outside Toon Jack.", style = MaterialTheme.typography.bodySmall)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = { createBackup.launch("ToonJack_Recordings_Backup.zip") },
                    enabled = recordings.isNotEmpty(),
                    modifier = Modifier.weight(1f)
                ) { Text("BACK UP") }
                OutlinedButton(
                    onClick = { restoreBackup.launch(arrayOf("application/zip", "application/octet-stream")) },
                    modifier = Modifier.weight(1f)
                ) { Text("RESTORE") }
            }
            Text(status, style = MaterialTheme.typography.bodySmall)
            Text("Back up before uninstalling. Android removes app-private recordings when the app is uninstalled.", style = MaterialTheme.typography.bodySmall)
        }
    }
}
