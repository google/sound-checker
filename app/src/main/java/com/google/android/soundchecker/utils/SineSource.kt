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

import android.util.Log
import com.google.android.soundchecker.mediacodec.AudioEncoderSource
import java.util.Arrays
import kotlin.math.sin

class SineSource : AudioSource() {
    private val mFrequencyPort = FrequencyControlPort("frequency", 100.0f, 440.0f, 6000.0f)
    private val mAmplitudePort = ControlPort("amplitude", 0.0f, 0.1f, 1.0f)
    private var mPhase = 0.0
    private var mPhaseIncrement = 0.0
    private var mShouldUpdateFrequency = false
    private var mIsFrequencyIncreasing = true
    private var mInverseSampleRate = 1.0f
    private var mLastSampleRate = 1

    companion object {
        private const val TAG = "SineSource"
        private const val FREQUENCY_INCREMENT_AMOUNT = 1.0001f;
        private const val INVERSE_FREQUENCY_INCREMENT_AMOUNT  = 1 / FREQUENCY_INCREMENT_AMOUNT;
    }

    fun getFrequencyPort(): ControlPort {
        return mFrequencyPort
    }

    fun getAmplitudePort(): ControlPort {
        return mAmplitudePort
    }

    fun enableSineSweep() {
        mShouldUpdateFrequency = true;
    }

    fun disableSineSweep() {
        mShouldUpdateFrequency = false;
    }

    override fun render(buffer: FloatArray, offset: Int, stride: Int, numFrames: Int): Int {
        var index = offset
        for (i in 0 until numFrames) {
            val sineValue = sin(mPhase).toFloat()
            buffer[index] = sineValue * mAmplitudePort.get()
            index += stride
            mPhase += mPhaseIncrement
            if (mPhase > Math.PI) {
                mPhase -= Math.PI * 2.0
            }
            if (mShouldUpdateFrequency) {
                if (mIsFrequencyIncreasing) {
                    mFrequencyPort.set(mFrequencyPort.get() * FREQUENCY_INCREMENT_AMOUNT)
                } else {
                    mFrequencyPort.set(mFrequencyPort.get() * INVERSE_FREQUENCY_INCREMENT_AMOUNT)
                }
                if (mFrequencyPort.get() >= mFrequencyPort.mMaximum) {
                    mFrequencyPort.set(mFrequencyPort.mMaximum)
                    mIsFrequencyIncreasing = false
                }
                if (mFrequencyPort.get() <= mFrequencyPort.mMinimum) {
                    mFrequencyPort.set(mFrequencyPort.mMinimum)
                    mIsFrequencyIncreasing = true
                }
            }
        }
        return numFrames
    }

    fun updatePhaseIncrement(frequency: Float) {
        if (mLastSampleRate != mSampleRate) {
            mLastSampleRate = mSampleRate
            mInverseSampleRate = 1.0f / mLastSampleRate
        }
        //Log.d(TAG, "mLastSampleRate: " + mLastSampleRate + " mInverseSampleRate: " +
        //        mInverseSampleRate + " mPhaseIncrement: " + mPhaseIncrement)
        mPhaseIncrement = frequency * 2.0 * Math.PI * mInverseSampleRate
    }

    override fun pull(buffer: ByteArray, numFrames: Int): Int {
        val floatArray = FloatArray(numFrames * getChannelCount())
        pull(floatArray, numFrames)
        //Log.d(TAG, "floatArray: " + Arrays.toString(floatArray))
        floatArrayToByteArray(floatArray, buffer)
        //Log.d(TAG, "byteArray: " + Arrays.toString(buffer))
        return numFrames
    }

    // Pull I16 bytes
    override fun pull(numBytes: Int, buffer: ByteArray): Int {
        val floatArray = FloatArray(numBytes / 2)
        pull(floatArray, numBytes / 2)
        //Log.d(TAG, "floatArray: " + Arrays.toString(floatArray))
        floatArrayToI16ByteArray(floatArray, buffer)
        //Log.d(TAG, "byteArray: " + Arrays.toString(buffer))
        return numBytes / 2
    }

    override fun pull(buffer: FloatArray, numFrames: Int): Int {
        for (i in 0 until getChannelCount()) {
            render(buffer, 0, getChannelCount(), numFrames)
        }
        return numFrames
    }

    inner class FrequencyControlPort(name: String, minimum: Float, current: Float, maximum: Float)
        : ControlPort(name, minimum, current, maximum) {
        override fun set(value: Float) {
            super.set(value)
            updatePhaseIncrement(value)
        }
    }
}
