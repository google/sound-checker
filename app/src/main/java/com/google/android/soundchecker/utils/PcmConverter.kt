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

import android.media.AudioFormat

import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * A converter to convert PCM data. Currently supports PCM 24 bit to PCM 32 bit.
 */
class PcmConverter(private val encodingIn: Int, private val encodingOut: Int) {
    companion object {
        private val VALID_ENCODINGS_IN = setOf(AudioFormat.ENCODING_PCM_24BIT_PACKED)
        private val VALID_ENCODINGS_OUT = setOf(AudioFormat.ENCODING_PCM_32BIT)
    }

    private val mBytesPerInputSample: Int
    private val mBytesPerOutputSample: Int
    private var mBuffer: ByteBuffer

    init {
        if (!VALID_ENCODINGS_IN.contains(encodingIn)) {
            throw IllegalArgumentException("Invalid encoding in:$encodingIn")
        }
        if (!VALID_ENCODINGS_OUT.contains(encodingOut)) {
            throw IllegalArgumentException("Invalid encoding in:$encodingOut")
        }
        mBytesPerInputSample = encodingIn.bytesPerSample()
        mBytesPerOutputSample = encodingOut.bytesPerSample()
        mBuffer = ByteBuffer.allocateDirect(0).order(ByteOrder.nativeOrder())
    }

    fun getOutputBuffer(input: ByteBuffer): ByteBuffer {
        val position = input.position()
        val limit = input.limit()
        val outputBufferSize = (limit - position) / mBytesPerInputSample * mBytesPerOutputSample
        val buffer = getOutputBuffer(outputBufferSize)
        when (encodingIn) {
            AudioFormat.ENCODING_PCM_24BIT_PACKED -> {
                when (encodingOut) {
                    AudioFormat.ENCODING_PCM_32BIT -> {
                        var i = position
                        while (i < limit) {
                            buffer.put(0.toByte())
                            buffer.put(input[i])
                            buffer.put(input[i + 1])
                            buffer.put(input[i + 2])
                            i += mBytesPerInputSample
                        }
                    }
                    else -> {}
                }
            }

            else -> {}
        }
        input.position(position)
        buffer.flip()
        return buffer
    }

    private fun getOutputBuffer(size: Int): ByteBuffer {
        if (mBuffer.capacity() < size) {
            mBuffer = ByteBuffer.allocateDirect(size).order(ByteOrder.nativeOrder())
        } else {
            mBuffer.clear()
        }
        return mBuffer
    }
}