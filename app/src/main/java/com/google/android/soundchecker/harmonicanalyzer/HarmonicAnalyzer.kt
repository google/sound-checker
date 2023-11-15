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
     * @param signalBin
     * @return
     */
    fun analyze(buffer: FloatArray, numFrames: Int, signalBin: Int): Result {
        val result = Result()
        if (numFrames and numFrames - 1 != 0) {
            throw IllegalArgumentException(
                    "numFrames should be power of two, not "
                            + numFrames
            )
        }

        // peak amplitude
        var peak = 0.0f
        for (i in 0 until numFrames) {
            val sample = abs(buffer[i])
            if (sample > peak) {
                peak = sample
            }
        }
        result.peakAmplitude = peak.toDouble()

        // Allocate array to receive the imaginary component.
        if (mImaginary == null || mImaginary!!.size != numFrames) {
            mImaginary = FloatArray(numFrames)
        } else {
            for (i in mImaginary!!.indices) {
                mImaginary!![i] = 0.0f
            }
        }
        FastFourierTransform.fft(numFrames, buffer, mImaginary!!)

        // Measure over a few adjacent bins.
        var signalMagSquared = 0.0f
        for (i in 0 - mPeakMargin until (1 + mPeakMargin)) {
            val bin = signalBin + i
            signalMagSquared += magnitudeSquared(buffer[bin], mImaginary!![bin])
        }
        signalMagSquared = VERY_SMALL_NUMBER.coerceAtLeast(signalMagSquared)

        // Calculate Total Harmonic Distortion (THD)
        var totalHarmonicsMagSquared = 0.0f
        val limit = numFrames / (2 * signalBin)
        for (harmonicScaler in 2 until limit) {
            for (i in (0 - mPeakMargin) until (1 + mPeakMargin)) {
                val bin = (signalBin * harmonicScaler) + i
                totalHarmonicsMagSquared += magnitudeSquared(buffer[bin], mImaginary!![bin])
            }
        }

        result.totalHarmonicDistortion = sqrt((totalHarmonicsMagSquared / signalMagSquared).toDouble())

        // Calculate Total Harmonic Distortion plus Noise (THD+N)
        var totalMagSquared = 0.0f
        // Ignore 0th bin because there may be DC offset
        // Consider weighting by ITU-R (CCIR) 468 curve or A-weighting.
        for (i in 1 until (numFrames / 2)) {
            totalMagSquared += magnitudeSquared(buffer[i], mImaginary!![i])
        }
        var noiseMagSquared = totalMagSquared - signalMagSquared
        if (noiseMagSquared < VERY_SMALL_NUMBER) noiseMagSquared = VERY_SMALL_NUMBER
        result.totalHarmonicDistortionPlusNoise =
                sqrt((noiseMagSquared / signalMagSquared).toDouble())

        // Calculate Signal To Noise Ratio in dB
        val signalNoisePowerRatio = signalMagSquared / noiseMagSquared
        result.signalNoiseRatioDB = powerToDecibels(signalNoisePowerRatio.toDouble())

        var peakMagSquared = VERY_SMALL_NUMBER
        for (i in 0 until numFrames / 2) {
            peakMagSquared = max(peakMagSquared, magnitudeSquared(buffer[i], mImaginary!![i]))
        }
        result.bins = FloatArray(numFrames / 2)
        for (i in 0 until numFrames / 2) {
            result.bins!![i] = sqrt(magnitudeSquared(buffer[i],
                    mImaginary!![i]) / peakMagSquared)
        }
        return result
    }

    private fun magnitude(real: Float, imag: Float): Float {
        return sqrt(magnitudeSquared(real, imag).toDouble()).toFloat()
    }

    private fun magnitudeSquared(real: Float, imag: Float): Float {
        return (real * real) + (imag * imag)
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