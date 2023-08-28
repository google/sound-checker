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

package com.google.android.soundchecker.utils

import android.media.AudioDeviceInfo
import android.media.AudioFormat

class Helpers {
    companion object {
        fun bytesPerSample(encoding: Int): Int {
            return when (encoding) {
                AudioFormat.ENCODING_PCM_8BIT -> 1
                AudioFormat.ENCODING_PCM_16BIT -> 2
                AudioFormat.ENCODING_PCM_24BIT_PACKED -> 3
                AudioFormat.ENCODING_PCM_32BIT -> 4
                AudioFormat.ENCODING_PCM_FLOAT -> 4
                else -> 0
            }
        }

        fun getChannelIndexMask(channelCount: Int): Int {
            return (1 shl channelCount) - 1
        }

        fun getChannelCountFromMask(channelMask: Int): Int {
            return Integer.bitCount(channelMask)
        }

        fun floatArrayToByteArray(floatArray: FloatArray, byteArray: ByteArray) {
            for (i in floatArray.indices) {
                val bi = i * 4
                val bits = floatArray[i].toBits()
                byteArray[bi] = (bits and 0xff).toByte()
                byteArray[bi + 1] = (bits shr 8 and 0xff).toByte()
                byteArray[bi + 2] = (bits shr 16 and 0xff).toByte()
                byteArray[bi + 3] = (bits shr 24 and 0xff).toByte()
            }
        }

        fun findHighestChannelCountFor(device: AudioDeviceInfo): Int {
            return if (device.channelCounts.isEmpty()) 3
            else device.channelCounts.max()
        }

        fun shortDump(adi: AudioDeviceInfo): String {
            val sb = StringBuilder()
            sb.append("Id: ")
            sb.append(adi.id)
            sb.append("\nProduct name: ")
            sb.append(adi.productName)
            sb.append("\nType: ")
            sb.append(typeToString(adi.type))
            sb.append("\nChannel counts: ")
            val channelCounts = adi.channelCounts
            sb.append(intArrayToString(channelCounts))
            sb.append("\nChannel masks: ")
            val channelMasks = adi.channelMasks
            sb.append(intArrayToString(channelMasks, "0x%x"))
            sb.append("\nChannel index masks: ")
            val channelIndexMasks = adi.channelIndexMasks
            sb.append(intArrayToString(channelIndexMasks, "0x%x"))
            return sb.toString()
        }

        /**
         * Converts an [AudioDeviceInfo] object into a human readable representation
         *
         * @param adi The AudioDeviceInfo object to be converted to a String
         * @return String containing all the information from the AudioDeviceInfo object
         */
        fun toString(adi: AudioDeviceInfo): String? {
            val sb = StringBuilder()
            sb.append(shortDump(adi))
            sb.append("\nIs source: ")
            sb.append(if (adi.isSource) "Yes" else "No")
            sb.append("\nIs sink: ")
            sb.append(if (adi.isSink) "Yes" else "No")
            sb.append("\nEncodings: ")
            val encodings = adi.encodings
            sb.append(intArrayToString(encodings))
            sb.append("\nSample Rates: ")
            val sampleRates = adi.sampleRates
            sb.append(intArrayToString(sampleRates))
            return sb.toString()
        }

        /**
         * Converts the value from [AudioDeviceInfo.getType] into a human
         * readable string
         * @param type One of the [AudioDeviceInfo].TYPE_* values
         * e.g. AudioDeviceInfo.TYPE_BUILT_IN_SPEAKER
         * @return string which describes the type of audio device
         */
        fun typeToString(type: Int): String? {
            return when (type) {
                AudioDeviceInfo.TYPE_AUX_LINE -> "auxiliary line-level connectors"
                AudioDeviceInfo.TYPE_BLUETOOTH_A2DP -> "Bluetooth device supporting the A2DP profile"
                AudioDeviceInfo.TYPE_BLUETOOTH_SCO -> "Bluetooth device typically used for telephony"
                AudioDeviceInfo.TYPE_BUILTIN_EARPIECE -> "built-in earphone speaker"
                AudioDeviceInfo.TYPE_BUILTIN_MIC -> "built-in microphone"
                AudioDeviceInfo.TYPE_BUILTIN_SPEAKER -> "built-in speaker"
                AudioDeviceInfo.TYPE_BUS -> "BUS"
                AudioDeviceInfo.TYPE_DOCK -> "DOCK"
                AudioDeviceInfo.TYPE_FM -> "FM"
                AudioDeviceInfo.TYPE_FM_TUNER -> "FM tuner"
                AudioDeviceInfo.TYPE_HDMI -> "HDMI"
                AudioDeviceInfo.TYPE_HDMI_ARC -> "HDMI audio return channel"
                AudioDeviceInfo.TYPE_IP -> "IP"
                AudioDeviceInfo.TYPE_LINE_ANALOG -> "line analog"
                AudioDeviceInfo.TYPE_LINE_DIGITAL -> "line digital"
                AudioDeviceInfo.TYPE_TELEPHONY -> "telephony"
                AudioDeviceInfo.TYPE_TV_TUNER -> "TV tuner"
                AudioDeviceInfo.TYPE_USB_ACCESSORY -> "USB accessory"
                AudioDeviceInfo.TYPE_USB_DEVICE -> "USB device"
                AudioDeviceInfo.TYPE_USB_HEADSET -> "USB headset"
                AudioDeviceInfo.TYPE_WIRED_HEADPHONES -> "wired headphones"
                AudioDeviceInfo.TYPE_WIRED_HEADSET -> "wired headset"
                AudioDeviceInfo.TYPE_UNKNOWN -> "unknown"
                else -> "unknown"
            }
        }

        /**
         * Converts an integer array into a string where each int is separated by a space
         *
         * @param integerArray the integer array to convert to a string
         * @return string containing all the integer values separated by spaces
         */
        private fun intArrayToString(integerArray: IntArray): String? {
            return intArrayToString(integerArray, "%d")
        }

        private fun intArrayToString(integerArray: IntArray, format: String): String? {
            val sb = StringBuilder()
            for (i in integerArray.indices) {
                sb.append(String.format(format, integerArray[i]))
                if (i != integerArray.size - 1) sb.append(" ")
            }
            return sb.toString()
        }
    }
}