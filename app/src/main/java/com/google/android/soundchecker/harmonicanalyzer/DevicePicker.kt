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

package com.google.android.soundchecker.harmonicanalyzer

import android.media.AudioManager
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

import kotlinx.coroutines.flow.MutableStateFlow

import com.google.android.soundchecker.utils.Helpers
import com.google.android.soundchecker.utils.ui.AudioDeviceListEntry
import com.google.android.soundchecker.utils.ui.AudioDeviceSpinner

@Composable
fun DevicePicker(type: Int,
                 devices: MutableStateFlow<ArrayList<AudioDeviceListEntry>>,
                 onItemSelected: (AudioDeviceListEntry) -> Unit,
                 onChannelIndexChanged: (channelIndex: Int) -> Unit) {
    val deviceDirectionPrompt = getDeviceDirectionPrompt(type)
    var deviceText by remember { mutableStateOf("") }

    val channelIndexes = listOf(0, 1, 2, 3)
    var selectedChannelIndex by remember { mutableStateOf(-1) }
    val channelIndexesEnabled = mutableListOf(true, true, true, true)

    fun updateDeviceText(entry: AudioDeviceListEntry) {
        deviceText = if (entry.deviceInfo == null) "none" else Helpers.shortDump(entry.deviceInfo!!)
    }

    Column {
        Row {
            Text(text = "$deviceDirectionPrompt device:",
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Bold)
            AudioDeviceSpinner(items = devices,
                    onItemSelected = { entry ->
                        updateDeviceText(entry)
                        onItemSelected(entry)
                        val maxChannelCount = if (entry.deviceInfo == null) 1
                        else Helpers.findHighestChannelCountFor(entry.deviceInfo!!)
                        for (i in 0 until maxChannelCount) {
                            channelIndexesEnabled[i] = true
                        }
                        for (i in maxChannelCount..3) {
                            channelIndexesEnabled[i] = false
                        }
                    })
        }
        Spacer(modifier = Modifier.padding(4.dp))
        Text(text = deviceText)
        Spacer(modifier = Modifier.padding(4.dp))
        Row {
            Text(text = "channel:",
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.align(Alignment.CenterVertically))
            channelIndexes.forEach { channelIndex ->
                RadioButton(
                        selected = selectedChannelIndex == channelIndex,
                        onClick = {
                            onChannelIndexChanged(channelIndex)
                            selectedChannelIndex = channelIndex
                        },
                        enabled = channelIndexesEnabled[channelIndex],
                        colors = RadioButtonDefaults.colors(
                                selectedColor = MaterialTheme.colorScheme.primary,
                                unselectedColor = MaterialTheme.colorScheme.onSurface,
                        )
                )
                Text(text = channelIndex.toString(),
                        modifier = Modifier.align(Alignment.CenterVertically))
            }
        }
    }
}

fun getDeviceDirectionPrompt(type: Int): String {
    return when (type) {
        AudioManager.GET_DEVICES_INPUTS -> "Input"
        AudioManager.GET_DEVICES_OUTPUTS -> "Output"
        else -> ""
    }
}