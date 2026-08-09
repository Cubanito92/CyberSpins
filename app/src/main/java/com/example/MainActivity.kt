package com.example

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.core.content.ContextCompat
import com.example.ui.RadioStudioViewModel
import com.example.ui.components.StudioConsoleScreen
import com.example.ui.theme.RadioStudioTheme

class MainActivity : ComponentActivity() {

    private val viewModel: RadioStudioViewModel by viewModels()

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        // Permission result handled
    }

    private val pickSongDeckALauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? -> uri?.let { onSongPicked(deck = 0, uri = it) } }

    private val pickSongDeckBLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? -> uri?.let { onSongPicked(deck = 1, uri = it) } }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        checkAndRequestAudioPermission()

        setContent {
            RadioStudioTheme {
                StudioConsoleScreen(
                    viewModel = viewModel,
                    onPickSongForDeckA = { pickSongDeckALauncher.launch(arrayOf("audio/*")) },
                    onPickSongForDeckB = { pickSongDeckBLauncher.launch(arrayOf("audio/*")) }
                )
            }
        }
    }

    private fun onSongPicked(deck: Int, uri: Uri) {
        try {
            contentResolver.takePersistableUriPermission(
                uri,
                android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
        } catch (e: SecurityException) {
            // Some providers don't support persistable permissions; the
            // MediaPlayer can still read it for this session.
        }
        val name = queryDisplayName(uri) ?: "Canción"
        viewModel.loadTrack(deck, uri, name)
    }

    private fun queryDisplayName(uri: Uri): String? {
        return try {
            contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (nameIndex >= 0 && cursor.moveToFirst()) cursor.getString(nameIndex) else null
            }
        } catch (e: Exception) {
            null
        }
    }

    private fun checkAndRequestAudioPermission() {
        if (ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.RECORD_AUDIO
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }
}
