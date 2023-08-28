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

import android.media.AudioDeviceInfo
import android.media.AudioManager

import java.util.Vector

import com.google.android.soundchecker.utils.Helpers


/**
 * POJO which represents basic information for an audio device.
 *
 * Example: id: 8, deviceName: "built-in speaker"
 */
class AudioDeviceListEntry(val id: Int, val name: String) {
    var deviceInfo: AudioDeviceInfo? = null
        private set

    constructor(info: AudioDeviceInfo) : this(
            info.id,
            info.productName.toString() + " " + Helpers.typeToString(info.type) + " " + info.address
    ) {
        deviceInfo = info
    }

    override fun toString(): String {
        return name
    }

    override fun equals(o: Any?): Boolean {
        if (this === o) return true
        if (o == null || javaClass != o.javaClass) return false
        val that = o as AudioDeviceListEntry
        if (id != that.id) return false
        return name == that.name
    }

    override fun hashCode(): Int {
        var result = id
        result = 31 * result + name.hashCode()
        return result
    }

    companion object {
        /**
         * Create a list of AudioDeviceListEntry objects from a list of AudioDeviceInfo objects.
         *
         * @param devices A list of {@Link AudioDeviceInfo} objects
         * @param directionType Only audio devices with this direction will be included in the list.
         * Valid values are GET_DEVICES_ALL, GET_DEVICES_OUTPUTS and
         * GET_DEVICES_INPUTS.
         * @return A list of AudioDeviceListEntry objects
         */
        fun createListFrom(
                devices: Array<AudioDeviceInfo>,
                directionType: Int,
        ): List<AudioDeviceListEntry> {
            val listEntries: MutableList<AudioDeviceListEntry> = Vector()
            for (info in devices) {
                if (directionType == AudioManager.GET_DEVICES_ALL ||
                        (directionType == AudioManager.GET_DEVICES_OUTPUTS && info.isSink) ||
                        (directionType == AudioManager.GET_DEVICES_INPUTS && info.isSource)) {
                    listEntries.add(AudioDeviceListEntry(info))
                }
            }
            return listEntries
        }
    }
}
