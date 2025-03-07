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

import kotlin.math.abs
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.sqrt

import com.google.android.soundchecker.utils.FastFourierTransform

/**
 * Analyze samples in a float buffer and determine THD, THD+N and SNR.
 * The algorithms are based on:
 * https://en.wikipedia.org/wiki/Total_harmonic_distortion
 * https://en.wikipedia.org/wiki/Signal-to-noise_ratio
 */
class HarmonicAnalyzer {
    private var mImaginary: FloatArray? = null
    private var mPeakMargin = 1 // number of extra bins on each side

    class Result {
        var totalHarmonicDistortion = 0.0
        var totalHarmonicDistortionPlusNoise = 0.0
        var signalNoiseRatioDB = 0.0
        var peakAmplitude = 0.0
        var bins: FloatArray? = null
        var buffer: FloatArray? = null
        var endOfStream = false
        var numOfChannels = 1
    }

    var peakMargin: Int
        get() = mPeakMargin
        set(peakMargin) {
            mPeakMargin = peakMargin.coerceAtLeast(0).coerceAtMost(5)
        }

    /**
     *
     * @param buffer
     * @param numFrames must be a power of two
     * @param signalBin if set to zero, only the peak amplitude and bins are valid
     * @return
     */
    fun analyze(buffer: FloatArray, numFrames: Int, signalBin: Int): Result {
        if (numFrames and (numFrames - 1) != 0) {
            throw IllegalArgumentException("numFrames should be power of two, not $numFrames")
        }

        val result = Result()
        result.buffer = buffer.clone()
        result.peakAmplitude = calculatePeakAmplitude(buffer, numFrames)

        initializeImaginaryArray(numFrames)
        FastFourierTransform.fft(numFrames, buffer, mImaginary!!)

        if (signalBin != 0) {
            analyzeSignal(result, buffer, numFrames, signalBin)
        }

        calculateMagnitudes(result, buffer, numFrames)

        return result
    }

    private fun calculatePeakAmplitude(buffer: FloatArray, numFrames: Int): Double {
        var peak = 0.0f
        for (i in 0 until numFrames) {
            peak = max(peak, abs(buffer[i]))
        }
        return peak.toDouble()
    }

    private fun initializeImaginaryArray(numFrames: Int) {
        if (mImaginary == null || mImaginary!!.size != numFrames) {
            mImaginary = FloatArray(numFrames)
        } else {
            mImaginary!!.fill(0.0f)
        }
    }

    private fun analyzeSignal(result: Result, buffer: FloatArray, numFrames: Int, signalBin: Int) {
        val signalMagSquared = calculateSignalMagnitudeSquared(buffer, signalBin)
        result.totalHarmonicDistortion = calculateTotalHarmonicDistortion(buffer, numFrames, signalBin, signalMagSquared)
        result.totalHarmonicDistortionPlusNoise = calculateTHDPlusNoise(buffer, numFrames, signalMagSquared)

        val noiseMagSquared = calculateNoiseMagnitudeSquared(buffer, numFrames, signalMagSquared)
        result.signalNoiseRatioDB = calculateSignalNoiseRatioDB(signalMagSquared, noiseMagSquared)
    }

    private fun calculateSignalMagnitudeSquared(buffer: FloatArray, signalBin: Int): Float {
        var signalMagSquared = 0.0f
        for (i in -mPeakMargin..mPeakMargin) {
            val bin = signalBin + i
            signalMagSquared += magnitudeSquared(buffer[bin], mImaginary!![bin])
        }
        return VERY_SMALL_NUMBER.coerceAtLeast(signalMagSquared)
    }

    private fun calculateTotalHarmonicDistortion(buffer: FloatArray, numFrames: Int, signalBin: Int, signalMagSquared: Float): Double {
        var totalHarmonicsMagSquared = 0.0f
        val limit = numFrames / (2 * signalBin)
        for (harmonicScaler in 2 until limit) {
            for (i in -mPeakMargin..mPeakMargin) {
                val bin = (signalBin * harmonicScaler) + i
                totalHarmonicsMagSquared += magnitudeSquared(buffer[bin], mImaginary!![bin])
            }
        }
        return sqrt((totalHarmonicsMagSquared / signalMagSquared).toDouble())
    }

    private fun calculateTHDPlusNoise(buffer: FloatArray, numFrames: Int, signalMagSquared: Float): Double {
        var totalMagSquared = 0.0f
        for (i in 1 until numFrames / 2) {
            totalMagSquared += magnitudeSquared(buffer[i], mImaginary!![i])
        }
        val noiseMagSquared = max(VERY_SMALL_NUMBER, totalMagSquared - signalMagSquared)
        return sqrt((noiseMagSquared / signalMagSquared).toDouble())
    }

    private fun calculateNoiseMagnitudeSquared(buffer: FloatArray, numFrames: Int, signalMagSquared: Float): Float {
        var totalMagSquared = 0.0f
        for (i in 1 until numFrames / 2) {
            totalMagSquared += magnitudeSquared(buffer[i], mImaginary!![i])
        }
        return max(VERY_SMALL_NUMBER, totalMagSquared - signalMagSquared)
    }

    private fun calculateSignalNoiseRatioDB(signalMagSquared: Float, noiseMagSquared: Float): Double {
        val signalNoisePowerRatio = signalMagSquared / noiseMagSquared
        return powerToDecibels(signalNoisePowerRatio.toDouble())
    }

    private fun calculateMagnitudes(result: Result, buffer: FloatArray, numFrames: Int) {
        result.bins = FloatArray(numFrames / 2)
        for (i in 0 until numFrames / 2) {
            result.bins!![i] = sqrt(magnitudeSquared(buffer[i], mImaginary!![i]))
        }
    }

    private fun magnitudeSquared(real: Float, imaginary: Float): Float {
        return real * real + imaginary * imaginary
    }

    companion object {
        private val TAG = "HarmonicAnalyzer"
        const val VERY_SMALL_NUMBER = 1.0e-12.toFloat()
        fun amplitudeToDecibels(amplitude: Double): Double {
            return 20.0 * ln(amplitude)
        }

        fun powerToDecibels(amplitude: Double): Double {
            return 10.0 * ln(amplitude)
        }
    }
}
