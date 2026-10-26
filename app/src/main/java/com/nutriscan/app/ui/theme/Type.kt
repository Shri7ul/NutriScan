package com.nutriscan.app.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

private val Default = Typography()

val NutriScanTypography = Typography(
    headlineSmall = Default.headlineSmall.copy(
        fontWeight = FontWeight.SemiBold,
        letterSpacing = (-0.2).sp
    ),
    titleLarge = Default.titleLarge.copy(fontWeight = FontWeight.SemiBold),
    titleMedium = Default.titleMedium.copy(fontWeight = FontWeight.SemiBold),
    labelLarge = Default.labelLarge.copy(fontWeight = FontWeight.SemiBold),
    bodyMedium = Default.bodyMedium.copy(lineHeight = 20.sp),
    bodySmall = Default.bodySmall.copy(lineHeight = 17.sp),
    // Barcodes and nutrition numbers read better in a monospaced face.
    bodyLarge = Default.bodyLarge.copy(fontFamily = FontFamily.Default, lineHeight = 24.sp),
    labelSmall = Default.labelSmall.copy(letterSpacing = 0.4.sp)
)

val MonospaceStyle = TextStyle(
    fontFamily = FontFamily.Monospace,
    fontSize = 15.sp,
    letterSpacing = 1.sp
)
