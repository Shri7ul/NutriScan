package com.nutriscan.app.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import com.nutriscan.app.AppContainer
import com.nutriscan.app.NutriScanApplication

@Composable
fun rememberAppContainer(): AppContainer {
    val context = LocalContext.current
    return remember(context) {
        (context.applicationContext as NutriScanApplication).container
    }
}
