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

package com.google.android.soundchecker

import android.Manifest
import android.content.pm.PackageManager
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioFormat
import android.media.AudioManager
import android.media.MediaFormat
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Divider
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

import kotlin.math.roundToInt
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update

import com.google.android.soundchecker.harmonicanalyzer.DevicePicker
import com.google.android.soundchecker.harmonicanalyzer.HarmonicAnalyzer
import com.google.android.soundchecker.harmonicanalyzer.HarmonicAnalyzer.Companion.amplitudeToDecibels
import com.google.android.soundchecker.harmonicanalyzer.HarmonicAnalyzerFramework
import com.google.android.soundchecker.harmonicanalyzer.HarmonicAnalyzerListener
import com.google.android.soundchecker.mediacodec.AudioEncoderDecoderFramework
import com.google.android.soundchecker.utils.deviceDisplayName
import com.google.android.soundchecker.utils.ui.AudioDeviceListEntry
import com.google.android.soundchecker.utils.ui.WaveformDisplay


class AudioEncoderDecoderActivity : ComponentActivity() {
    companion object {
        const val TAG = "AudioEncoderDecoderActivity"

        private const val FFT_SIZE = 1024
        private const val AVERAGE_SIZE = 24
        private val FREQUENCY = 1000
        private val AUDIO_CODECS = listOf(
            MediaFormat.MIMETYPE_AUDIO_AAC, MediaFormat.MIMETYPE_AUDIO_OPUS, MediaFormat.MIMETYPE_AUDIO_AMR_NB,
            MediaFormat.MIMETYPE_AUDIO_AMR_WB, MediaFormat.MIMETYPE_AUDIO_FLAC)
        private val SAMPLE_RATES = listOf(8000, 16000, 32000, 44100, 48000, 96000, 192000)
        private val CHANNEL_COUNT = 1
        private val BITRATES = listOf(6000, 10000, 20000, 64000, 128000)
        private val FLAC_COMPRESSION_LEVELS = (0..8).toList()
        private val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_FLOAT

        private const val MIN_DECIBELS = -160F

        private fun calculateBinFrequency(bin: Int, sampleRate: Int): Double {
            return sampleRate.toDouble() * bin / FFT_SIZE
        }

        private fun calculateNearestBin(frequency: Double, sampleRate: Int): Int {
            return (FFT_SIZE * frequency / sampleRate).roundToInt()
        }
    }

    private var mStartButtonEnabled = mutableStateOf(true)
    private var mStopButtonEnabled = mutableStateOf(false)

    private var mParam = mutableStateOf("")
    private var mStatus = mutableStateOf("")
    private var mUseLogDisplay = mutableStateOf(true)
    private var mBins: FloatArray? = null

    private var mSpinnersEnabled = mutableStateOf(true)
    private var mSampleRateText = mutableStateOf("")
    private var mBitrateText = mutableStateOf("")
    private var mFlacCompressionLevelText = mutableStateOf("")
    private var mAudioCodecText = mutableStateOf("")

    private var mAudioEncoderDecoderFramework: AudioEncoderDecoderFramework? = null
    private val mListener: MyHarmonicAnalyzerListener = MyHarmonicAnalyzerListener()
    private var mAudioCodec = ""
    private var mSampleRate = 0;
    private var mBitrate = 0;
    private var mFlacCompressionLevel = 0;

