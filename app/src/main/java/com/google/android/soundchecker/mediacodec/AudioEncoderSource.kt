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
import android.media.MediaExtractor
import android.media.MediaFormat
import android.util.Log
import com.google.android.soundchecker.utils.AudioSource
import java.util.Arrays
import kotlin.math.min


class AudioEncoderSource(val codec: String, val codecFormat: String, sampleRate: Int,
                         channelCount: Int, val bitRate: Int, val flacCompressionLevel: Int,
                         pcmEncoding: Int, val uri: String) : AudioSource() {
    private var encoder: MediaCodec = MediaCodec.createByCodecName(codec)
    private val outputFormat: MediaFormat = MediaFormat.createAudioFormat(codecFormat, sampleRate,
            channelCount)

    private var audioSource: AudioSource? = null
    private var audioDecoderSource: AudioDecoderSource? = null

    init {
        Log.i(TAG, "Creating AudioEncoderSource, codec=$codec, codecFormat=$codecFormat, " +
                "sampleRate=$sampleRate, bitRate=$bitRate")
        if (codecFormat == MediaFormat.MIMETYPE_AUDIO_FLAC) {
            outputFormat.setInteger(MediaFormat.KEY_FLAC_COMPRESSION_LEVEL, flacCompressionLevel)
        } else {
            outputFormat.setInteger(MediaFormat.KEY_BIT_RATE, bitRate)
        }
        outputFormat.setInteger(MediaFormat.KEY_PCM_ENCODING, pcmEncoding)

        mChannelCount = channelCount
        mEncoding = pcmEncoding
        mSampleRate = sampleRate

        encoder = MediaCodec.createByCodecName(codec)
    }

    fun setSource(source: AudioSource?) {
        audioSource = source
    }

    fun setDecoder(decoder: AudioDecoderSource?) {
        audioDecoderSource = decoder
    }

    fun start() {
        encoder.configure(outputFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
        encoder.start()

        Log.i(TAG, "Outputformat" + encoder.outputFormat)
        Log.i(TAG, "Inputformat" + encoder.inputFormat)

        // Check actual encoding - this may or may not exist
        try {
            mEncoding = encoder.inputFormat.getInteger(MediaFormat.KEY_PCM_ENCODING)
            Log.d(TAG, "pcmEncoding: " + mEncoding)
        } catch (e: Exception) {
            mEncoding = AudioFormat.ENCODING_PCM_16BIT
            Log.d(TAG, "failed to set encoding. Defaulting to 16 bit.")
        }
    }

    fun stop() {
        encoder.stop()
        encoder.release()
    }

    fun getOutputPcmEncoding() : Int {
        // Check actual encoding - this may or may not exist
        try {
            return encoder.outputFormat.getInteger(MediaFormat.KEY_PCM_ENCODING)
        } catch (e: Exception) {
            return AudioFormat.ENCODING_PCM_16BIT
        }
    }

    override fun pull(numBytes: Int, buffer: ByteArray): Int {
        Log.i(TAG, "pulling " + numBytes)
        if (buffer.isEmpty()) {
            Log.i(TAG, "The buffer is empty, do nothing")
            return 0
        }

        var inputIndex: Int
        var outputIndex: Int

        while (true) {
            inputIndex = encoder.dequeueInputBuffer(TIMEOUT_MICROSECONDS)
            if (inputIndex >= 0) {
                val inputBuffer = encoder.getInputBuffer(inputIndex)
                inputBuffer!!.clear()
                val framesToProcess = min(inputBuffer.capacity() / getBytesPerFrame(),
                    FRAMES_TO_PROCESS)
                val inputArray = ByteArray(getBytesPerFrame() * framesToProcess)
                var framesProcessed = 0
                if (mEncoding == AudioFormat.ENCODING_PCM_16BIT) {
                    framesProcessed = audioSource!!.pull(framesToProcess * getBytesPerFrame(),
                            inputArray)
                } else { // FLOAT
                    framesProcessed = audioSource!!.pull(inputArray, framesToProcess)
                }
                //Log.d(TAG, "inputArraya: " + Arrays.toString(inputArray))
                inputBuffer.put(inputArray)
                inputBuffer.flip()
                var flags = 0
                if (framesToProcess != framesProcessed) {
                    flags = MediaCodec.BUFFER_FLAG_END_OF_STREAM
                }
                encoder.queueInputBuffer(inputIndex, 0, getBytesPerFrame() * framesToProcess, 0, flags)
                //Log.d(TAG, " " + inputIndex + " " + getBytesPerFrame() * framesToProcess)
            }

            val bufferInfo = MediaCodec.BufferInfo()
            outputIndex = encoder.dequeueOutputBuffer(bufferInfo, TIMEOUT_MICROSECONDS)

            if (outputIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                Log.d(TAG, "new output format: " + encoder.outputFormat)
                audioDecoderSource?.setFormat(encoder.outputFormat)
            }

            val isConfigFrame = bufferInfo.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG != 0
            if (isConfigFrame) {
                val configBuffer = encoder.getOutputBuffer(outputIndex)
                val arr = ByteArray(bufferInfo.size)
                configBuffer!!.get(arr)
                Log.d(TAG, "config buffer: " + Arrays.toString(arr))
                encoder.releaseOutputBuffer(outputIndex, false)
                continue
            }

            val isEndOfStream = bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0
            if (isEndOfStream) {
                audioDecoderSource?.isEndOfStream = true
                Log.d(TAG, "encoderIsEndOfStream: " + isEndOfStream)
            }

            if (outputIndex >= 0) {
                val outputBuffer = encoder.getOutputBuffer(outputIndex)
                //Log.d(TAG, "offset: " + bufferInfo.offset)
                //Log.d(TAG, "size: " + bufferInfo.size)
                //Log.d(TAG, "numBytes: " + numBytes)
                //Log.d(TAG, "size2: " + buffer.size)
                outputBuffer!!.get(buffer, 0, min(numBytes, bufferInfo.size))
                //Log.d(TAG, "buffer: " + Arrays.toString(buffer))

                encoder.releaseOutputBuffer(outputIndex, false)
                return bufferInfo.size
            }
        }
    }

    companion object {
        private const val TAG = "AudioEncoderSource"

        private const val FRAMES_TO_PROCESS = 16
        private const val TIMEOUT_MICROSECONDS: Long = 2000
    }
}
