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

import java.io.BufferedInputStream
import java.io.IOException
import java.io.InputStream
import java.util.Arrays

class DsfReader constructor(inputStream: InputStream) {
    companion object {
        private const val TAG = "DsfReader"

        private val DSD_CHUNK_HEADER_ID = byteArrayOf(
                'D'.code.toByte(), 'S'.code.toByte(), 'D'.code.toByte(), ' '.code.toByte())
        private val FMT_CHUNK_HEADER_ID = byteArrayOf(
                'f'.code.toByte(), 'm'.code.toByte(), 't'.code.toByte(), ' '.code.toByte())
        private val DATA_CHUNK_HEADER_ID = byteArrayOf(
                'd'.code.toByte(), 'a'.code.toByte(), 't'.code.toByte(), 'a'.code.toByte())
        private const val CHUNK_HEADER_LENGTH = 12 // header type + size of chunk

        private const val DSD_HEADER_SIZE = 28
        private const val MIN_FMT_SIZE = 48
        private const val MAX_CHANNEL_COUNT = 6
        private const val BLOCK_SIZE_PER_CHANNEL = 4096

        private val CHANNEL_TYPE_MAP = mapOf(
                1 to AudioFormat.CHANNEL_OUT_MONO,
                2 to AudioFormat.CHANNEL_OUT_STEREO,
                3 to (AudioFormat.CHANNEL_OUT_FRONT_LEFT
                        or AudioFormat.CHANNEL_OUT_FRONT_RIGHT
                        or AudioFormat.CHANNEL_OUT_FRONT_CENTER),
                4 to AudioFormat.CHANNEL_OUT_QUAD,
                5 to (AudioFormat.CHANNEL_OUT_FRONT_LEFT
                        or AudioFormat.CHANNEL_OUT_FRONT_RIGHT
                        or AudioFormat.CHANNEL_OUT_FRONT_CENTER
                        or AudioFormat.CHANNEL_OUT_LOW_FREQUENCY),
                6 to (AudioFormat.CHANNEL_OUT_QUAD or AudioFormat.CHANNEL_OUT_LOW_FREQUENCY),
                7 to AudioFormat.CHANNEL_OUT_5POINT1
        )

        private const val DEFAULT_ENCODING = AudioFormat.ENCODING_PCM_24BIT_PACKED
    }

    private var mStream: InputStream? = null

    private var mDataLeft: Long = 0
    private var mTotalFileSize: Long = 0
    private var mIsInvalidFile = false

    private var mAudioFormatBuilder: AudioFormat.Builder? = null
    private var mChannelCount = 0
    private var mBitsPerSample = 0
    private var mSampleCount: Long = 0

    private val mData = Array(MAX_CHANNEL_COUNT) {
        ByteArray(
                BLOCK_SIZE_PER_CHANNEL
        )
    }
    private val mChannelCursor = IntArray(MAX_CHANNEL_COUNT)

    init {
        mStream = BufferedInputStream(inputStream)
        mAudioFormatBuilder = AudioFormat.Builder().setEncoding(DEFAULT_ENCODING)
    }

    fun prepareForReadingData(): Boolean {
        if (!readFileHeader() || !readFmt() || !readDataHeader()) {
            Log.e(TAG, "Failed to prepare for reading")
            mIsInvalidFile = true
            return false
        }
        for (i in 0 until mChannelCount) {
            if (!readNextBlock(i)) {
                return false
            }
        }
        return true
    }

    private fun reverseByte(value: Byte): Byte {
        var value = value
        var b: Byte = 0x0
        for (i in 0..7) {
            b = (b.toInt() shl 1).toByte()
            b = (b.toInt() or (value.toInt() and 0x1)).toByte()
            value = (value.toInt() shr 1).toByte()
        }
        return b
    }

    private fun getByteAccordingToBitsPerSample(value: Byte): Byte {
        // If bits per sample is 8, the data is stored as MSB.
        // Otherwise, the data is stored as LSB.
        return if (mBitsPerSample == 8) value else reverseByte(value)
    }

    fun read(channel: Int): Byte? {
        if (mChannelCursor[channel] >= BLOCK_SIZE_PER_CHANNEL && !readNextBlock(channel)) {
            return null
        }
        val v = getByteAccordingToBitsPerSample(mData[channel][mChannelCursor[channel]])
        mChannelCursor[channel]++
        return v
    }

    fun getAudioFormatBuilder(): AudioFormat.Builder? {
        return if (mIsInvalidFile) {
            null
        } else mAudioFormatBuilder
    }

    fun getChannelCount(): Int {
        return mChannelCount
    }

    fun isEndOfFile(): Boolean {
        return mDataLeft <= 0
    }

    fun close() {
        try {
            if (mStream != null) {
                mStream!!.close()
            }
        } catch (e: IOException) {
            Log.e(TAG, "Error when closing reader:$e")
        }
    }

    private fun readNextBlock(channel: Int): Boolean {
        Arrays.fill(mData[channel], 0, mData[channel].size - 1, 0x0.toByte())
        try {
            val sz = mStream!!.read(mData[channel])
            if (sz != mData[channel].size) {
                Log.w(TAG, "Cannot read full buffer: $sz")
            }
            mDataLeft -= sz.toLong()
            mChannelCursor[channel] = 0
        } catch (e: IOException) {
            Log.e(TAG, "Unable to read, set end of file. Error:$e")
            return false
        }
        return true
    }

    private fun readInteger(length: Int): Long? {
        val bytes = ByteArray(length)
        try {
            if (mStream!!.read(bytes) == length) {
                return convertToLongFrom(bytes)
            }
        } catch (e: IOException) {
            Log.e(TAG, "Error when reading:$e")
        }
        return null
    }

