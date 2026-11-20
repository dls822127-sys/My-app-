package com.example

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.game.GameMode
import com.example.game.GameViewModel
import com.example.ui.AnantDriftScreen
import com.example.ui.theme.AnantDriftTheme

class MainActivity : ComponentActivity() {

    private val gameViewModel: GameViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
        )

        setContent {
            AnantDriftTheme {
                val mode by gameViewModel.gameMode.collectAsStateWithLifecycle()

                // Keep screen awake during active non-stop gameplay until paused or closed
                DisposableEffect(mode) {
                    if (mode == GameMode.PLAYING) {
                        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                    } else {
                        window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                    }
                    onDispose {
                        window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                    }
                }

                AnantDriftScreen(viewModel = gameViewModel)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        gameViewModel.onAppResumed()
    }

    override fun onPause() {
        super.onPause()
        gameViewModel.onAppPaused()
    }
}
