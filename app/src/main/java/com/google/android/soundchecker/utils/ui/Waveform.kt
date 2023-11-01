package com.google.android.soundchecker.utils.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment.Companion.Center
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.unit.dp

@Composable
fun Waveform(
    modifier : Modifier,
    points: FloatArray?,
) {
    if (points == null) {
        return;
    }
    Box(
        modifier = modifier
            .background(Color.White)
            .padding(horizontal = 8.dp, vertical = 12.dp),
        contentAlignment = Center
    ) {
        Canvas(
            modifier = Modifier.fillMaxSize(),
        ) {
            val offsetY = 0.5f * size.height;
            val scaleY = 0.0f - offsetY;

            val xScale = size.width / (points.size - 1)
            var x0 = 0.0f
            if (xScale < 1.0) {
                // Draw a vertical bar for multiple samples.
                var ymin = offsetY
                var ymax: Float = offsetY
                for (i in 0 until points.size) {
                    val x1 = i * xScale
                    if (x0.toInt() != x1.toInt()) {
                        // draw old data
                        drawLine(
                            start = Offset(x0, ymin),
                            end = Offset(x0, ymax),
                            color = Color.Black,
                            strokeWidth = 1F)
                    }
                    val y1: Float = points.get(i) * scaleY + offsetY
                    ymin = Math.min(ymin, y1)
                    ymax = Math.max(ymax, y1)
                }
            } else {
                // Draw line between samples.
                var y0: Float = points.get(0) * scaleY + offsetY
                for (i in 1 until points.size) {
                    val x1 = i * xScale
                    val y1: Float = points.get(i) * scaleY + offsetY
                    drawLine(
                        start = Offset(x0, y0),
                        end = Offset(x0, y1),
                        color = Color.Black,
                        strokeWidth = 5F)
                    x0 = x1
                    y0 = y1
                }
            }
        }
    }
}