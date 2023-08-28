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

import kotlin.math.sin
import kotlin.math.sqrt

object FastFourierTransform {
    private const val MAX_SIZE_LOG_2 = 16
    private var reverseTables = arrayOfNulls<BitReverseTable>(MAX_SIZE_LOG_2)
    private var floatSineTables = arrayOfNulls<FloatSineTable>(MAX_SIZE_LOG_2)

    private fun getFloatSineTable(n: Int): FloatArray {
        var sineTable: FloatSineTable? = floatSineTables[n]
        if (sineTable == null) {
            sineTable = FloatSineTable(n)
            floatSineTables[n] = sineTable
        }
        return sineTable.sineValues
    }

    private fun getReverseTable(n: Int): IntArray {
        var reverseTable: BitReverseTable? = reverseTables[n]
        if (reverseTable == null) {
            reverseTable = BitReverseTable(n)
            reverseTables[n] = reverseTable
        }
        return reverseTable.reversedBits
    }

    /**
     * Calculate the amplitude of the sine wave associated with each bin of a complex FFT result.
     *
     * @param ar
     * @param ai
     * @param magnitudes
     */
    fun calculateMagnitudes(ar: FloatArray, ai: FloatArray, magnitudes: FloatArray) {
        for (i in magnitudes.indices) {
            magnitudes[i] = sqrt((ar[i] * ar[i] + ai[i] * ai[i]).toDouble()).toFloat()
        }
    }

    private fun transform(sign: Int, n: Int, ar: FloatArray, ai: FloatArray) {
        val scale = if (sign > 0) 2.0f / n else 0.5f
        val numBits: Int = numBits(n)
        val reverseTable: IntArray = getReverseTable(numBits)
        val sineTable: FloatArray = getFloatSineTable(numBits)
        val mask = n - 1
        val cosineOffset = n / 4 // phase offset between cos and sin
        var i: Int
        var j: Int
        i = 0
        while (i < n) {
            j = reverseTable[i]
            if (j >= i) {
                val tempr = ar[j] * scale
                val tempi = ai[j] * scale
                ar[j] = ar[i] * scale
                ai[j] = ai[i] * scale
                ar[i] = tempr
                ai[i] = tempi
            }
            i++
        }
        var mmax: Int
        var stride: Int
        val numerator = sign * n
        mmax = 1
        stride = 2 * mmax
        while (mmax < n) {
            var phase = 0
            val phaseIncrement = numerator / (2 * mmax)
            for (m in 0 until mmax) {
                val wr = sineTable[(phase + cosineOffset) and mask] // cosine
                val wi = sineTable[phase]
                i = m
                while (i < n) {
                    j = i + mmax
                    val tr = wr * ar[j] - wi * ai[j]
                    val ti = wr * ai[j] + wi * ar[j]
                    ar[j] = ar[i] - tr
                    ai[j] = ai[i] - ti
                    ar[i] += tr
                    ai[i] += ti
                    i += stride
                }
                phase = (phase + phaseIncrement) and mask
            }
            mmax = stride
            stride = 2 * mmax
        }
    }

    /**
     * Calculate log2(n)
     *
     * @param powerOf2 must be a power of two, for example 512 or 1024
     * @return for example, 9 for an input value of 512
     */
    private fun numBits(powerOf2: Int): Int {
        assert(
                powerOf2 and (powerOf2 - 1) == 0 // is it a power of 2?
        )
        var powerOf2 = powerOf2
        var i = -1
        while (powerOf2 > 0) {
            powerOf2 = powerOf2 shr 1
            i++
        }
        return i
    }

    /**
     * Calculate an FFT in place, modifying the input arrays.
     *
     * @param n
     * @param ar
     * @param ai
     */
    fun fft(n: Int, ar: FloatArray, ai: FloatArray) {
        transform(1, n, ar, ai)
    }

    /**
     * Calculate an inverse FFT in place, modifying the input arrays.
     *
     * @param n
     * @param ar
     * @param ai
     */
    fun ifft(n: Int, ar: FloatArray, ai: FloatArray) {
        transform(-1, n, ar, ai)
    }

    private class FloatSineTable(numBits: Int) {
        var sineValues: FloatArray

        init {
            val len = 1 shl numBits
            sineValues = FloatArray(len)
            for (i in 0 until len) {
                sineValues[i] = sin(i * Math.PI * 2.0 / len).toFloat()
            }
        }
    }

    private class BitReverseTable(numBits: Int) {
        var reversedBits: IntArray

        init {
            reversedBits = IntArray(1 shl numBits)
            for (i in reversedBits.indices) {
                reversedBits[i] = reverseBits(i, numBits)
            }
        }

        companion object {
            fun reverseBits(index: Int, numBits: Int): Int {
                var idx = index
                var rev = 0
                for (i in 0 until numBits) {
                    rev = (rev shl 1) or (idx and 1)
                    idx = (idx shr 1)
                }
                return rev
            }
        }
    }
}
