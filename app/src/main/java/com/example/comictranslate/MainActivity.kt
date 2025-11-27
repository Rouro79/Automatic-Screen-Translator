package com.example.comictranslate

import android.content.Context
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue

class MainActivity : ComponentActivity() {

    private lateinit var mediaProjectionManager: MediaProjectionManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        mediaProjectionManager =
            getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager

        setContent {
            ScreenCaptureUI()
        }
    }

    @Composable
    fun ScreenCaptureUI() {

        var lastResultCode by remember { mutableStateOf<Int?>(null) }
        var lastResultData by remember { mutableStateOf<Intent?>(null) }

        val launcher =
            rememberLauncherForActivityResult(
                ActivityResultContracts.StartActivityForResult()
            ) { result ->

                val code = result.resultCode
                val data = result.data

                if (code != RESULT_OK || data == null) {
                    Log.e("MainActivity", "Screen capture permission denied")
                    return@rememberLauncherForActivityResult
                }

                lastResultCode = code
                lastResultData = data

                Log.d("MainActivity", "Permission granted → Starting service")

                startCaptureService(code, data)
            }

        Button(onClick = {
            val intent = mediaProjectionManager.createScreenCaptureIntent()
            launcher.launch(intent)
        }) {
            Text("Start Screen Capture")
        }
    }

    private fun startCaptureService(resultCode: Int, resultData: Intent) {
        val serviceIntent = Intent(this, ScreenCaptureService::class.java).apply {
            action = ScreenCaptureService.ACTION_START
            putExtra(ScreenCaptureService.EXTRA_RESULT_CODE, resultCode)
            putExtra(ScreenCaptureService.EXTRA_DATA, resultData)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent)
        } else {
            startService(serviceIntent)
        }
    }
}