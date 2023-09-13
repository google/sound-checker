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

fun Int.bytesPerSample(): Int = when (this) {
    AudioFormat.ENCODING_PCM_8BIT -> 1
    AudioFormat.ENCODING_PCM_16BIT -> 2
    AudioFormat.ENCODING_PCM_24BIT_PACKED -> 3
    AudioFormat.ENCODING_PCM_32BIT -> 4
    AudioFormat.ENCODING_PCM_FLOAT -> 4
    else -> 0
}

fun Int.generateChannelIndexMask() : Int {
    return (1 shl this) - 1
}

fun Int.getChannelCountFromMask() : Int {
    return Integer.bitCount(this)
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

fun AudioDeviceInfo.highestChannelCount() : Int {
    return if (channelCounts.isEmpty()) 3
    else channelCounts.max()
}

fun AudioDeviceInfo.shortDump(): String {
    return buildString {
        appendLine("Id: $id")
        appendLine("Product name: $productName")
        appendLine("Type: ${typeToString(type)}")
        appendLine("Channel counts: ${channelCounts.toStringFormatted()}")
        appendLine("Channel masks: ${channelMasks.toStringFormatted("0x%x")}")
        appendLine(
            "Channel index masks: ${channelIndexMasks.toStringFormatted("0x%x")}")
    }
}

/**
 * Converts an [AudioDeviceInfo] object into a human readable representation
 *
 * @param adi The AudioDeviceInfo object to be converted to a String
 * @return String containing all the information from the AudioDeviceInfo object
 */
fun AudioDeviceInfo.describe(): String {
    return buildString {
        append(shortDump())
        appendLine("Is source: ${if (isSource) "Yes" else "No"}")
        appendLine("Is sink: ${if (isSink) "Yes" else "No"}")
        appendLine("Encodings: ${encodings.toStringFormatted()}")
        appendLine("Sample Rates: ${sampleRates.toStringFormatted()}")
    }
}

fun AudioDeviceInfo.deviceDisplayName() : String {
    return productName.toString() + " " + typeToString(type) + " " + address
}

fun IntArray.toStringFormatted(format: String = "%d"): String {
    return joinToString { integer -> String.format(format, integer) }
}

/**
 * Converts the value from [AudioDeviceInfo.getType] into a human
 * readable string
 * @param type One of the [AudioDeviceInfo].TYPE_* values
 * e.g. AudioDeviceInfo.TYPE_BUILT_IN_SPEAKER
 * @return string which describes the type of audio device
 */
fun typeToString(type: Int): String {
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
