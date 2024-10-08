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
import kotlin.math.ln

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

fun byteArrayToFloatArray(byteArray: ByteArray, floatArray: FloatArray) {
    for (i in floatArray.indices) {
        val bi = i * 4
        floatArray[i] = Float.fromBits(
            (byteArray[bi + 3].toInt() and 0xff shl 24) or
                    (byteArray[bi + 2].toInt() and 0xff shl 16) or
                    (byteArray[bi + 1].toInt() and 0xff shl 8) or
                    (byteArray[bi].toInt() and 0xff)
        )
    }
}

fun i16ByteArrayToFloatArray(byteArray: ByteArray, floatArray: FloatArray) {
    for (i in floatArray.indices) {
        val bi = i * 2
        floatArray[i] = ((byteArray[bi + 1].toInt() and 0xff shl 8) or
                (byteArray[bi].toInt() and 0xff)).toShort().toFloat() * (1.0f / 32768)
    }
}

fun floatArrayToI16ByteArray(floatArray: FloatArray, byteArray: ByteArray) {
    for (i in floatArray.indices) {
        val bi = i * 2
        val shortVal = (floatArray[i] * 32768.0f / 1.0f).toInt()
        byteArray[bi] = (shortVal and 0xff).toByte()
        byteArray[bi + 1] = (shortVal shr 8 and 0xff).toByte()
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

// Remap a linear FloatArray to log scale

// [1.0, 2.0, 3.0, 4.0, 5.0, 6.0, 7.0, 8.0, 9.0, 10.0], 10 becomes
// [1.0, 1.0, 1.0, 2.0, 3.0, 3.0, 4.5, 6.0, 7.0, 9.0]
fun remapToLog(input: FloatArray, outputSize: Int): FloatArray {
    val inputSize = input.size
    val output = FloatArray(outputSize)
    var inputIndex = 0
    var nextInputVal = ln(inputIndex + 2.0f)
    val linearEnd = ln(inputSize.toFloat())
    val linearIncrement = linearEnd / outputSize
    for (outputIndex in 0 until outputSize) {
        val outputVal = (outputIndex + 1) * linearIncrement
        var sum = 0.0f
        var count = 0
        var isFirstIndex = true
        while (nextInputVal < outputVal + .0001f) {
            // Skip the first index if it's smaller as the previous case already counted for it.
            if (!isFirstIndex) {
                sum += input[inputIndex]
                count++
            } else {
                isFirstIndex = false
            }
            inputIndex++
            nextInputVal = ln(inputIndex + 2.0f)
        }
        sum += input[inputIndex]
        count++
        // Add in the few remaining values
        if (outputIndex == outputSize - 1) {
            while (inputIndex < inputSize) {
                sum += input[inputIndex]
                count++
                inputIndex++
            }


        }
        output[outputIndex] = sum / count
    }
    return output
}

fun reverseByte(value: Byte): Byte {
    var value = value
    var b: Byte = 0x0
    for (i in 0..7) {
        b = (b.toInt() shl 1).toByte()
        b = (b.toInt() or (value.toInt() and 0x1)).toByte()
        value = (value.toInt() shr 1).toByte()
    }
    return b
}
