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

import android.media.AudioFormat

open class AudioEndpoint(val isInput: Boolean, val errorCallback: AudioErrorCallback? = null) {
    var mChannelCount = 1
    var mChannelMask = AudioFormat.CHANNEL_INVALID
    var mEncoding = AudioFormat.ENCODING_DEFAULT
    var mSampleRate = 48000

    fun getChannelCount(): Int {
        return if (mChannelMask == AudioFormat.CHANNEL_INVALID) mChannelCount
        else mChannelMask.getChannelCountFromMask()
    }

    fun getBytesPerSample(): Int {
        return mEncoding.bytesPerSample()
    }

    fun getBytesPerFrame(): Int {
        return getChannelCount() * getBytesPerSample()
    }

    @Throws(IllegalArgumentException::class)
    fun getAudioFormat(): AudioFormat {
        val builder = AudioFormat.Builder().setEncoding(mEncoding)
        if (mSampleRate != 0) {
            builder.setSampleRate(mSampleRate)
        }
        if (mChannelMask != AudioFormat.CHANNEL_INVALID) {
            builder.setChannelMask(mChannelMask)
        } else if (mChannelCount != 0) {
            // Prefer position mask if the channel count is smaller than 2
            when (mChannelCount) {
                1 -> {
                    builder.setChannelMask(if (isInput) AudioFormat.CHANNEL_IN_MONO
                    else AudioFormat.CHANNEL_OUT_MONO)
                }

                2 -> {
                    builder.setChannelMask(if (isInput) AudioFormat.CHANNEL_IN_STEREO
                    else AudioFormat.CHANNEL_OUT_STEREO)
                }

                else -> {
                    builder.setChannelIndexMask(mChannelCount.generateChannelIndexMask())
                }
            }
        }
        return builder.build()
    }
}