    private fun skipData(n: Long): Boolean {
        try {
            mStream!!.skip(n)
            return true
        } catch (e: IOException) {
            Log.e(TAG, "Failed to skip data n=$n")
        }
        return false
    }

    private fun readChunkHeader(targetHeaderId: ByteArray): Long {
        val headerBytes = ByteArray(CHUNK_HEADER_LENGTH)
        return try {
            if (mStream!!.read(headerBytes) != CHUNK_HEADER_LENGTH) {
                Log.w(TAG, "Cannot read header")
                return 0
            }
            val headerType = Arrays.copyOfRange(headerBytes, 0, 4)
            if (!Arrays.equals(headerType, targetHeaderId)) {
                Log.e(
                        TAG, "Wrong chunk header type:" + Arrays.toString(headerType)
                        + " expected:" + Arrays.toString(targetHeaderId)
                )
                return 0
            }
            convertToLongFrom(Arrays.copyOfRange(headerBytes, 4, 12))
        } catch (e: IOException) {
            Log.e(TAG, "Failed to read chunk header:$e")
            0
        }
    }

    private fun readFileHeader(): Boolean {
        var length = readChunkHeader(DSD_CHUNK_HEADER_ID)
        if (length == 0L || length != DSD_HEADER_SIZE.toLong()) {
            Log.e(TAG, "Invalid file header length:$length")
            return false
        }
        length -= CHUNK_HEADER_LENGTH.toLong()
        val fileSize = readInteger(8)
        if (fileSize == null) {
            Log.e(TAG, "Failed to read file size")
            return false
        }
        mTotalFileSize = fileSize
        length -= 8
        Log.i(TAG, "Total file size=$mTotalFileSize")
        skipData(8) // Skip pointer to metadata chunk
        return true
    }

    private fun readFmt(): Boolean {
        var length = readChunkHeader(FMT_CHUNK_HEADER_ID)
        if (length < MIN_FMT_SIZE) {
            Log.e(TAG, "Invalid fmt chunk length:$length")
            return false
        }
        Log.i(TAG, "Fmt length=$length")
        length -= CHUNK_HEADER_LENGTH.toLong() // Fmt chunk header and size
        if (!skipData(8)) { // Skip format version, format ID
            return false
        }
        length -= 8
        val channelType = readInteger(4)
        if (channelType == null) {
            Log.e(TAG, "Unable to read channel type")
            return false
        }
        Log.i(TAG, "Channel type=$channelType")
        if (!setChannelMask(channelType.toInt())) {
            Log.e(TAG, "Invalid channel type:$channelType")
            return false
        }
        length -= 4
        val channelNum = readInteger(4)
        if (channelNum == null) {
            Log.e(TAG, "Unable to read channel num")
            return false
        }
        Log.i(TAG, "Channel num=$channelNum")
        mChannelCount = channelNum.toInt()
        if (mChannelCount <= 0 || mChannelCount > MAX_CHANNEL_COUNT) {
            Log.e(TAG, "Invalid channel num:$mChannelCount")
            return false
        }
        length -= 4
        val sampleRate = readInteger(4)
        if (sampleRate == null) {
            Log.e(TAG, "Unable to read sample rate")
            return false
        }
        mAudioFormatBuilder!!.setSampleRate(sampleRate.toInt() / 16)
        Log.i(TAG, "Sample rate:$sampleRate")
        length -= 4
        val bitsPerSample = readInteger(4)
        if (bitsPerSample == null) {
            Log.e(TAG, "Unable to read bits per sample")
            return false
        }
        mBitsPerSample = bitsPerSample.toInt()
        Log.i(TAG, "Bits per sample:$mBitsPerSample")
        if (mBitsPerSample != 1 && mBitsPerSample != 8) {
            Log.e(TAG, "Invalid bits per sample:$mBitsPerSample")
            return false
        }
        length -= 4
        val sampleCount = readInteger(8)
        if (sampleCount == null) {
            Log.e(TAG, "Unable to read sample count")
            return false
        }
        mSampleCount = sampleCount
        Log.i(TAG, "Sample count=$mSampleCount")
        length -= 8
        val blockSizePerChannel = readInteger(4)
        if (blockSizePerChannel == null) {
            Log.e(TAG, "Unable to read block size per channel")
            return false
        }
        Log.i(TAG, "Block size per channel=$blockSizePerChannel")
        if (blockSizePerChannel.toInt() !== BLOCK_SIZE_PER_CHANNEL) {
            Log.e(TAG, "Invalid block size per channel: $blockSizePerChannel")
            return false
        }
        length -= 4
        skipData(length)
        return true
    }

    private fun readDataHeader(): Boolean {
        val length = readChunkHeader(DATA_CHUNK_HEADER_ID)
        if (length < CHUNK_HEADER_LENGTH) {
            Log.e(TAG, "Invalid data chunk length:$length")
            return false
        }
        mDataLeft = length - CHUNK_HEADER_LENGTH
        Log.i(TAG, "Data chunk size=$length")
        return true
    }

    private fun convertToLongFrom(arr: ByteArray): Long {
        // Numerical data is stored as LSB.
        var ret: Long = 0
        for (i in arr.indices.reversed()) {
            ret = ret shl 8
            ret += arr[i].toLong()
        }
        return ret
    }

    private fun setChannelMask(value: Int): Boolean {
        val channelMask = CHANNEL_TYPE_MAP[value]
        if (channelMask != null) {
            Log.i(TAG, "Channel mask=$channelMask")
            mAudioFormatBuilder!!.setChannelMask(channelMask)
            return true
        }
        return false
    }
}
