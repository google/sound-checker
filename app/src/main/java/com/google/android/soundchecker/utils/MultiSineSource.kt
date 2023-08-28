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

class MultiSineSource : AudioSource() {

    private var mSineSources = ArrayList<SineSource>()

    fun addPartial(min: Float, current: Float, max: Float) {
        val sineSource = SineSource()
        sineSource.getFrequencyPort().mMinimum = min
        sineSource.getFrequencyPort().mMaximum = max
        sineSource.getFrequencyPort().set(current)
        mSineSources.add(sineSource)
    }

    fun getFrequencyPort(i: Int): ControlPort {
        return mSineSources[i].getFrequencyPort()
    }

    fun getAmplitudePort(i: Int): ControlPort {
        return mSineSources[i].getAmplitudePort()
    }

    fun size(): Int {
        return mSineSources.size
    }

    override fun render(buffer: FloatArray, offset: Int, stride: Int, numFrames: Int): Int {
        if (mTemporaryBuffer == null || mTemporaryBuffer!!.size < numFrames) {
            mTemporaryBuffer = FloatArray(numFrames)
        }
        // Clear for mixing.
        for (i in 0 until numFrames * stride) {
            buffer[i] = 0.0f
        }
        for (sine in mSineSources) {
            sine.render(mTemporaryBuffer!!, 0, 1, numFrames)
            // Mix into the output buffer.
            var index = offset
            for (i in 0 until numFrames) {
                buffer[index] += mTemporaryBuffer!![i]
                index += stride
            }
        }
        return numFrames
    }

    override fun pull(buffer: ByteArray, numFrames: Int): Int {
        val floatArray = FloatArray(numFrames)
        pull(floatArray, numFrames)
        Helpers.floatArrayToByteArray(floatArray, buffer)
        return numFrames
    }

    override fun pull(buffer: FloatArray, numFrames: Int): Int {
        render(buffer, 0, getChannelCount(), numFrames)
        return numFrames
    }

    var mTemporaryBuffer: FloatArray? = null
}