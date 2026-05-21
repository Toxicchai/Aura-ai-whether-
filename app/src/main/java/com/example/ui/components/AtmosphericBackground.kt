package com.example.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import com.example.data.model.WeatherThemeConfig

@Composable
fun AtmosphericBackground(config: WeatherThemeConfig) {
    val startColor = Color(config.backgroundStart)
    val endColor = Color(config.backgroundEnd)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(startColor, endColor)
                )
            )
    ) {
        if (config.isNight) {
            NightStarsCanvas()
        } else {
            DaySunAuraCanvas(accentColor = Color(config.accentColor))
        }
    }
}

@Composable
fun NightStarsCanvas() {
    val infiniteTransition = rememberInfiniteTransition(label = "stars")
    val twinkleAlpha by infiniteTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(2200, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "twinkle"
    )

    // Predefined coordinates for small scattered stars to ensure performance
    val stars = remember {
        listOf(
            Offset(0.15f, 0.12f), Offset(0.28f, 0.08f), Offset(0.42f, 0.18f),
            Offset(0.68f, 0.10f), Offset(0.85f, 0.15f), Offset(0.09f, 0.28f),
            Offset(0.35f, 0.32f), Offset(0.55f, 0.22f), Offset(0.78f, 0.28f),
            Offset(0.92f, 0.38f), Offset(0.20f, 0.45f), Offset(0.48f, 0.48f),
            Offset(0.62f, 0.40f), Offset(0.80f, 0.52f), Offset(0.12f, 0.65f),
            Offset(0.38f, 0.68f), Offset(0.65f, 0.60f), Offset(0.88f, 0.62f),
            Offset(0.25f, 0.82f), Offset(0.52f, 0.78f), Offset(0.72f, 0.85f)
        )
    }

    Canvas(modifier = Modifier.fillMaxSize()) {
        val width = size.width
        val height = size.height

        stars.forEach { percentOffset ->
            val sizeRadius = if (percentOffset.x * 100 % 3 == 0f) 5f else 3f
            drawCircle(
                color = Color.White.copy(alpha = twinkleAlpha * (0.5f + (percentOffset.y / 2f))),
                radius = sizeRadius,
                center = Offset(percentOffset.x * width, percentOffset.y * height)
            )
        }
    }
}

@Composable
fun DaySunAuraCanvas(accentColor: Color) {
    val infiniteTransition = rememberInfiniteTransition(label = "auras")
    val scaleShift by infiniteTransition.animateFloat(
        initialValue = 0.92f,
        targetValue = 1.05f,
        animationSpec = infiniteRepeatable(
            animation = tween(4000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse"
    )

    Canvas(modifier = Modifier.fillMaxSize()) {
        val width = size.width
        val height = size.height

        // Glowing sun orb in the top right corner
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    accentColor.copy(alpha = 0.35f),
                    accentColor.copy(alpha = 0.12f),
                    Color.Transparent
                ),
                center = Offset(width * 0.88f, height * 0.12f),
                radius = width * 0.8f * scaleShift
            ),
            center = Offset(width * 0.88f, height * 0.12f),
            radius = width * 0.8f * scaleShift
        )

        // Subtle ambient lower aura
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    Color.White.copy(alpha = 0.08f),
                    Color.Transparent
                ),
                center = Offset(width * 0.2f, height * 0.88f),
                radius = width * 1.2f
            ),
            center = Offset(width * 0.2f, height * 0.88f),
            radius = width * 1.2f
        )
    }
}
