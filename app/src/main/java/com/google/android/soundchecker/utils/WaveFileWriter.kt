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

import java.io.OutputStream

class WaveFileWriter(val outputStream: OutputStream, val frameRate: Int, val samplesPerFrame: Int,
                     val bitsPerSample: Int) {
    private var mBytesWritten = 0
    private var mHeaderWritten = false

    fun write(value: Float) {
        if (!mHeaderWritten) {
            writeHeader()
        }
        if (bitsPerSample == 24) {
            writePCM24(value)
        } else {
            writePCM16(value)
        }
    }

    fun write(buffer: FloatArray, startSample: Int, numSamples: Int) {
        for (i in 0 until numSamples) {
            write(buffer[startSample + i])
        }
    }

    private fun writeIntLittle(n: Int) {
        writeByte(n)
        writeByte(n shr 8)
        writeByte(n shr 16)
        writeByte(n shr 24)
    }

    private fun writeShortLittle(n: Int) {
        writeByte(n)
        writeByte(n shr 8)
    }

    private fun writeFormatChunk() {
        val bytesPerSample = (bitsPerSample + 7) / 8

        writeByte('f')
        writeByte('m')
        writeByte('t')
        writeByte(' ')
        writeIntLittle(16) // chunk size
        writeShortLittle(WAVE_FORMAT_PCM)
        writeShortLittle(samplesPerFrame)
        writeIntLittle(frameRate)
        // bytes/second
        writeIntLittle(frameRate * samplesPerFrame * bytesPerSample)
        // block align
        writeShortLittle(samplesPerFrame * bytesPerSample)
        writeShortLittle(bitsPerSample)
    }

    private fun writeDataChunkHeader() {
        writeByte('d')
        writeByte('a')
        writeByte('t')
        writeByte('a')
        // Maximum size is not strictly correct but is commonly used
        // when we do not know the final size.
        writeIntLittle(Int.MAX_VALUE)
    }

    private fun writeHeader() {
        writeRiffHeader()
        writeFormatChunk()
        writeDataChunkHeader()
        mHeaderWritten = true
    }

    private fun writeByte(b: Char) {
        writeByte(b.toByte())
    }

    private fun writeByte(b: Int) {
        writeByte(b.toByte())
    }

    // Write lower 8 bits. Upper bits ignored.
    private fun writeByte(b: Byte) {
        val arr = byteArrayOf(b)
        outputStream.write(arr)
        mBytesWritten += 1
    }

    private fun writePCM24(value: Float) {
        // Offset before casting so that we can avoid using floor().
        // Also round by adding 0.5 so that very small signals go to zero.
        val temp = (PCM24_MAX * value) + 0.5 - PCM24_MIN
        var sample = temp.toInt() + PCM24_MIN
        // clip to 24-bit range
        if (sample > PCM24_MAX) {
            sample = PCM24_MAX
        } else if (sample < PCM24_MIN) {
            sample = PCM24_MIN
        }
        // encode as little-endian
        writeByte(sample) // little end
        writeByte(sample shr 8) // middle
        writeByte(sample shr 16) // big end
    }

    private fun writePCM16(value: Float) {
        // Offset before casting so that we can avoid using floor().
        // Also round by adding 0.5 so that very small signals go to zero.
        val temp = (Short.MAX_VALUE * value) + 0.5 - Short.MIN_VALUE
        var sample = temp.toInt() + Short.MIN_VALUE
        if (sample > Short.MAX_VALUE) {
            sample = Short.MAX_VALUE.toInt()
        } else if (sample < Short.MIN_VALUE) {
            sample = Short.MIN_VALUE.toInt()
        }
        writeByte(sample) // little end
        writeByte(sample shr 8) // big end
    }

    private fun writeRiffHeader() {
        writeByte('R')
        writeByte('I')
        writeByte('F')
        writeByte('F')
        // Maximum size is not strictly correct but is commonly used
        // when we do not know the final size.
        writeIntLittle(Int.MAX_VALUE)
        writeByte('W')
        writeByte('A')
        writeByte('V')
        writeByte('E')
    }

    companion object {
        private const val TAG = "WaveFileWriter"

        private const val PCM24_MIN = -(1 shl 23)
        private const val PCM24_MAX = (1 shl 23) - 1
        private const val WAVE_FORMAT_PCM = 1
    }
}
