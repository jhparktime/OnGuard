// app/src/main/java/com/onguard/presentation/theme/Color.kt

package com.onguard.presentation.theme

import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

fun String.toColor() = Color(android.graphics.Color.parseColor(if (this.startsWith("#")) this else "#$this"))

// Primary Brand Colors (Orange to Peach Gradient)
val BGGradientStart = "#EA5029".toColor() // 상단 배경 색상
val BGGradientEnd = "#FFFFFF".toColor()   // 하단 배경 색상
val BrandGradient = Brush.verticalGradient(
    colors = listOf(BGGradientStart, BGGradientEnd)
)

// Risk Level Colors (이미지 디자인과 일치)
val RiskHigh = "#EA5029".toColor()   // 고위험 (빨강)
val RiskMedium = "#FF7A05".toColor() // 중위험 (주황)
val RiskLow =  "#FFA705".toColor()    // 저위험 (노랑)
val RiskSafe =  "#0AAF00".toColor()
val SearhKeyword = "#0AAF00".toColor()     // 키워드 (노랑)
val SearhTime = "#0090FF".toColor()     // 키워드 (노랑)

// Text Colors
val TextWhite = Color.White
val TextPrimary = Color(0xFF212121)
val TextSecondary = Color(0xFF757575)

// Background Colors
val CardBackground = Color.White
val BackgroundLight = Color(0xFFF5F5F5) // 탭바 배경 등