    // Display average value so it does not jump around so much.
    private var mSumPeakAmplitude = 0.0
    private var mSumTHD = 0.0
    private var mSumTHDN = 0.0
    private var mSumSNR = 0.0
    private var mAverageCount = 0
    private var mSumBins: FloatArray? = null
    private var mMinGraphValue = 0F
    private var mMaxGraphValue = 1F

    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        initSpinnerValues()
        setContent {
            Scaffold(
                    topBar = {
                        TopAppBar(
                                title = { Text(title.toString()) },
                                modifier = Modifier.shadow(elevation = 4.dp)
                        )
                    }
            ) { paddingValues ->
                Column(
                        modifier = Modifier
                            .padding(paddingValues = paddingValues)
                            .verticalScroll(rememberScrollState())) {
                    Divider(color = Color.Gray, thickness = 1.dp)
                    Row {
                        Text(text = "Audio Codec")
                        Spacer(modifier = Modifier.padding(4.dp))
                        var expanded by remember { mutableStateOf(false) }
                        Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .wrapContentSize(Alignment.CenterEnd)
                        ) {
                            // Create the dropdown menu
                            DropdownMenu(
                                    expanded = expanded,
                                    onDismissRequest = { expanded = false },
                                    modifier = Modifier.fillMaxWidth()
                            ) {
                                AUDIO_CODECS.forEachIndexed { index, bin ->
                                    DropdownMenuItem(
                                            text = { Text(bin) },
                                            onClick = {
                                                mAudioCodec = AUDIO_CODECS[index]
                                                mAudioCodecText.value = AUDIO_CODECS[index]
                                                expanded = false
                                            },
                                            enabled = mSpinnersEnabled.value
                                    )
                                }
                            }
                            Text(
                                    text = mAudioCodecText.value, modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable(onClick = { expanded = true })
                                    .background(
                                        Color.Gray
                                    )
                            )
                        }
                    }
                    Spacer(modifier = Modifier.padding(4.dp))
                    Row {
                        Text(text = "Sample Rate (Hz)")
                        Spacer(modifier = Modifier.padding(4.dp))
                        var expanded by remember { mutableStateOf(false) }
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .wrapContentSize(Alignment.CenterEnd)
                        ) {
                            // Create the dropdown menu
                            DropdownMenu(
                                expanded = expanded,
                                onDismissRequest = { expanded = false },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                SAMPLE_RATES.forEachIndexed { index, bin ->
                                    DropdownMenuItem(
                                        text = { Text(bin.toString()) },
                                        onClick = {
                                            mSampleRate = SAMPLE_RATES[index]
                                            mSampleRateText.value = SAMPLE_RATES[index].toString()
                                            expanded = false
                                        },
                                        enabled = mSpinnersEnabled.value
                                    )
                                }
                            }
                            Text(
                                text = mSampleRateText.value, modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable(onClick = { expanded = true })
                                    .background(
                                        Color.Gray
                                    )
                            )
                        }
                    }
                    Spacer(modifier = Modifier.padding(4.dp))
                    Row {
                        Text(text = "Bitrate (bits/s)")
                        Spacer(modifier = Modifier.padding(4.dp))
                        var expanded by remember { mutableStateOf(false) }
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .wrapContentSize(Alignment.CenterEnd)
                        ) {
                            // Create the dropdown menu
                            DropdownMenu(
                                expanded = expanded,
                                onDismissRequest = { expanded = false },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                BITRATES.forEachIndexed { index, bin ->
                                    DropdownMenuItem(
                                        text = { Text(bin.toString()) },
                                        onClick = {
                                            mBitrate = BITRATES[index]
                                            mBitrateText.value = BITRATES[index].toString()
                                            expanded = false
                                        },
                                        enabled = mSpinnersEnabled.value
                                    )
                                }
                            }
                            Text(
                                text = mBitrateText.value, modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable(onClick = { expanded = true })
                                    .background(
                                        Color.Gray
                                    )
                            )
                        }
                    }
                    Spacer(modifier = Modifier.padding(4.dp))
                    Row {
                        Text(text = "FLAC compression level")
                        Spacer(modifier = Modifier.padding(4.dp))
                        var expanded by remember { mutableStateOf(false) }
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .wrapContentSize(Alignment.CenterEnd)
                        ) {
                            // Create the dropdown menu
                            DropdownMenu(
                                expanded = expanded,
                                onDismissRequest = { expanded = false },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                FLAC_COMPRESSION_LEVELS.forEachIndexed { index, bin ->
                                    DropdownMenuItem(
                                        text = { Text(bin.toString()) },
                                        onClick = {
                                            mFlacCompressionLevel = FLAC_COMPRESSION_LEVELS[index]
                                            mFlacCompressionLevelText.value = FLAC_COMPRESSION_LEVELS[index].toString()
                                            expanded = false
                                        },
                                        enabled = mSpinnersEnabled.value
                                    )
                                }
                            }
                            Text(
                                text = mFlacCompressionLevelText.value, modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable(onClick = { expanded = true })
                                    .background(
                                        Color.Gray
                                    )
                            )
                        }
                    }
                    Spacer(modifier = Modifier.padding(4.dp))
                    Row {
                        Button(onClick = {
                            onStartTest()
                        },
                                enabled = mStartButtonEnabled.value) {
                            Text(text = "Start")
                        }
                        Button(onClick = {
                            onStopTest()
                        },
                                enabled = mStopButtonEnabled.value) {
                            Text(text = "Stop")
                        }
                    }
                    Text(text = mParam.value)
                    Spacer(modifier = Modifier.padding(4.dp))
                    Text(text = mStatus.value,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold)
                    WaveformDisplay(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(200.dp)
                            .border(1.dp, Color.Gray)
                            .background(Color.LightGray)
                            .padding(horizontal = 4.dp, vertical = 4.dp),
                        yValues = mBins,
                        yMax = mMaxGraphValue,
                        yMin = mMinGraphValue)
                    Row {
                        Text(text = "Use logarithmic display",
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.align(Alignment.CenterVertically))
                        Checkbox(
                            checked = mUseLogDisplay.value,
                            onCheckedChange = { mUseLogDisplay.value = it }
                        )
                    }
                }
            }
        }
    }

    private fun onStartTest() {
        runTest()
    }

    private fun runTest() {
        mStartButtonEnabled.value = false
        mStopButtonEnabled.value = true
        mSpinnersEnabled.value = false

        mAudioEncoderDecoderFramework = AudioEncoderDecoderFramework(mAudioCodec, mSampleRate,
            CHANNEL_COUNT, mBitrate, mFlacCompressionLevel, AUDIO_FORMAT)
        checkNotNull(mAudioEncoderDecoderFramework) {
            Toast.makeText(
                this,
                "Failed to init harmonic analyzer framework",
                Toast.LENGTH_LONG).show()
        }
        val harmonicAnalyzerSink = mAudioEncoderDecoderFramework?.harmonicAnalyzerSink
        checkNotNull(harmonicAnalyzerSink) {
            Toast.makeText(this, "Failed to init harmonic analyzer sink", Toast.LENGTH_LONG).show()
        }

        mAverageCount = 0
        mSumPeakAmplitude = 0.0
        mSumTHD = 0.0
        mSumTHDN = 0.0
        mSumSNR = 0.0
        mSumBins = null

        mAudioEncoderDecoderFramework?.addListener(mListener)

        harmonicAnalyzerSink.mSampleRate = mSampleRate
        harmonicAnalyzerSink.mFftSize = FFT_SIZE
        harmonicAnalyzerSink.mFundamentalBin = calculateBinFrequency().toInt()

        mParam.value = String.format("Sample Rate = %6d Hz\nFFT size = %d\nFundamental Bin = %d\n",
                harmonicAnalyzerSink.mSampleRate,
                harmonicAnalyzerSink.mFftSize,
                harmonicAnalyzerSink.mFundamentalBin)

        mAudioEncoderDecoderFramework?.start()
    }

    private fun onStopTest() {
        mStartButtonEnabled.value = true
        mStopButtonEnabled.value = false
        mSpinnersEnabled.value = true

        mAudioEncoderDecoderFramework?.stop()
    }

    private fun initSpinnerValues() {
        mAudioCodec = AudioEncoderDecoderActivity.AUDIO_CODECS[0]
        mAudioCodecText.value = AudioEncoderDecoderActivity.AUDIO_CODECS[0]
        mSampleRate = AudioEncoderDecoderActivity.SAMPLE_RATES[0]
        mSampleRateText.value = AudioEncoderDecoderActivity.SAMPLE_RATES[0].toString()
        mBitrate = AudioEncoderDecoderActivity.BITRATES[0]
        mBitrateText.value = AudioEncoderDecoderActivity.BITRATES[0].toString()
        mFlacCompressionLevel = AudioEncoderDecoderActivity.FLAC_COMPRESSION_LEVELS[0]
        mFlacCompressionLevelText.value = AudioEncoderDecoderActivity.FLAC_COMPRESSION_LEVELS[0].toString()
    }

    private fun calculateBinFrequency(): Double{
        return calculateBinFrequency(calculateNearestBin(FREQUENCY.toDouble(), mSampleRate),
            mSampleRate)
    }

    private inner class MyHarmonicAnalyzerListener : HarmonicAnalyzerListener {
        override fun onMeasurement(analysisCount: Int, result: HarmonicAnalyzer.Result) {
            mSumPeakAmplitude += result.peakAmplitude
            mSumTHD += result.totalHarmonicDistortion
            mSumTHDN += result.totalHarmonicDistortionPlusNoise
            mSumSNR += result.signalNoiseRatioDB
            if (mSumBins == null || mSumBins!!.size !=
                    result.bins!!.size) {
                mSumBins = result.bins
            } else {
                for (bucket in 0 until (result.bins!!.size)) {
                    mSumBins!![bucket] += result.bins!![bucket]
                }
            }
            mAverageCount++
            if (mAverageCount == AVERAGE_SIZE) {
                val averagePeakAmplitude = mSumPeakAmplitude / AVERAGE_SIZE
                val averagePeakDB = HarmonicAnalyzer.amplitudeToDecibels(averagePeakAmplitude)
                val averageTHD = mSumTHD / AVERAGE_SIZE
                val averageTHDN = mSumTHDN / AVERAGE_SIZE
                val averageSNR = mSumSNR / AVERAGE_SIZE
                mAverageCount = 0
                mSumPeakAmplitude = 0.0
                mSumTHD = 0.0
                mSumTHDN = 0.0
                mSumSNR = 0.0
                mStatus.value = """
                    analysis #%04d
                    THD   = %6.4f%c
                    THD+N = %6.4f%c
                    SNR   = %6.2f dB
                    nPeak  = %6.2f dB
                """.trimIndent().format(
                        analysisCount,
                        averageTHD * 100.0, '%',
                        averageTHDN * 100.0, '%',
                        averageSNR,
                        averagePeakDB)
                mBins = FloatArray(mSumBins!!.size)
                for (bucket in 0 until (mSumBins!!.size)) {
                    var avgValue = mSumBins!![bucket] / AVERAGE_SIZE
                    if (mUseLogDisplay.value) {
                        mBins!![bucket] = amplitudeToDecibels(avgValue
                            .toDouble()).toFloat()
                        mMinGraphValue = MIN_DECIBELS
                        mMaxGraphValue = 0F
                    } else {
                        mBins!![bucket] = avgValue
                        mMinGraphValue = 0F
                        mMaxGraphValue = 1F
                    }
                }
                mSumBins = null
            }
        }
    }
}
