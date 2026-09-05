package com.example.netshadow.ui.theme

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

private val DarkColorScheme = darkColorScheme(
    primary = NeonGreen,
    onPrimary = OnPrimary,
    background = Black,
    onBackground = OnBackground,
    surface = SurfaceCard,
    onSurface = OnSurface,
    surfaceVariant = SurfaceDim,
    outline = BorderColor,
    error = ErrorLight
)

@Composable
fun NetShadowTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = DarkColorScheme,
        typography = Typography,
        shapes = Shapes,
        content = content
    )
}

/**
 * Reusable glow helper for Phase 3 (active connections) and Phase 5 (critical alerts).
 */
fun Modifier.glowBorder(
    color: Color,
    glowRadius: Dp = 8.dp
) = this.drawBehind {
    val paint = Paint().asFrameworkPaint().apply {
        setShadowLayer(glowRadius.toPx(), 0f, 0f, color.copy(alpha = 0.3f).toArgb())
    }
    drawIntoCanvas { canvas ->
        canvas.nativeCanvas.drawRoundRect(
            0f, 0f, size.width, size.height,
            8.dp.toPx(), 8.dp.toPx(), paint
        )
    }
}

@Preview(showBackground = true)
@Composable
fun ThemeBaselinePreview() {
    NetShadowTheme {
        Scaffold(
            containerColor = Black // True-black viewport
        ) { padding ->
            Card(
                modifier = Modifier
                    .padding(padding)
                    .padding(16.dp)
                    .fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = SurfaceCard // #121212 card
                ),
                border = BorderStroke(1.dp, BorderColor),
                shape = Shapes.medium
            ) {
                Column(
                    modifier = Modifier.padding(16.dp)
                ) {
                    Text(
                        "NETSHADOW BASELINE",
                        style = MaterialTheme.typography.labelLarge
                    )
                    Text(
                        "192.168.1.1",
                        style = MaterialTheme.typography.labelSmall, // Roboto Mono
                        color = NeonGreen
                    )
                }
            }
        }
    }
}
