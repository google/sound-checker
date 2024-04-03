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

import android.content.Intent
import android.media.AudioFormat
import android.media.MediaFormat
import android.os.Bundle
import android.os.Environment
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
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
import androidx.core.content.FileProvider
import com.google.android.soundchecker.harmonicanalyzer.HarmonicAnalyzer
import com.google.android.soundchecker.harmonicanalyzer.HarmonicAnalyzer.Companion
.amplitudeToDecibels
import com.google.android.soundchecker.harmonicanalyzer.HarmonicAnalyzerListener
import com.google.android.soundchecker.mediacodec.AudioEncoderDecoderFramework
import com.google.android.soundchecker.utils.remapToLog
import com.google.android.soundchecker.utils.ui.SpectogramDisplay
import com.google.android.soundchecker.utils.ui.WaveformDisplay
import java.io.File
import java.text.DateFormat
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import kotlin.math.roundToInt


class AudioEncoderDecoderActivity : ComponentActivity() {
    companion object {
        const val TAG = "AudioEncoderDecoderActivity"

        private const val FFT_SIZE = 1024
        private const val AVERAGE_SIZE = 1
        private val FREQUENCY = 1000
        private val AUDIO_CODECS = listOf(
            MediaFormat.MIMETYPE_AUDIO_AAC, MediaFormat.MIMETYPE_AUDIO_OPUS, MediaFormat.MIMETYPE_AUDIO_AMR_NB,
            MediaFormat.MIMETYPE_AUDIO_AMR_WB, MediaFormat.MIMETYPE_AUDIO_FLAC)
        private val SAMPLE_RATES = listOf(8000, 16000, 32000, 44100, 48000, 96000, 192000)
        private val CHANNEL_COUNT = 1
        private val BITRATES = listOf(6000, 10000, 20000, 64000, 128000)
        private val FLAC_COMPRESSION_LEVELS = (0..8).toList()
        private val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_FLOAT
        private val WAVEFORM_HEIGHT = 200
        private val SPECTOGRAM_WIDTH = 300

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
    private var mShareButtonEnabled = mutableStateOf(false)
    private var mPlaySineSweep = mutableStateOf(false)

    private var mParam = mutableStateOf("")
    private var mStatus = mutableStateOf("")
    private var mBins: FloatArray? = null
    private var mSpectogram: MutableList<FloatArray?>? = null
    private var mLastOutputBuffer: FloatArray? = null

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

    private var mFile: File? = null

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
                        Text(text = "Play sine sweep",
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.align(Alignment.CenterVertically))
                        Checkbox(
                            checked = mPlaySineSweep.value,
                            onCheckedChange = { mPlaySineSweep.value = it },
                            enabled = mSpinnersEnabled.value
                        )
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
                        Button(onClick = {
                            onShareResults()
                        },
                            enabled = mShareButtonEnabled.value) {
                            Text(text = "Share")
                        }
                    }
                    Spacer(modifier = Modifier.padding(4.dp))
                    Text(text = mParam.value)
                    Spacer(modifier = Modifier.padding(4.dp))
                    Text(text = mStatus.value,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold)
                    WaveformDisplay(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(WAVEFORM_HEIGHT.dp)
                            .border(1.dp, Color.Gray)
                            .background(Color.Green)
                            .padding(horizontal = 4.dp, vertical = 4.dp),
                        yValues = mLastOutputBuffer,
                        yMin = -1.0f,
                        yMax = 1.0f)
                    WaveformDisplay(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(WAVEFORM_HEIGHT.dp)
                            .border(1.dp, Color.Gray)
                            .background(Color.LightGray)
                            .padding(horizontal = 4.dp, vertical = 4.dp),
                        yValues = mBins,
                        yMin = MIN_DECIBELS,
                        yMax = 0.0f)
                    SpectogramDisplay(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(WAVEFORM_HEIGHT.dp)
                            .border(1.dp, Color.Gray)
                            .background(Color.LightGray)
                            .padding(horizontal = 4.dp, vertical = 4.dp),
                        values = mSpectogram,
                        min = MIN_DECIBELS,
                        max = 0.0f)
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
        mShareButtonEnabled.value = false
        mSpinnersEnabled.value = false

        mFile = createFileName()

        try {
            mAudioEncoderDecoderFramework = AudioEncoderDecoderFramework(
                mAudioCodec, mSampleRate,
                CHANNEL_COUNT, mBitrate, mFlacCompressionLevel, AUDIO_FORMAT, mPlaySineSweep.value,
                mFile!!
            )
        } catch (e: Exception) {
            Toast.makeText(
                this,
                "Params not supported " + e,
                Toast.LENGTH_LONG).show()
            mStartButtonEnabled.value = true
            mStopButtonEnabled.value = false
            mSpinnersEnabled.value = true
            return
        }
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

