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

package com.google.android.soundchecker.harmonicanalyzer

import kotlin.math.roundToInt

import com.google.android.soundchecker.utils.AudioSink
import com.google.android.soundchecker.utils.AudioSource
import com.google.android.soundchecker.utils.AudioThread

class HarmonicAnalyzerSink : AudioSink() {
    private var mAudioSource: AudioSource? = null
    private var mThread: AudioThread? = null
    private var mBuffer: FloatArray? = null

    var mFftSize = 1024 // must be power of 2
    var mFundamentalBin = calculateNearestBin(TARGET_FREQUENCY)
    private val mListeners = ArrayList<HarmonicAnalyzerListener>()
    private val mAnalyzer = HarmonicAnalyzer()

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
            mBuffer = FloatArray(mFftSize)
        }
        mThread!!.start()
    }

    override fun stop() {
        mThread?.stop()
    }

    fun runAudioLoop() {
        var count = 0
        while (mThread?.isEnabled() != true) {
            val audioSource = mAudioSource!!
            val buffer = mBuffer!!
            // pull audio from source
            val framesRead = audioSource.render(buffer, 0, 1, mFftSize)
            // Analyze it
            val result = mAnalyzer.analyze(buffer, framesRead, mFundamentalBin)
            fireListeners(count++, result)
        }
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

    private fun calculateNearestBin(frequency: Double): Int {
        return (mFftSize * frequency / mSampleRate).roundToInt()
    }

    companion object {
        private const val TAG = "HarmonicAnalyzerSink"
        private const val TARGET_FREQUENCY = 1000.0
    }
}
