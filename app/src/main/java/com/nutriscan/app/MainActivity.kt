package com.nutriscan.app

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import com.nutriscan.app.ui.NutriScanApp
import com.nutriscan.app.ui.theme.NutriScanTheme

/**
 * AppCompatActivity (not ComponentActivity) so AppCompatDelegate can apply per-app locales
 * on Android 12 and below as well as Android 13+.
 */
class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            NutriScanTheme {
                NutriScanApp()
            }
        }
    }
}
