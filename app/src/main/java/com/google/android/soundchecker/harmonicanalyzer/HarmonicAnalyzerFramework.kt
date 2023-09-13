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

import android.media.AudioDeviceInfo
import android.media.AudioFormat

import com.google.android.soundchecker.utils.AudioRecordSource
import com.google.android.soundchecker.utils.AudioTrackSink
import com.google.android.soundchecker.utils.SineSource
import com.google.android.soundchecker.utils.highestChannelCount

class HarmonicAnalyzerFramework {
    private var mSineSource = SineSource()
    private var mAudioTrackSink = AudioTrackSink()
    private var mAudioRecordSource = AudioRecordSource()
    var harmonicAnalyzerSink = HarmonicAnalyzerSink()
    private var mSineFrequency = 1000.0 // overwrite this with bin frequency

    init {
        // Output
        mSineSource.getAmplitudePort().set(0.5f)
        mSineSource.connect(mAudioTrackSink)
        // Input
        mAudioRecordSource.connect(harmonicAnalyzerSink)
        mAudioRecordSource.selectedChannelIndex = 0
    }

    fun start() {
        val bin: Int = harmonicAnalyzerSink.mFundamentalBin
        mSineFrequency = harmonicAnalyzerSink.calculateBinFrequency(bin)
        mSineSource.getFrequencyPort().set(mSineFrequency.toFloat())
        mAudioTrackSink.start()
        mAudioRecordSource.start()
        harmonicAnalyzerSink.start()
    }

    fun stop() {
        harmonicAnalyzerSink.stop()
        mAudioRecordSource.stop()
        mAudioTrackSink.stop()
    }

    fun addListener(listener: HarmonicAnalyzerListener?) {
        harmonicAnalyzerSink.addListener(listener!!)
    }

    fun setInputDevice(inputDevice: AudioDeviceInfo?) {
        mAudioRecordSource.preferredDevice = inputDevice
        if (inputDevice == null) {
            mAudioRecordSource.mChannelMask = AudioFormat.CHANNEL_IN_STEREO
        } else {
            mAudioRecordSource.mChannelCount = inputDevice.highestChannelCount()
        }
    }

    fun setOutputDevice(outputDevice: AudioDeviceInfo?) {
        mAudioTrackSink.preferredDevice = outputDevice
        if (outputDevice == null) {
            mAudioTrackSink.mChannelMask = AudioFormat.CHANNEL_OUT_STEREO
        } else {
            mAudioTrackSink.mChannelCount = outputDevice.highestChannelCount()
            mSineSource.mChannelCount = mAudioTrackSink.mChannelCount
        }
    }

    val routedInputDevice: AudioDeviceInfo
        get() = mAudioRecordSource.routedDevice!!

    fun setInputChannelIndex(channelIndex: Int) {
        mAudioRecordSource.selectedChannelIndex = channelIndex
    }

    fun setOutputChannelIndex(channelIndex: Int) {
        mAudioTrackSink.selectedChannelIndex = channelIndex
    }
}