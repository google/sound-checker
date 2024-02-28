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

package com.google.android.soundchecker.mediacodec

import android.media.AudioDeviceInfo
import android.media.AudioFormat
import com.google.android.soundchecker.harmonicanalyzer.HarmonicAnalyzerListener
import com.google.android.soundchecker.harmonicanalyzer.HarmonicAnalyzerSink

import com.google.android.soundchecker.utils.AudioRecordSource
import com.google.android.soundchecker.utils.AudioTrackSink
import com.google.android.soundchecker.utils.SineSource
import com.google.android.soundchecker.utils.highestChannelCount

class AudioEncoderDecoderFramework (val codec: String, val sampleRate: Int, val channelCount: Int,
                                    val bitRate: Int, val flacCompressionLevel: Int, val
                                    pcmEncoding: Int) {
    private var mSineSource = SineSource()
    private var mAudioEncoderSource = AudioEncoderSource(codec, sampleRate, channelCount,
        bitRate, flacCompressionLevel, pcmEncoding)
    private var mAudioDecoderSource = AudioDecoderSource()
    var harmonicAnalyzerSink = AudioEncoderDecoderHarmonicAnalyzerSink()
    private var mSineFrequency = 1000.0 // overwrite this with bin frequency

    init {
        // Output
        mSineSource.getAmplitudePort().set(0.5f)
        mSineSource.mSampleRate = sampleRate
        mAudioEncoderSource.setSource(mSineSource)
        mAudioDecoderSource.setSource(mAudioEncoderSource)
        mAudioEncoderSource.setDecoder(mAudioDecoderSource)
        harmonicAnalyzerSink.setSource(mAudioDecoderSource)
    }

    fun start() {
        val bin: Int = harmonicAnalyzerSink.mFundamentalBin
        mSineFrequency = harmonicAnalyzerSink.calculateBinFrequency(bin)
        mSineSource.getFrequencyPort().set(mSineFrequency.toFloat())
        mAudioEncoderSource.start()
        val outputPcmEncoding = mAudioEncoderSource.getOutputPcmEncoding()
        //mAudioDecoderSource.start()
        harmonicAnalyzerSink.setOutputPcmEncoding(outputPcmEncoding)
        harmonicAnalyzerSink.start()
    }

    fun stop() {
        harmonicAnalyzerSink.stop()
        mAudioDecoderSource.stop()
        mAudioEncoderSource.stop()
    }

    fun addListener(listener: HarmonicAnalyzerListener) {
        harmonicAnalyzerSink.addListener(listener)
    }
}