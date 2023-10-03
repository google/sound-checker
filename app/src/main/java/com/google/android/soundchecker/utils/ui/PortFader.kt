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

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier

import com.google.android.soundchecker.utils.ControlPort

@Composable
fun PortFader(
  controlPort: ControlPort,
  modifier: Modifier = Modifier,
  horizontalArrangement: Arrangement.Horizontal = Arrangement.Start,
  verticalAlignment: Alignment.Vertical = Alignment.Top
) {
  val labelText = remember { mutableStateOf(generateLabelText(controlPort)) }
  Row(modifier = modifier,
      horizontalArrangement = horizontalArrangement,
      verticalAlignment = verticalAlignment) {
    Text(text = labelText.value)
    Slider(value = convertPortToFaderValue(controlPort),
           onValueChange = {
             updateControlPortValue(controlPort, it)
             labelText.value = generateLabelText(controlPort)
           })
  }
}

fun updateControlPortValue(controlPort: ControlPort, progress: Float) {
  val value = controlPort.mMinimum + progress * controlPort.range()
  controlPort.set(value)
}

fun convertPortToFaderValue(controlPort: ControlPort) : Float {
  return (controlPort.get() - controlPort.mMinimum) / controlPort.range()
}

fun generateLabelText(controlPort: ControlPort) : String {
  return String.format("%8s\n%8.4f", controlPort.mName, controlPort.get())
}