        mLastOutputBuffer = null
        mBins = null
        mSpectogram = null
        mStatus.value = ""

        mAudioEncoderDecoderFramework?.addListener(mListener)

        harmonicAnalyzerSink.mSampleRate = mSampleRate
        harmonicAnalyzerSink.mFftSize = FFT_SIZE
        if (mPlaySineSweep.value) {
            harmonicAnalyzerSink.mFundamentalBin = 0
        } else {
            harmonicAnalyzerSink.mFundamentalBin = calculateBinFrequency().toInt()
        }

        mParam.value = String.format("Sample Rate = %6d Hz\nFFT size = %d\nFundamental Bin = %d\n",
                harmonicAnalyzerSink.mSampleRate,
                harmonicAnalyzerSink.mFftSize,
                harmonicAnalyzerSink.mFundamentalBin)

        mAudioEncoderDecoderFramework?.start()
    }

    private fun onStopTest() {
        mStartButtonEnabled.value = true
        mStopButtonEnabled.value = false
        mShareButtonEnabled.value = true
        mSpinnersEnabled.value = true

        mAudioEncoderDecoderFramework?.stop()
    }

    private fun onShareResults() {
        shareWaveFile(mFile!!)
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
            mLastOutputBuffer = result.buffer
            if (mPlaySineSweep.value) {
                sineSweepOnMeasurement(analysisCount, result)
            } else {
                sineOnMeasurement(analysisCount, result)
            }
        }
    }

    private fun sineOnMeasurement(analysisCount: Int, result: HarmonicAnalyzer.Result) {
        mStatus.value = """
                analysis #%04d
                THD   = %6.4f%c
                THD+N = %6.4f%c
                SNR   = %6.2f dB
                nPeak  = %6.2f dB
            """.trimIndent().format(
            analysisCount,
            result.totalHarmonicDistortion * 100.0, '%',
            result.totalHarmonicDistortionPlusNoise * 100.0, '%',
            result.signalNoiseRatioDB,
            HarmonicAnalyzer.amplitudeToDecibels(result.peakAmplitude))
        val bins = result.bins
        mBins = FloatArray(bins!!.size)
        for (bucket in 0 until (bins.size)) {
            mBins!![bucket] = amplitudeToDecibels(bins[bucket]
                .toDouble()).toFloat()
        }
    }

    private fun sineSweepOnMeasurement(analysisCount: Int, result: HarmonicAnalyzer.Result) {
        if (mSpectogram == null) {
            mSpectogram = mutableListOf<FloatArray?>();
        }
        var bins = remapToLog(result.bins!!, WAVEFORM_HEIGHT)
        for (i in 0 until (bins.size)) {
            bins[i] = amplitudeToDecibels(bins[i].toDouble()).toFloat()
        }
        mStatus.value = """
                    analysis #%04d
                    nPeak  = %6.2f dB
                """.trimIndent().format(
            analysisCount,
            amplitudeToDecibels(result.peakAmplitude)
        )
        mSpectogram!!.add(bins)
        if (mSpectogram!!.size > SPECTOGRAM_WIDTH) {
            mSpectogram!!.removeAt(0)
        }
    }

    private fun getTimestampString(): String? {
        val df: DateFormat = SimpleDateFormat("yyyyMMdd-HHmmss")
        val now: Date = Calendar.getInstance().getTime()
        return df.format(now)
    }

    private fun createFileName(): File {
        // Get directory and filename
        val dir = getExternalFilesDir(Environment.DIRECTORY_MUSIC)
        return File(
            dir,
            "soundchecker" + "_" + getTimestampString() + ".wav"
        )
    }

    fun shareWaveFile(file: File) {
        // Share WAVE file via GMail, Drive or other method.
        val sharingIntent = Intent(Intent.ACTION_SEND)
        sharingIntent.type = "audio/wav"
        val subjectText = file.name
        sharingIntent.putExtra(Intent.EXTRA_SUBJECT, subjectText)
        val uri = FileProvider.getUriForFile(
            this,
            this.applicationContext.packageName.toString() + ".provider",
            file
        )
        sharingIntent.putExtra(Intent.EXTRA_STREAM, uri)
        sharingIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        startActivity(Intent.createChooser(sharingIntent, "Share WAV using:"))
    }
}
