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

import android.media.MediaMuxer
import com.google.android.soundchecker.harmonicanalyzer.HarmonicAnalyzerListener
import com.google.android.soundchecker.mediacodec.AudioEncoderDecoderSink.Companion.TARGET_FREQUENCY
import com.google.android.soundchecker.utils.MultiChannelSineSource
import com.google.android.soundchecker.utils.MultiSineSource

import com.google.android.soundchecker.utils.SineSource
import com.google.android.soundchecker.utils.WaveFileReader
import com.google.android.soundchecker.utils.WaveFileSource
import java.io.File

class AudioEncoderDecoderFramework (codec: String, codecFormat: String, sampleRate: Int,
                                    channelCount: Int, bitRate: Int, flacCompressionLevel: Int,
                                    aacProfile: Int, pcmEncoding: Int, usePitchSweep: Boolean,
                                    outputFile: File, reader: WaveFileReader?,
                                    encodedDataMediaMuxer: MediaMuxer?) {
    private var mSineSource = MultiChannelSineSource()
    private var mWaveFileSource = WaveFileSource()
    private var mAudioEncoderSource = AudioEncoderSource(codec, codecFormat, sampleRate,
        channelCount, bitRate, flacCompressionLevel, aacProfile, pcmEncoding,
        encodedDataMediaMuxer)
    private var mAudioDecoderSource = AudioDecoderSource()
    var harmonicAnalyzerSink = AudioEncoderDecoderSink(outputFile)
    private var mSineFrequencies = ArrayList<Float>()

    init {
        harmonicAnalyzerSink.mSampleRate = sampleRate
        val fundamentalBins = IntArray(channelCount)
        for (channel in 0 until channelCount) {
            fundamentalBins[channel] = harmonicAnalyzerSink.calculateNearestBin(TARGET_FREQUENCY * (channel + 1))
        }
        if (reader != null) {
            mWaveFileSource.waveFileReader = reader
            mWaveFileSource.mChannelCount = channelCount
            mAudioEncoderSource.setSource(mWaveFileSource)
        } else {
            mSineSource.mSampleRate = sampleRate
            mSineSource.mChannelCount = channelCount
            for (channel in 0 until channelCount) {
                if (usePitchSweep) {
                    mSineSource.addPartial(sampleRate / 1000.0F, sampleRate * (channel + 1) / 10.0F, sampleRate / 2.3F, true)
                    mSineSource.getAmplitudePort(channel).set(0.5f)
                } else {
                    val sineFrequency = harmonicAnalyzerSink.calculateBinFrequency(fundamentalBins[channel]).toFloat()
                    mSineFrequencies.add(sineFrequency)
                    mSineSource.addPartial(sampleRate / 1000.0F, sineFrequency, sampleRate / 2.3F, false)
                    mSineSource.getAmplitudePort(channel).set(0.5f)
                }
            }
            mAudioEncoderSource.setSource(mSineSource)
        }
        mAudioDecoderSource.setSource(mAudioEncoderSource)
        mAudioEncoderSource.setDecoder(mAudioDecoderSource)
        harmonicAnalyzerSink.setSource(mAudioDecoderSource)
        mAudioDecoderSource.setHarmonicAnalyzer(harmonicAnalyzerSink)
        harmonicAnalyzerSink.mChannelCount = channelCount
        harmonicAnalyzerSink.mFundamentalBins = fundamentalBins
        harmonicAnalyzerSink.mUseAnalyzer = (reader == null)
        harmonicAnalyzerSink.mUseFundamentalBin = (reader == null) && !usePitchSweep
    }

    fun start() {
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
