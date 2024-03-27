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
import android.util.Log
import com.google.android.soundchecker.harmonicanalyzer.HarmonicAnalyzer
import com.google.android.soundchecker.harmonicanalyzer.HarmonicAnalyzerListener
import kotlin.math.roundToInt

import com.google.android.soundchecker.utils.AudioSink
import com.google.android.soundchecker.utils.AudioSource
import com.google.android.soundchecker.utils.AudioThread
import com.google.android.soundchecker.utils.WaveFileWriter
import com.google.android.soundchecker.utils.byteArrayToFloatArray
import com.google.android.soundchecker.utils.bytesPerSample
import com.google.android.soundchecker.utils.floatArrayToByteArray
import com.google.android.soundchecker.utils.i16ByteArrayToFloatArray
import java.io.File
import java.io.FileOutputStream
import java.util.Arrays

class AudioEncoderDecoderHarmonicAnalyzerSink(outputFile: File): AudioSink() {
    private var mAudioSource: AudioSource? = null
    private var mThread: AudioThread? = null
    private var mBuffer: ByteArray? = null
    private var mPcmEncoding = 0

    var mFftSize = 1024 // must be power of 2
    var mFundamentalBin = calculateNearestBin(TARGET_FREQUENCY)
    private val mListeners = ArrayList<HarmonicAnalyzerListener>()
    private val mAnalyzer = HarmonicAnalyzer()
    private val mFileOutputStream = FileOutputStream(outputFile)

    override fun setSource(source: AudioSource?) {
        mAudioSource = source
    }

    override fun start() {
        mThread = object : AudioThread() {
            override fun run() {
                runAudioLoop()
            }
        }
        if (mBuffer == null) {
            mBuffer = ByteArray(mFftSize * 4)
        }
        mThread!!.start()
    }

    override fun stop() {
        mThread?.stop()
    }

    fun runAudioLoop() {
        var outputBitsPerSample = 16
        if (mPcmEncoding == AudioFormat.ENCODING_PCM_FLOAT) {
            outputBitsPerSample = 24
        }
        val waveFileWriter = WaveFileWriter(mFileOutputStream, mSampleRate, mChannelCount,
            outputBitsPerSample)
        var count = 0
        while (mThread?.isEnabled() == true) {
            val audioSource = mAudioSource!!
            val buffer = mBuffer!!
            // pull audio from source
            audioSource.pull(buffer.size, buffer)
            //Log.d(TAG, "bufferSize: " + buffer.size)
            //Log.d(TAG, "buffer: " + Arrays.toString(buffer))
            val floatArray = FloatArray(mFftSize)
            //Log.d(TAG, "floatArray indices: " + floatArray.indices)
            if (mPcmEncoding == AudioFormat.ENCODING_PCM_FLOAT) {
                byteArrayToFloatArray(buffer, floatArray)
            } else if (mPcmEncoding == AudioFormat.ENCODING_PCM_16BIT) {
                i16ByteArrayToFloatArray(buffer, floatArray)
            } else {
                Log.d(TAG, "unsupported pcmEncoding: " + mPcmEncoding)
            }
            waveFileWriter.write(floatArray, 0, floatArray.size)
            //Log.d(TAG, "floatArray: " + Arrays.toString(floatArray))
            // Analyze it
            val result = mAnalyzer.analyze(floatArray, mFftSize, mFundamentalBin)
            fireListeners(count++, result)
        }
        mFileOutputStream.flush()
        mFileOutputStream.close()
    }

    private fun fireListeners(count: Int, result: HarmonicAnalyzer.Result) {
        for (listener in mListeners) {
            listener.onMeasurement(count, result)
        }
    }

    fun addListener(listener: HarmonicAnalyzerListener) {
        mListeners.add(listener)
    }

    fun calculateBinFrequency(bin: Int): Double {
        return (mSampleRate * bin / mFftSize).toDouble()
    }

    fun calculateNearestBin(frequency: Double): Int {
        return (mFftSize * frequency / mSampleRate).roundToInt()
    }

    fun setOutputPcmEncoding(pcmEncoding: Int) {
        mPcmEncoding = pcmEncoding
        mBuffer = ByteArray(mFftSize * mPcmEncoding.bytesPerSample())
    }

    companion object {
        private const val TAG = "AudioEncoderDecoderHarmonicAnalyzerSink"
        const val TARGET_FREQUENCY = 1000.0
    }
}
