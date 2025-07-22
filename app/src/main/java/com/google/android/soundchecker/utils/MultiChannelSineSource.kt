/**
 * Copyright 2025 Google LLC
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

import android.media.MediaCodec

/**
 * Multi channel sine source where each channel has its own frequency and amplitude.
 * When pulling from this source, the channels will be rendered in order and not combined.
 */
class MultiChannelSineSource : AudioSource() {

    private var mSineSources = ArrayList<SineSource>()

    fun addPartial(min: Float, current: Float, max: Float, enableSineSweep: Boolean = false) {
        val sineSource = SineSource()
        sineSource.getFrequencyPort().mMinimum = min
        sineSource.getFrequencyPort().mMaximum = max
        sineSource.getFrequencyPort().set(current)
        if (enableSineSweep) {
            sineSource.enableSineSweep()
        }
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
        for (channel in 0 until mChannelCount) {
            mSineSources[channel].render(buffer, channel, mChannelCount, numFrames)
        }
        return numFrames
    }

    override fun pull(buffer: ByteArray, numFrames: Int): MediaCodec.BufferInfo {
        val floatArray = FloatArray(numFrames * getChannelCount())
        pull(floatArray, numFrames)
        floatArrayToByteArray(floatArray, buffer)
        val bufferInfo = MediaCodec.BufferInfo()
        bufferInfo.set(0, numFrames, 0, 0)
        return bufferInfo
    }

    // Pull I16 bytes
    override fun pull(numBytes: Int, buffer: ByteArray): MediaCodec.BufferInfo {
        val floatArray = FloatArray(numBytes * Short.SIZE_BYTES / Float.SIZE_BYTES)
        pull(floatArray, numBytes * Short.SIZE_BYTES / Float.SIZE_BYTES / getChannelCount())
        floatArrayToI16ByteArray(floatArray, buffer)
        val bufferInfo = MediaCodec.BufferInfo()
        bufferInfo.set(0, numBytes / 2 / getChannelCount(), 0, 0)
        return bufferInfo
    }

    override fun pull(buffer: FloatArray, numFrames: Int): Int {
        render(buffer, 0, getChannelCount(), numFrames)
        return numFrames
    }

    companion object {
        private val TAG = "MultiSinSource"
    }
}
