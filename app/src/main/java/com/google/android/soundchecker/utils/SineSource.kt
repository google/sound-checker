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

class SineSource() : AudioSource() {
    private val mFequencyPort = FrequencyControlPort("frequency", 100.0f, 440.0f, 6000.0f)
    private val mAmplitudePort = ControlPort("amplitude", 0.0f, 0.1f, 1.0f)
    private var mPhase = 0.0
    private var mPhaseIncrement = 0.0

    fun getFrequencyPort(): ControlPort {
        return mFequencyPort
    }

    fun getAmplitudePort(): ControlPort {
        return mAmplitudePort
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
        }
        return numFrames
    }

    fun updatePhaseIncrement(frequency: Float) {
        mPhaseIncrement = frequency * 2.0 * Math.PI / mSampleRate
    }

    override fun pull(buffer: ByteArray, numFrames: Int): Int {
        val floatArray = FloatArray(numFrames * getChannelCount())
        pull(floatArray, numFrames)
        Helpers.floatArrayToByteArray(floatArray, buffer)
        return numFrames
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
