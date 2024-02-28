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

package com.google.android.soundchecker.mediacodec

import android.media.AudioFormat
import android.media.MediaCodec
import android.media.MediaCodecList
import android.media.MediaFormat
import android.util.Log
import com.google.android.soundchecker.utils.AudioSource
import java.nio.ByteBuffer
import java.nio.charset.Charset
import java.util.Arrays
import kotlin.math.min

class AudioDecoderSource() : AudioSource() {

    companion object {
        private const val TAG = "AudioDecoderSource"

        private const val MAX_BYTES_TO_PULL = 4000
        private const val TIMEOUT_MICROSECONDS: Long = 2000
    }

    private var decoder: MediaCodec? = null
    private var inputFormat: MediaFormat? = null

    private var audioSource: AudioSource? = null
    private var lastOutputBuffer: ByteArray? = null
    private var lastOutputBufferPointer = 0

    private var inputArray = ByteArray(MAX_BYTES_TO_PULL)

    fun setSource(source: AudioSource?) {
        audioSource = source
    }

    fun setFormat(format: MediaFormat) {
        mChannelCount = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
        mSampleRate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE)
        inputFormat = MediaFormat.createAudioFormat(format.getString(MediaFormat.KEY_MIME)!!,
            mSampleRate, mChannelCount)
        val csd0Buffer = format.getByteBuffer("csd-0")
        if (csd0Buffer != null) {
            Log.d(TAG, "csdArr: " + Arrays.toString(csd0Buffer.array()))
            Log.d(TAG, "csd-0: " + String(csd0Buffer.array(), Charset.defaultCharset()))
            inputFormat!!.setByteBuffer("csd-0", csd0Buffer);
        }
        var csd1Buffer = format.getByteBuffer("csd-1")
        if (csd1Buffer != null) {
            Log.d(TAG, "csd-1: " + String(csd1Buffer.array(), Charset.defaultCharset()))
            inputFormat!!.setByteBuffer("csd-1", csd1Buffer);
        } else if (format.getString(MediaFormat.KEY_MIME) == MediaFormat.MIMETYPE_AUDIO_OPUS) {
            val csd1Bytes = byteArrayOf(0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00)
            csd1Buffer = ByteBuffer.wrap(csd1Bytes)
            inputFormat!!.setByteBuffer("csd-1", csd1Buffer);
        }
        var csd2Buffer = format.getByteBuffer("csd-2")
        if (csd2Buffer != null) {
            Log.d(TAG, "csd-2: " + String(csd2Buffer.array(), Charset.defaultCharset()))
            inputFormat!!.setByteBuffer("csd-2", csd2Buffer);
        } else if (format.getString(MediaFormat.KEY_MIME) == MediaFormat.MIMETYPE_AUDIO_OPUS) {
            val csd2Bytes = byteArrayOf(0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00)
            csd2Buffer = ByteBuffer.wrap(csd2Bytes)
            inputFormat!!.setByteBuffer("csd-2", csd2Buffer);
        }
        //inputFormat = format
        if (format.getString(MediaFormat.KEY_MIME) == MediaFormat.MIMETYPE_AUDIO_FLAC) {
            inputFormat!!.setInteger(MediaFormat.KEY_FLAC_COMPRESSION_LEVEL, format.getInteger(MediaFormat
                .KEY_FLAC_COMPRESSION_LEVEL))
        } else {
            inputFormat!!.setInteger(MediaFormat.KEY_BIT_RATE, format.getInteger(MediaFormat
                .KEY_BIT_RATE))
        }

        // Check actual encoding - this may or may not exist
        try {
            mEncoding = format.getInteger(MediaFormat.KEY_PCM_ENCODING)
            Log.d(TAG, "pcmEncoding: " + format.getInteger(MediaFormat.KEY_PCM_ENCODING))
            inputFormat!!.setInteger(MediaFormat.KEY_PCM_ENCODING, mEncoding)
        } catch (e: Exception) {
            mEncoding = AudioFormat.ENCODING_PCM_16BIT
        }

