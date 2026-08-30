package com.lifeos.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

val Inter = FontFamily(
    Font(com.lifeos.ui.R.font.inter_regular, FontWeight.Normal),
    Font(com.lifeos.ui.R.font.inter_medium, FontWeight.Medium),
    Font(com.lifeos.ui.R.font.inter_semibold, FontWeight.SemiBold),
    Font(com.lifeos.ui.R.font.inter_bold, FontWeight.Bold),
)

private fun inter(
    size: Int,
    line: Int,
    weight: FontWeight,
    tracking: Float = 0f,
    tnum: Boolean = false,
): TextStyle = TextStyle(
    fontFamily = Inter,
    fontWeight = weight,
    fontSize = size.sp,
    lineHeight = line.sp,
    letterSpacing = tracking.sp,
    fontFeatureSettings = if (tnum) "tnum" else null,
)

val TimeNumeric = TextStyle(
    fontFamily = Inter,
    fontWeight = FontWeight.Medium,
    fontSize = 13.sp,
    lineHeight = 16.sp,
    fontFeatureSettings = "tnum",
)

val LifeOsTypography = Typography(
    displayLarge = inter(36, 44, FontWeight.Bold, tracking = -0.5f, tnum = true),
    displayMedium = inter(30, 36, FontWeight.Bold, tracking = -0.5f, tnum = true),
    displaySmall = inter(26, 32, FontWeight.Bold, tracking = -0.4f, tnum = true),
    headlineLarge = inter(28, 34, FontWeight.SemiBold, tracking = -0.3f),
    headlineMedium = inter(24, 30, FontWeight.SemiBold, tracking = -0.3f),
    headlineSmall = inter(20, 26, FontWeight.SemiBold),
    titleLarge = inter(20, 26, FontWeight.SemiBold),
    titleMedium = inter(17, 22, FontWeight.SemiBold),
    titleSmall = inter(15, 20, FontWeight.SemiBold),
    bodyLarge = inter(16, 24, FontWeight.Normal),
    bodyMedium = inter(14, 20, FontWeight.Normal),
    bodySmall = inter(13, 18, FontWeight.Normal),
    labelLarge = inter(14, 18, FontWeight.Medium),
    labelMedium = inter(12, 16, FontWeight.Medium),
    labelSmall = inter(11, 14, FontWeight.Medium, tracking = 1.0f),
)

object LifeOsFonts {
    val body = Inter
}
