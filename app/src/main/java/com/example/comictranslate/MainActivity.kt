package com.example.comictranslate

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.example.comictranslate.ui.theme.ComicTranslateTheme
import org.opencv.android.OpenCVLoader

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // --- Initialize OpenCV here ---
        if (OpenCVLoader.initLocal()) {
            Log.i("OpenCV", "OpenCV loaded successfully")
        } else {
            Log.e("OpenCV", "OpenCV initialization failed!")
            Toast.makeText(this, "OpenCV initialization failed!", Toast.LENGTH_LONG).show()
        }
        // --- End of OpenCV initialization ---

        setContent {
            ComicTranslateTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    ScreenCaptureScreen()
                }
            }
        }
    }
}

@Composable
fun ScreenCaptureScreen() {
    val context = LocalContext.current
    val mediaProjectionManager =
        context.getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager

    val startMediaProjection = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult(),
        onResult = { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                val intent = Intent(context, ScreenCaptureService::class.java).apply {
                    putExtra("resultCode", result.resultCode)
                    putExtra("data", result.data)
                }
                ContextCompat.startForegroundService(context, intent)
            }
        }
    )

    val overlayPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult(),
        onResult = { }
    )

    LaunchedEffect(Unit) {
        if (!Settings.canDrawOverlays(context)) {
            val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${context.packageName}"))
            overlayPermissionLauncher.launch(intent)
        }
    }

    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Button(onClick = {
            if (Settings.canDrawOverlays(context)) {
                startMediaProjection.launch(mediaProjectionManager.createScreenCaptureIntent())
                val translator = SpeechBalloonTranslator(context)
                translator.debugMode = true // ✅ AKTIFKAN DEBUG

            } else {
                val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${context.packageName}"))
                overlayPermissionLauncher.launch(intent)
            }
        }) {
            Text("Start Continuous Translation")
        }
        Spacer(modifier = Modifier.height(16.dp))
        Button(onClick = {
            // Send an intent with the STOP action to the service
            val stopIntent = Intent(context, ScreenCaptureService::class.java).apply {
                action = ScreenCaptureService.ACTION_STOP
            }
            context.startService(stopIntent)
        }) {
            Text("Stop Translation")
        }
    }
}