        //inputFormat!!.setByteBuffer("csd-1", ByteBuffer.wrap(byteArrayOf(0, 0, 0, 1, 104, -18,
        //    60, -128)));
        //MediaCodec.createDecoderByType(format.getString(MediaFormat.KEY_MIME)!!)
        start()
    }

    fun start() {
        val mcl = MediaCodecList(MediaCodecList.ALL_CODECS)
        val decoderName = mcl.findDecoderForFormat(inputFormat)
        decoder = MediaCodec.createByCodecName(decoderName)
        //decoder = MediaCodec.createDecoderByType(inputFormat!!.getString(MediaFormat.KEY_MIME)!!)
        decoder!!.configure(inputFormat, null, null, 0)
        val format = decoder?.outputFormat
        Log.d(TAG, "format: " + format)
        Log.d(TAG, "inputFormat: " + inputFormat)
        Log.d(TAG, "inputFormat2: " + decoder?.inputFormat)
        //assert(format.getString(MediaFormat.KEY_MIME) == inputFormat.getString(MediaFormat
        //    .KEY_MIME))
        decoder!!.start()
    }

    fun stop() {
        decoder?.stop()
        decoder?.release()
    }

    override fun pull(numBytes: Int, buffer: ByteArray): Int {
        Log.i(TAG, "pulling " + numBytes)
        if (buffer.isEmpty()) {
            Log.i(TAG, "The buffer is empty, do nothing")
            return 0
        }
        Arrays.fill(buffer, 0, buffer.size - 1, 0x00.toByte())

        var inputIndex: Int
        var outputIndex: Int

        var destinationBufferPointer = 0
        var destinationBufferSize = min(buffer.size, numBytes);

        while (true) {
            if (lastOutputBuffer != null) {
                val lastOutputBufferDataSize = lastOutputBuffer!!.size - lastOutputBufferPointer
                val amountToCopyFromLastOutputBuffer = min(lastOutputBufferDataSize, buffer.size - destinationBufferPointer)
                lastOutputBuffer!!.copyInto(buffer, destinationBufferPointer,
                    lastOutputBufferPointer, lastOutputBufferPointer + amountToCopyFromLastOutputBuffer)
                destinationBufferPointer += amountToCopyFromLastOutputBuffer
                lastOutputBufferPointer += amountToCopyFromLastOutputBuffer
                if (lastOutputBufferPointer >= lastOutputBuffer!!.size) {
                    lastOutputBuffer = null
                }
                //Log.d(TAG, "1pointer: " + destinationBufferPointer + " size: " +
                //        destinationBufferSize)
                if (destinationBufferPointer >= destinationBufferSize) {
                    return destinationBufferSize
                }
            }

            var isInitialDecode = false
            var encodedSize = 0
            if (decoder == null) {
                encodedSize = audioSource!!.pull(MAX_BYTES_TO_PULL, inputArray)
                isInitialDecode = true
                //Thread.sleep(5_000)
            }

            inputIndex = decoder!!.dequeueInputBuffer(TIMEOUT_MICROSECONDS)
            if (inputIndex >= 0) {
                val inputBuffer = decoder!!.getInputBuffer(inputIndex)
                inputBuffer!!.clear()
                if (!isInitialDecode) {
                    if (inputBuffer.capacity() > inputArray.size) {
                        inputArray = ByteArray(inputBuffer.capacity())
                    }
                    encodedSize = audioSource!!.pull(inputBuffer.capacity(), inputArray)
                }
                //Log.d(TAG, "capacity: " + inputBuffer.capacity())
                //Log.d(TAG, "inputArray: " + Arrays.toString(inputArray))
                inputBuffer.put(inputArray)
                decoder!!.queueInputBuffer(inputIndex, 0, encodedSize,
                    0, 0)
                //Log.d(TAG, "queued " + inputIndex + " " + encodedSize)
            }

            val bufferInfo = MediaCodec.BufferInfo()
            outputIndex = decoder!!.dequeueOutputBuffer(bufferInfo, TIMEOUT_MICROSECONDS)

            if (outputIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                Log.d(TAG, "new output format: " + decoder!!.outputFormat)
            }

            val isConfigFrame = bufferInfo.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG != 0
            if (isConfigFrame) {
                val configBuffer = decoder!!.getOutputBuffer(outputIndex)
                val arr = ByteArray(bufferInfo.size)
                configBuffer!!.get(arr)
                Log.d(TAG, "config buffer2: " + Arrays.toString(arr))
                decoder!!.releaseOutputBuffer(outputIndex, false)
                continue
            }

            //Log.d(TAG, "output index: " + outputIndex)
            while (outputIndex >= 0) {
                val outputBuffer = decoder!!.getOutputBuffer(outputIndex)
                //Log.d(TAG, "offset2: " + bufferInfo.offset)
                bufferInfo.presentationTimeUs
                lastOutputBuffer = ByteArray(bufferInfo.size)
                lastOutputBufferPointer = 0;
                outputBuffer!!.get(lastOutputBuffer!!)
                //Log.d(TAG, " " + bufferInfo.size)
                //Log.d(TAG, "lastOutputBuffer: " + Arrays.toString(lastOutputBuffer))

                if (lastOutputBuffer != null) {
                    val lastOutputBufferDataSize = lastOutputBuffer!!.size - lastOutputBufferPointer
                    val amountToCopyFromLastOutputBuffer = min(lastOutputBufferDataSize, buffer.size - destinationBufferPointer)
                    lastOutputBuffer!!.copyInto(buffer, destinationBufferPointer,
                        lastOutputBufferPointer, lastOutputBufferPointer + amountToCopyFromLastOutputBuffer)
                    destinationBufferPointer += amountToCopyFromLastOutputBuffer
                    lastOutputBufferPointer += amountToCopyFromLastOutputBuffer
                    //Log.d(TAG, "2pointer: " + destinationBufferPointer + " size: " +
                    //        destinationBufferSize)
                    if (destinationBufferPointer >= destinationBufferSize) {
                        decoder!!.releaseOutputBuffer(outputIndex, false)
                        //Log.d(TAG, "test3")
                        return destinationBufferSize
                    }
                }

                decoder!!.releaseOutputBuffer(outputIndex, false)
                outputIndex = decoder!!.dequeueOutputBuffer(bufferInfo, TIMEOUT_MICROSECONDS)
            }
        }
    }
}
