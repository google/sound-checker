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

package com.google.android.soundchecker.dsd

import android.media.AudioFormat
import android.util.Log

import java.util.Arrays

import kotlin.math.min

import com.google.android.soundchecker.utils.AudioSource

class DopWrapper @Throws(IllegalArgumentException::class) constructor(
        dsfReader: DsfReader,
        encoding: Int
) : AudioSource() {

    companion object {
        private const val TAG = "DOPHelper"
    }

    private var mDsfReader: DsfReader? = null
    private var mBytesPerSample = 0
    private var mBytesPerFrame = 0

    private val mSyncBytes = byteArrayOf(0x05.toByte(), 0xfa.toByte())
    private var mSyncByteIndex = 0

    init {
        Log.i(TAG, "Creating DopWrapper, encoding=$encoding")
        if (encoding != AudioFormat.ENCODING_PCM_24BIT_PACKED &&
                encoding != AudioFormat.ENCODING_PCM_32BIT) {
            throw IllegalArgumentException("Invalid encoding=$encoding for creating DopWrapper")
        }
        mDsfReader = dsfReader
        mChannelCount = dsfReader.getChannelCount()
        mEncoding = encoding
        mBytesPerSample = getBytesPerSample()
        mBytesPerFrame = getBytesPerFrame()
    }

    override fun pull(buffer: ByteArray, numFrames: Int): Int {
        if (buffer.isEmpty()) {
            Log.i(TAG, "The buffer is empty, do nothing")
            return 0
        }
        Arrays.fill(buffer, 0, buffer.size - 1, 0x00.toByte())
        var index = 0
        val targetFrames = min(numFrames, buffer.size / mBytesPerFrame)
        for (i in 0 until targetFrames) {
            val syncByte = getSyncByte()
            for (j in 0 until mChannelCount) {
                index += mBytesPerSample
                if (!fillData(buffer, j, index - 1, syncByte)) {
                    return i
                }
            }
        }
        return targetFrames
    }

    private fun fillData(arr: ByteArray, channel: Int, index: Int, syncByte: Byte): Boolean {
        var idx = index
        arr[idx] = syncByte
        idx--
        for (i in 0..1) {
            val data = mDsfReader!!.read(channel) ?: return false
            arr[idx] = data
            idx--
        }
        return true
    }

    private fun getSyncByte(): Byte {
        val syncByte = mSyncBytes[mSyncByteIndex]
        mSyncByteIndex = mSyncByteIndex xor 1
        return syncByte
    }
}
