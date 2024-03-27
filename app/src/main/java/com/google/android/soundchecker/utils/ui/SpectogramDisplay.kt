/**
 * Copyright 2023 Google LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.android.soundchecker.utils.ui

import android.graphics.Color.rgb
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment.Companion.Center
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import kotlin.math.max
import kotlin.math.min

@Composable
fun SpectogramDisplay(
    modifier : Modifier,
    values: MutableList<FloatArray?>?,
    min: Float = 0.0F,
    max: Float = 1.0F
) {
    if (values.isNullOrEmpty()) {
        return;
    }

    Box(
        modifier = modifier,
        contentAlignment = Center
    ) {
        Canvas(
            modifier = Modifier.fillMaxSize(),
        ) {
            val numXValues = values.size
            val numYValues = values[0]!!.size

            val xScale = size.width / numXValues
            val yScale = size.height / numYValues

            for (xIndex in 0 until numXValues) {
                for (yIndex in 0 until numYValues) {
                    var normalizedColor = values[xIndex]!![yIndex]
                    normalizedColor = max(min(normalizedColor, max), min)
                    normalizedColor = (normalizedColor - min) / (max - min)
                    val color = Color(rgb(normalizedColor, 0.0f, 0.0f))
                    val xStart = xIndex * xScale
                    val xEnd = (xIndex + 1) * xScale + 1
                    val yStart = yIndex * yScale
                    val yEnd = (yIndex + 1) * yScale + 1

                    drawRect(
                        color = color,
                        topLeft = Offset(xStart, yStart),
                        size = Size(xEnd - xStart, yEnd - yStart)
                    )
                }
            }
        }
    }
}
