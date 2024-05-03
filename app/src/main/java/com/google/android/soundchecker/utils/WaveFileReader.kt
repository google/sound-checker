/**
 * Copyright 2024 Google LLC
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

import android.media.AudioFormat
import android.util.Log
import com.google.android.soundchecker.AudioEncoderDecoderActivity
import java.io.InputStream
import java.util.Arrays
import kotlin.math.min

typealias RiffID = UInt
typealias RiffInt32 = Int
typealias RiffInt16 = Short

fun makeRiffID(a: Char, b: Char, c: Char, d: Char): RiffID {
    return makeRiffID(a.toByte(), b.toByte(), c.toByte(), d.toByte())
}

fun makeRiffID(a: Byte, b: Byte, c: Byte, d: Byte): RiffID {
    return (d.toUInt() shl 24) or
            (c.toUInt() shl 16) or
            (b.toUInt() shl 8) or
            a.toUInt()
}

fun makeRiffInt32(a: Byte, b: Byte, c: Byte, d: Byte): RiffInt32 {
    return ((d.toInt() and 0xff) shl 24) or
            ((c.toInt() and 0xff) shl 16) or
            ((b.toInt() and 0xff) shl 8) or
            (a.toInt() and 0xff)
}

fun makeRiffInt16(a: Byte, b: Byte): RiffInt16 {
    return (((b.toInt() and 0xff) shl 8) or (a.toInt() and 0xff)).toShort()
}

fun getRiffID(stream: InputStream): RiffID {
    return makeRiffID(stream.read().toByte(),
        stream.read().toByte(),
        stream.read().toByte(),
        stream.read().toByte())
}

fun getRiffInt32(stream: InputStream): RiffInt32 {
    return makeRiffInt32(stream.read().toByte(),
        stream.read().toByte(),
        stream.read().toByte(),
        stream.read().toByte())
}

fun getRiffInt16(stream: InputStream): RiffInt16 {
    return makeRiffInt16(stream.read().toByte(),
        stream.read().toByte())
}

class WaveFileReader(val stream: InputStream) {
    open class WaveChunkHeader(var chunkId : RiffID) {
        companion object {
            val RIFFID_DATA = makeRiffID('d', 'a', 't', 'a')
        }

        var chunkSize: RiffInt32 = 0

        open fun read(stream: InputStream) {
            chunkSize = getRiffInt32(stream)
        }
    }

    class WaveFmtChunkHeader(chunkId: RiffID) : WaveChunkHeader(chunkId) {
        companion object {
            val RIFFID_FMT = makeRiffID('f', 'm', 't', ' ')
            const val ENCODING_PCM: Short = 1
            const val ENCODING_ADPCM: Short = 2 // Microsoft ADPCM Format
            const val ENCODING_IEEE_FLOAT: Short = 3 // Samples from -1.0 -> 1.0
        }

        var encodingId: RiffInt16 = ENCODING_PCM
        var numChannels: RiffInt16 = 0
        var sampleRate: RiffInt32 = 0
        var aveBytesPerSecond: RiffInt32 = 0
        var blockAlign: RiffInt16 = 0
        var sampleSize: RiffInt16 = 0
        var extraBytes: RiffInt16 = 0

        fun normalize() {
            // Only handle this for PCM and FLOAT
            if (encodingId == ENCODING_PCM || encodingId == ENCODING_IEEE_FLOAT) {
                blockAlign = (numChannels * (sampleSize / 8)).toShort()
                aveBytesPerSecond = sampleRate * blockAlign
                extraBytes = 0
            }
        }

        override fun read(stream: InputStream) {
            super.read(stream)
            encodingId = getRiffInt16(stream)
            numChannels = getRiffInt16(stream)
            sampleRate = getRiffInt32(stream)
            aveBytesPerSecond = getRiffInt32(stream)
            blockAlign = getRiffInt16(stream)
            sampleSize = getRiffInt16(stream)

            if (encodingId != ENCODING_PCM && encodingId != ENCODING_IEEE_FLOAT) {
                extraBytes = getRiffInt16(stream)
            } else {
                extraBytes = (chunkSize - 16).toShort()
            }
        }
    }

    class WaveRiffChunkHeader(chunkId: RiffID) : WaveChunkHeader(chunkId) {
        companion object {
            val RIFFID_RIFF = makeRiffID('R', 'I', 'F', 'F')
            val RIFFID_WAVE = makeRiffID('W', 'A', 'V', 'E')
        }

        var formatId: RiffID = RIFFID_WAVE

        override fun read(stream: InputStream) {
            super.read(stream)
            formatId = getRiffID(stream)
        }
    }

    companion object {
        private const val TAG = "WaveFileReader"
        private const val CONVERSION_BUFFER_FRAMES = 16
        private const val ERR_INVALID_FORMAT = -1
        private const val ERR_INVALID_STATE = -2
    }

    var wavChunk: WaveRiffChunkHeader? = null
    var fmtChunk: WaveFmtChunkHeader? = null
    var dataChunk: WaveChunkHeader? = null

    fun getSampleRate(): Int {
        return fmtChunk?.sampleRate?.toInt() ?: 0
    }

    fun getNumChannels(): Int {
        return fmtChunk?.numChannels?.toInt() ?: 0
    }

    fun getBitsPerSample(): Int {
        return fmtChunk?.sampleSize?.toInt() ?: 0
    }

    fun getNumSampleFrames(): Int {
        // If the file was streaming, the data chunk size is INT_MAX.
        // Use stream.available() instead.
        if (dataChunk?.chunkSize?.toInt() == Int.MAX_VALUE) {
            return stream.available() / (getBitsPerSample() / 8) / getNumChannels()
        }
        return (dataChunk?.chunkSize?.toInt() ?: 0) / (getBitsPerSample() / 8) / getNumChannels()
    }

    fun getSampleEncoding(): Int {
        if (fmtChunk?.encodingId == WaveFmtChunkHeader.ENCODING_PCM) {
            when (fmtChunk?.sampleSize?.toInt()) {
                8 -> return AudioFormat.ENCODING_PCM_8BIT
                16 -> return AudioFormat.ENCODING_PCM_16BIT
                24 -> return AudioFormat.ENCODING_PCM_24BIT_PACKED
                32 -> return AudioFormat.ENCODING_PCM_32BIT
                else -> return AudioFormat.ENCODING_INVALID
            }
        } else if (fmtChunk?.encodingId == WaveFmtChunkHeader.ENCODING_IEEE_FLOAT) {
            return AudioFormat.ENCODING_PCM_FLOAT
        }
        return AudioFormat.ENCODING_INVALID
    }

    fun parse() {
        while (true) {
            if (stream.available() < RiffID.SIZE_BYTES) {
                break
            }
            val tag = getRiffID(stream)

            if (tag == WaveRiffChunkHeader.RIFFID_RIFF) {
                wavChunk = WaveRiffChunkHeader(tag)
                wavChunk!!.read(stream)
            } else if (tag == WaveFmtChunkHeader.RIFFID_FMT) {
                fmtChunk = WaveFmtChunkHeader(tag)
                fmtChunk!!.read(stream)
            } else if (tag == WaveChunkHeader.RIFFID_DATA) {
                dataChunk = WaveChunkHeader(tag)
                dataChunk!!.read(stream)
                Log.d(TAG, "chunkSize: " + dataChunk!!.chunkSize.toLong() +
                        " stream.available(): " + stream.available())
                // We are now positioned at the start of the audio data.
                // stream.skip(dataChunk!!.chunkSize.toLong())
                break
            } else { // Unknown
                var chunk = WaveChunkHeader(tag)
                chunk!!.read(stream)
                stream.skip(chunk!!.chunkSize.toLong()) // skip the body
            }
        }
    }

    fun getDataFloat(buf: FloatArray, numFrames: Int) : Int {
        if (fmtChunk == null || dataChunk == null) {
            return ERR_INVALID_STATE
        }

        val numChannels = fmtChunk!!.numChannels.toInt()
        val sampleSize = getBitsPerSample() / 8

        if (fmtChunk?.encodingId == WaveFmtChunkHeader.ENCODING_IEEE_FLOAT) {
            if (sampleSize != Float.SIZE_BYTES) {
                return ERR_INVALID_FORMAT
            }
        } else if (fmtChunk?.encodingId == WaveFmtChunkHeader.ENCODING_PCM) {
            if (sampleSize > Int.SIZE_BYTES || sampleSize < Byte.SIZE_BYTES) {
                return ERR_INVALID_FORMAT
            }
        } else {
            return ERR_INVALID_FORMAT
        }

        var bufOffset = 0
        var sampleFullScale = 0x80000000.toFloat()
        if (sampleSize == 1) {
            sampleFullScale = 0x80.toFloat()
        }
        val inverseScale = 1.0f / sampleFullScale

        val readBuf = ByteArray(CONVERSION_BUFFER_FRAMES * numChannels * sampleSize)
        var framesLeft = numFrames
        while (framesLeft > 0) {
            val framesThisRead = min(framesLeft, CONVERSION_BUFFER_FRAMES)
            val numFramesRead = stream.read(readBuf, 0, framesThisRead * numChannels *
                    sampleSize) / numChannels / sampleSize
            //Log.d(TAG, "numFramesRead: " + numFramesRead)

            // Convert & Scale
            for (offset in 0 until numFramesRead * numChannels) {
                // PCM8 is unsigned while everything else is signed
                if (sampleSize == 1) {
                    buf[bufOffset++] = (readBuf[offset].toUByte().toFloat() - sampleFullScale) *
                            inverseScale
                } else {
                    var sample = 0
                    for (bit in 0 until sampleSize) {
                        val cur = (readBuf[offset * sampleSize + bit].toInt() and
                                0xff) shl ((bit + 4 - sampleSize) * 8)
                        if (bit == 0) {
                            sample = cur
                        } else {
                            sample = sample or cur
                        }
                    }
                    // No need to scale for FLOAT
                    if (fmtChunk?.encodingId == WaveFmtChunkHeader.ENCODING_IEEE_FLOAT) {
                        buf[bufOffset++] = Float.fromBits(sample)
                    }
                    else {
                        buf[bufOffset++] = sample * inverseScale
                    }
                }
            }

            framesLeft -= numFramesRead

            if (numFramesRead < framesThisRead) {
                break // none left
            }
        }

        val totalFramesRead = numFrames - framesLeft
        if (framesLeft > 0) {
            Arrays.fill(buf, totalFramesRead * numChannels, numFrames * numChannels, 0F)
        }

        //Log.d(TAG, "stream.available(): " + stream.available())
        //Log.d(TAG, "totalFramesRead: " + totalFramesRead)

        return totalFramesRead
    }
}