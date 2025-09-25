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
import android.content.ContentUris
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.media.AudioTrack.WRITE_BLOCKING
import android.media.MediaCodecInfo
import android.media.MediaCodecList
import android.media.MediaFormat
import android.media.MediaMuxer
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.provider.OpenableColumns
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
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
import androidx.compose.runtime.LaunchedEffect
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
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.google.android.soundchecker.harmonicanalyzer.HarmonicAnalyzer
import com.google.android.soundchecker.harmonicanalyzer.HarmonicAnalyzer.Companion.amplitudeToDecibels
import com.google.android.soundchecker.harmonicanalyzer.HarmonicAnalyzerListener
import com.google.android.soundchecker.mediacodec.AudioEncoderDecoderFramework
import com.google.android.soundchecker.utils.WaveFileReader
import com.google.android.soundchecker.utils.remapToLog
import com.google.android.soundchecker.utils.ui.SpectogramDisplay
import com.google.android.soundchecker.utils.ui.WaveformDisplay
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.text.DateFormat
import java.text.SimpleDateFormat
import java.util.Arrays
import java.util.Calendar
import java.util.Date
import kotlin.concurrent.thread


class AudioEncoderDecoderActivity : ComponentActivity() {
    private var mStartButtonEnabled = mutableStateOf(true)
    private var mStopButtonEnabled = mutableStateOf(false)
    private var mShareButtonEnabled = mutableStateOf(false)
    private var mPlayButtonEnabled = mutableStateOf(false)
    private var mPlaySineSweep = mutableStateOf(false)

    private var mParam = mutableStateOf("")
    private var mStatus = mutableStateOf("")
    private var mFrequencyBins: MutableList<FloatArray?>? = null
    private var mSpectrograms: MutableList<MutableList<FloatArray?>?>? = null
    private var mWaveforms: MutableList<FloatArray?>? = null
    private var mTopBins: IntArray? = null
    private var mTopFrequencies: FloatArray? = null
    private var mFundamentalFrequencies: MutableList<MutableList<Float>?>? = null

    private var mSpinnersEnabled = mutableStateOf(true)
    private var mSampleRateText = mutableStateOf("")
    private var mChannelCountText = mutableStateOf("")
    private var mBitrateText = mutableStateOf("")
    private var mFlacCompressionLevelText = mutableStateOf("")
    private var mAacProfileText = mutableStateOf("")
    private var mAudioCodecText = mutableStateOf("")
    private var mOutputFormatText = mutableStateOf("")
    private var mEncoderDelayText = mutableStateOf("")

    private var mAudioEncoderDecoderFramework: AudioEncoderDecoderFramework? = null
    private val mListener: MyHarmonicAnalyzerListener = MyHarmonicAnalyzerListener()
    private var mSampleRate = 0;
    private var mChannelCount = 0;
    private var mBitrate = 0;
    private var mFlacCompressionLevel = 0;
    private var mAacProfile = 0;
    private var mEncoderDelay = 0;

    private var mCodecStatus = mutableStateOf("")
    private var mAudioCodecs: MutableList<MediaCodecInfo>? = null
    private var mAudioCodecStrings: MutableList<String>? = null
    private var mSelectedCodec: MediaCodecInfo? = null
    private var mAvailableOutputFormats: MutableList<String>? = null
    private var mAvailableSampleRates: MutableList<Int>? = null
    private var mAvailableChannelCounts: MutableList<Int>? = null
    private var mAvailableBitRates: MutableList<Int>? = null
    private var mAvailableAacProfiles: MutableList<Int>? = null

    private var mOutputFile: File? = null
    private var mEncodedFile: File? = null
    private var mEncodedFileOutputStream: FileOutputStream? = null
    private var mEncodedFileType: String = ""

    private var mInputFile: Uri? = null
    private var mInputFileMsg = mutableStateOf("")
    private var mInputFileStatus = mutableStateOf("")
    private var mInputFileNumChannels = 0
    private var mInputFileSampleRate = 0
    private var mInputFileName = ""
    private var mInputFileStream: InputStream? = null
    private var mOutputLogFileName: String? = null
    private var mOutputLogFile: File? = null

    private var mPlayAudioMonotonicCounter = 0 // Used to allow one AudioTrack to play at a time.
    private var mAutoStart = false
    private var mAutoExit = false
    private var mCallbackCount = 0
    private var mMaxCallbacks = -1

    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        updateMediaCodecList()
        setContent {
            LaunchedEffect(Unit) {
                handleIntent()
            }
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
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        val pickFileLauncher = rememberLauncherForActivityResult(
                            ActivityResultContracts.GetContent()
                        ) { uri ->
                            if (uri != null) {
                                mInputFile = uri
                                mInputFileName = getSelectedFileName()
                                mInputFileMsg.value = getSelectedFileUnplayableReason()
                                displayInputFileStatus()
                                updateMediaCodecList()
                            }
                        }
                        Button(onClick = {
                            pickFileLauncher.launch("*/*")
                        }) {
                            Text(text = "Select File")
                        }
                        Spacer(modifier = Modifier.padding(4.dp))

                        Button(onClick = {
                            mInputFile = null
                            mInputFileName = ""
                            mInputFileStatus.value = ""
                            updateMediaCodecList()
                        }) {
                            Text(text = "Cancel")
                        }
                        Spacer(modifier = Modifier.padding(4.dp))

                        Button(onClick = {
                            playInputFile()
                        }) {
                            Text(text = "Play")
                        }
                    }
                    mInputFileMsg.value = getSelectedFileUnplayableReason()
                    Text(text = mInputFileName)
                    if (mInputFileStatus.value != "") {
                        Spacer(modifier = Modifier.padding(4.dp))
                        Text(
                            text = mInputFileStatus.value,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Light
                        )
                        Spacer(modifier = Modifier.padding(4.dp))
                    }
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
                                mAudioCodecStrings!!.forEachIndexed { index, bin ->
                                    DropdownMenuItem(
                                        text = { Text(bin) },
                                        onClick = {
                                            mSelectedCodec = mAudioCodecs!![index]
                                            mAudioCodecText.value = mAudioCodecStrings!![index]
                                            updateAvailableOutputFormats()
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
                    Text(
                        text = mCodecStatus.value,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Light
                    )
                    Spacer(modifier = Modifier.padding(4.dp))
                    Row {
                        Text(text = "Output Format")
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
                                mAvailableOutputFormats!!.forEachIndexed { index, bin ->
                                    DropdownMenuItem(
                                        text = { Text(bin) },
                                        onClick = {
                                            mOutputFormatText.value =
                                                mAvailableOutputFormats!![index]
                                            updateSelectedFormat()
                                            expanded = false
                                        },
                                        enabled = mSpinnersEnabled.value
                                    )
                                }
                            }
                            Text(
                                text = mOutputFormatText.value, modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable(onClick = { expanded = true })
                                    .background(
                                        Color.Gray
                                    )
                            )
                        }
                    }
                    if (mOutputFormatText.value == AUDIO_FORMAT_AAC) {
                        Spacer(modifier = Modifier.padding(4.dp))
                        Row {
                            Text(text = "AAC profile")
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
                                    mAvailableAacProfiles!!.forEachIndexed { index, bin ->
                                        DropdownMenuItem(
                                            text = {Text(AAC_CODEC_PROFILES_TO_STRING[bin].toString())},
                                            onClick = {
                                                mAacProfile = mAvailableAacProfiles!![index]
                                                mAacProfileText.value = AAC_CODEC_PROFILES_TO_STRING[mAacProfile].toString()
                                                expanded = false
                                            },
                                            enabled = mSpinnersEnabled.value
                                        )
                                    }
                                }
                                Text(
                                    text = mAacProfileText.value, modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable(onClick = { expanded = true })
                                        .background(
                                            Color.Gray
                                        )
                                )
                            }
                        }
                    }
                    if (mInputFile == null) {
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
                                    mAvailableSampleRates!!.forEachIndexed { index, bin ->
                                        DropdownMenuItem(
                                            text = { Text(bin.toString()) },
                                            onClick = {
                                                mSampleRate = mAvailableSampleRates!![index]
                                                mSampleRateText.value =
                                                    mAvailableSampleRates!![index].toString()
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
                            Text(text = "Channel Count")
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
                                    mAvailableChannelCounts!!.forEachIndexed { index, bin ->
                                        DropdownMenuItem(
                                            text = { Text(bin.toString()) },
                                            onClick = {
                                                mChannelCount = mAvailableChannelCounts!![index]
                                                mChannelCountText.value =
                                                    mAvailableChannelCounts!![index].toString()
                                                expanded = false
                                            },
                                            enabled = mSpinnersEnabled.value
                                        )
                                    }
                                }
                                Text(
                                    text = mChannelCountText.value, modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable(onClick = { expanded = true })
                                        .background(
                                            Color.Gray
                                        )
                                )
                            }
                        }
                    }
                    if (mOutputFormatText.value != AUDIO_FORMAT_FLAC) {
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
                                    mAvailableBitRates!!.forEachIndexed { index, bin ->
                                        DropdownMenuItem(
                                            text = { Text(bin.toString()) },
                                            onClick = {
                                                mBitrate = mAvailableBitRates!![index]
                                                mBitrateText.value =
                                                    mAvailableBitRates!![index].toString()
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
                    } else {
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
                                                mFlacCompressionLevel =
                                                    FLAC_COMPRESSION_LEVELS[index]
                                                mFlacCompressionLevelText.value =
                                                    FLAC_COMPRESSION_LEVELS[index].toString()
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
                    }
                    Spacer(modifier = Modifier.padding(4.dp))
                    Row {
                        Text(text = "Encoder Delay")
                        Spacer(modifier = Modifier.padding(4.dp))
                        var expanded by remember { mutableStateOf(false) }
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .wrapContentSize(Alignment.CenterEnd)
                        ) {
                            // Create a dropdown menu for encoder delays
                            DropdownMenu(
                                expanded = expanded,
                                onDismissRequest = { expanded = false },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                ENCODER_DELAYS.forEachIndexed { index, bin ->
                                    DropdownMenuItem(
                                        text = { Text(bin.toString()) },
                                        onClick = {
                                            mEncoderDelay =
                                                ENCODER_DELAYS[index]
                                            mEncoderDelayText.value =
                                                ENCODER_DELAYS[index].toString()
                                            expanded = false
                                        },
                                        enabled = mSpinnersEnabled.value
                                    )
                                }
                            }
                            Text(
                                text = mEncoderDelayText.value, modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable(onClick = { expanded = true })
                                    .background(
                                        Color.Gray
                                    )
                            )
                        }
                    }
                    if (mInputFile == null) {
                        Spacer(modifier = Modifier.padding(4.dp))
                        Row {
                            Text(text = "Play sine sweep",
                                modifier = Modifier.align(Alignment.CenterVertically))
                            Checkbox(
                                checked = mPlaySineSweep.value,
                                onCheckedChange = { mPlaySineSweep.value = it },
                                enabled = mSpinnersEnabled.value
                            )
                        }
                    }
                    if (mEncodedFile != null) {
                        Spacer(modifier = Modifier.padding(4.dp))
                        Row {
                            Button(
                                onClick = {
                                    onShareEncodedData()
                                },
                                enabled = mShareButtonEnabled.value
                            ) {
                                Text(text = "Share Encoded Data")
                            }
                        }
                    }
                    Spacer(modifier = Modifier.padding(4.dp))
                    Row {
                        Button(
                            onClick = {
                                onStartTest()
                            },
                            enabled = mStartButtonEnabled.value) {
                            Text(text = "Start")
                        }
                        Button(
                            onClick = {
                                onStopTest()
                            },
                            enabled = mStopButtonEnabled.value) {
                            Text(text = "Stop")
                        }
                        Button(
                            onClick = {
                                onShareOutputFile()
                            },
                            enabled = mShareButtonEnabled.value) {
                            Text(text = "Share")
                        }
                        Button(
                            onClick = {
                                onPlayResult()
                            },
                            enabled = mPlayButtonEnabled.value) {
                            Text(text = "Play")
                        }
                    }
                    Spacer(modifier = Modifier.padding(4.dp))
                    Text(text = mParam.value)
                    Spacer(modifier = Modifier.padding(4.dp))
                    Text(text = mStatus.value)
                    if (mFundamentalFrequencies != null) {
                        Spacer(modifier = Modifier.padding(4.dp))
                        var channelNumber = 1
                        for (frequencies in mFundamentalFrequencies!!) {
                            Text(text = "Channel #$channelNumber initial frequencies: " + (frequencies?.take(INITIAL_FREQUENCIES_TO_PRINT)?.joinToString()))
                            channelNumber++
                        }
                    }
                    if (mWaveforms != null) {
                        var channelNumber = 1
                        for (waveform in mWaveforms!!) {
                            Spacer(modifier = Modifier.padding(4.dp))
                            var waveformText = "Resulting waveform. Channel #$channelNumber. Pinch to zoom"
                            if (mTopBins != null && mTopFrequencies != null) {
                                waveformText += "\nBin: ${mTopBins!![channelNumber - 1]}, Frequency: ${mTopFrequencies!![channelNumber - 1]}"
                            }
                            Text(text = waveformText)
                            WaveformDisplay(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(WAVEFORM_HEIGHT.dp)
                                    .border(1.dp, Color.Gray)
                                    .background(Color.Green)
                                    .padding(horizontal = 4.dp, vertical = 4.dp),
                                yValues = waveform,
                                yMin = -1.0f,
                                yMax = 1.0f,
                                shouldZoom = true
                            )
                            channelNumber++
                        }
                    }
                    if (mFrequencyBins != null) {
                        var channelNumber = 1
                        for (bins in mFrequencyBins!!) {
                            Spacer(modifier = Modifier.padding(4.dp))
                            Text(text = "Frequency bins for channel #$channelNumber")
                            WaveformDisplay(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(WAVEFORM_HEIGHT.dp)
                                    .border(1.dp, Color.Gray)
                                    .background(Color.LightGray)
                                    .padding(horizontal = 4.dp, vertical = 4.dp),
                                yValues = bins,
                                yMin = MIN_DECIBELS,
                                yMax = 0.0f,
                            )
                            channelNumber++
                        }
                    }
                    if (mSpectrograms != null) {
                        var channelNumber = 1
                        for (spectrogram in mSpectrograms!!) {
                            Spacer(modifier = Modifier.padding(4.dp))
                            Text(text = "Spectrogram for channel #$channelNumber")
                            SpectogramDisplay(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(WAVEFORM_HEIGHT.dp)
                                    .border(1.dp, Color.Gray)
                                    .background(Color.LightGray)
                                    .padding(horizontal = 4.dp, vertical = 4.dp),
                                values = spectrogram,
                                min = MIN_DECIBELS,
                                max = 0.0f,
                            )
                            channelNumber++
                        }
                    }
                }
            }
        }
    }

    private fun handleIntent() {
        if (intent == null) {
            return
        }

        val source = intent.getStringExtra(INTENT_EXTRA_SOURCE)
        if (source == "file") {

            var permission = Manifest.permission.READ_EXTERNAL_STORAGE
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                permission = Manifest.permission.READ_MEDIA_AUDIO
            }
            if (ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED) {
                permissionsResultCallback.launch(permission)
            } else {
                updateInputFileFromIntent()
            }
        } else if (source == "sine" || source == "sinesweep") {
            mPlaySineSweep.value = source == "sinesweep"
            handleRestOfIntent()
        }
    }

    // We need to handle intents differently in the input file case as a callback is used.
    // This is should be called after the input file is done with its setup or directly when
    // an input file is not used.
    private fun handleRestOfIntent() {
        val codecName = intent.getStringExtra(INTENT_EXTRA_CODEC_NAME)
        if (codecName != null) {
            mAudioCodecText.value = codecName
        }
        val mimeType = intent.getStringExtra(INTENT_EXTRA_MIME_TYPE)
        if (mimeType != null) {
            mOutputFormatText.value = mimeType
        }
        mSampleRate = intent.getIntExtra(INTENT_EXTRA_SAMPLE_RATE, mSampleRate)
        mSampleRateText.value = mSampleRate.toString()
        mChannelCount = intent.getIntExtra(INTENT_EXTRA_CHANNEL_COUNT, mChannelCount)
        mChannelCountText.value = mChannelCount.toString()
        mBitrate = intent.getIntExtra(INTENT_EXTRA_BITRATE, mBitrate)
        mBitrateText.value = mBitrate.toString()
        mAacProfile = intent.getIntExtra(INTENT_EXTRA_AAC_PROFILE, mAacProfile)
        mAacProfileText.value = AAC_CODEC_PROFILES_TO_STRING[mAacProfile].toString()
        mFlacCompressionLevel = intent.getIntExtra(INTENT_EXTRA_FLAC_COMPRESSION, mFlacCompressionLevel)
        mFlacCompressionLevelText.value = mFlacCompressionLevel.toString()
        mEncoderDelay = intent.getIntExtra(INTENT_EXTRA_ENCODER_DELAY, mEncoderDelay)
        mEncoderDelayText.value = mEncoderDelay.toString()

        mMaxCallbacks = intent.getIntExtra(INTENT_EXTRA_COUNT, mMaxCallbacks)
        mAutoStart = intent.getBooleanExtra(INTENT_EXTRA_AUTO_START, mAutoStart)
        mAutoExit = intent.getBooleanExtra(INTENT_EXTRA_AUTO_EXIT, mAutoExit)
        mOutputLogFileName = intent.getStringExtra(INTENT_EXTRA_OUTPUT_FILE)

        if (mAutoStart) {
            runTest()
        }
    }

    private val permissionsResultCallback = registerForActivityResult(
        ActivityResultContracts.RequestPermission()){
        when (it) {
            true -> {
                updateInputFileFromIntent()
            }
            false -> {
                Toast.makeText(this, "Permission denied", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun updateInputFileFromIntent() {
        val source = intent.getStringExtra(INTENT_EXTRA_SOURCE)
        if (source == "file") {
            val inputFileName = intent.getStringExtra(INTENT_EXTRA_INPUT_FILE)
            if (inputFileName == null) {
                Log.w("URIError", "Intent extra INTENT_EXTRA_INPUT_FILE is null")
                return
            }
            val filePath = "/sdcard/Music/${inputFileName}"
            val file = File(filePath)

            if (!file.exists()) {
                // If you see this log, your path is wrong or the file isn't there.
                Log.e("MediaScanner", "File does NOT exist at path: $filePath")
                return
            }

            Log.i("MediaScanner", "File exists at path: $filePath. Starting scan.")
            MediaScannerConnection.scanFile(
                this,
                arrayOf(filePath),
                arrayOf("audio/wav")
            ) { path, uri ->
                Log.i("MediaScanner", "Scan complete for $path, URI is $uri")

                val projection =
                    arrayOf(MediaStore.Audio.Media._ID, MediaStore.Audio.Media.DISPLAY_NAME)
                val selection = "${MediaStore.Audio.Media.DISPLAY_NAME} = ?"
                val selectionArgs = arrayOf(inputFileName)

                val cursor = applicationContext.contentResolver.query(
                    MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                    projection,
                    selection,
                    selectionArgs,
                    null
                )

                cursor?.use {
                    if (it.moveToFirst()) {
                        val id = it.getLong(it.getColumnIndexOrThrow(MediaStore.Audio.Media._ID))
                        mInputFile =
                            ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id)
                        mInputFileName = inputFileName
                        displayInputFileStatus()
                        updateMediaCodecList()
                    }
                }

                handleRestOfIntent()
            }
        }
    }
    private fun onStartTest() {
        runTest()
    }

    private fun playAudioStream(audioStream: InputStream) {
        val reader = WaveFileReader(audioStream)
        reader.parse()
        playParsedAudioStream(reader)
    }

    private fun playParsedAudioStream(reader: WaveFileReader) {
        try {
            Log.d(TAG, "numChannels: " + reader.getNumChannels() +
                    ",sampleEncoding: " + reader.getSampleEncoding() +
                    ",sampleRate: " + reader.getSampleRate() +
                    ",bitsPerSample: " + reader.getBitsPerSample() +
                    ",numSampleFrames: " + reader.getNumSampleFrames())

            mPlayAudioMonotonicCounter++
            val currentPlayAudioMonotonicCounter = mPlayAudioMonotonicCounter

            var channelFormat = AudioFormat.CHANNEL_OUT_MONO
            if (reader.getNumChannels() == 2) {
                channelFormat = AudioFormat.CHANNEL_OUT_STEREO
            }

            val attributesBuilder: AudioAttributes.Builder = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
            val attributes: AudioAttributes = attributesBuilder.build()

            val format = AudioFormat.Builder()
                .setEncoding(AudioFormat.ENCODING_PCM_FLOAT)
                .setChannelMask(channelFormat)
                .setSampleRate(reader.getSampleRate())
                .build()
            val builder = AudioTrack.Builder()
                .setAudioAttributes(attributes)
                .setAudioFormat(format)
                .setPerformanceMode(AudioTrack.PERFORMANCE_MODE_LOW_LATENCY)
            val at = builder.build()
            at.play()

            try {
                val framesPerWrite = 50
                val music = FloatArray(framesPerWrite * reader.getNumChannels())
                var framesRead = framesPerWrite
                var isPos = true;
                // Only one stream should be playing audio at once.
                // Since this loop happens often, whenever mPlayAudioMonotonicCounter increments,
                // stop writing audio.
                while (framesRead == framesPerWrite && mPlayAudioMonotonicCounter == currentPlayAudioMonotonicCounter) {
                    framesRead = reader.getDataFloat(music, framesPerWrite)
                    //Log.d(TAG, Arrays.toString(music))
                    at.write(music, 0, framesRead * reader.getNumChannels(), WRITE_BLOCKING)
                }
            } catch (e: IOException) {
                e.printStackTrace()
            }

            at.stop()
            at.release()
        } catch (ex: IOException) {
            Log.i(TAG, "IOException$ex")
        }
    }

    private fun onPlayResult() {
        thread {
            playAudioStream(mOutputFile!!.inputStream())
        }
    }

    private fun playInputFile() {
        thread {
            val inputStream = contentResolver.openInputStream(mInputFile!!)
            playAudioStream(inputStream!!)
            inputStream.close()
        }
    }

    private fun displayInputFileStatus() {
        val inputStream = contentResolver.openInputStream(mInputFile!!)
        val reader = WaveFileReader(inputStream!!)
        reader.parse()
        mInputFileStatus.value = """
                    Channel Count: %d
                    Sample Encoding: %d
                    Sample Rate: %d
                    Bits Per Sample: %d
                    Number of Sample Frames: %d
                """.trimIndent().format(
            reader.getNumChannels(),
            reader.getSampleEncoding(),
            reader.getSampleRate(),
            reader.getBitsPerSample(),
            reader.getNumSampleFrames()
        )
        mInputFileNumChannels = reader.getNumChannels()
        mInputFileSampleRate = reader.getSampleRate()
        inputStream.close()
    }

    private fun runTest() {
        mCallbackCount = 0
        mPlayAudioMonotonicCounter++ // Stop the previous audioStream
        mStartButtonEnabled.value = false
        mStopButtonEnabled.value = true
        mShareButtonEnabled.value = false
        mPlayButtonEnabled.value = false
        mSpinnersEnabled.value = false

        if (mOutputLogFileName != null) {
            val dir = getExternalFilesDir(Environment.DIRECTORY_MUSIC)
            mOutputLogFile = mOutputLogFileName?.let { File(dir, it) }
            mOutputLogFile?.writeText("")
        } else {
            mOutputLogFile = null
        }
        val timestamp = getTimestampString()
        mOutputFile = createFile(timestamp, "wav")

        var encodedFormat = AUDIO_FORMAT_TO_MEDIA_MUXER_OUTPUT_FORMAT.get(mOutputFormatText.value)
        var mediaMuxer: MediaMuxer? = null
        if (encodedFormat != null) {
            val encodedFileExtension = MEDIA_MUXER_OUTPUT_FORMAT_TO_EXTENSION.get(encodedFormat)
            mEncodedFile = createFile(getTimestampString(), encodedFileExtension!!)
            mEncodedFileType = MEDIA_MUXER_OUTPUT_FORMAT_TO_STRING[encodedFormat].toString()
            mEncodedFileOutputStream = FileOutputStream(mEncodedFile)
            mediaMuxer = MediaMuxer(mEncodedFileOutputStream!!.fd, encodedFormat)
        } else {
            mEncodedFile = null
            mEncodedFileType = ""
            mEncodedFileOutputStream = null
        }

        try {
            if (mInputFile == null) {
                mAudioEncoderDecoderFramework = AudioEncoderDecoderFramework(
                    mAudioCodecText.value,
                    mOutputFormatText.value,
                    mSampleRate,
                    mChannelCount,
                    mBitrate,
                    mFlacCompressionLevel,
                    mAacProfile,
                    AUDIO_FORMAT,
                    mPlaySineSweep.value,
                    mOutputFile!!,
                    null,
                    mediaMuxer,
                    mEncoderDelay
                )
            } else {
                mInputFileStream?.close()
                val inputStream = contentResolver.openInputStream(mInputFile!!)
                val reader = WaveFileReader(inputStream!!)
                reader.parse()
                mAudioEncoderDecoderFramework = AudioEncoderDecoderFramework(
                    mAudioCodecText.value,
                    mOutputFormatText.value,
                    mInputFileSampleRate,
                    mInputFileNumChannels,
                    mBitrate,
                    mFlacCompressionLevel,
                    mAacProfile,
                    AUDIO_FORMAT,
                    mPlaySineSweep.value,
                    mOutputFile!!,
                    reader,
                    mediaMuxer,
                    mEncoderDelay
                )
            }
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

        mWaveforms = null
        mFrequencyBins = null
        mSpectrograms = null
        mTopBins = null
        mTopFrequencies = null
        mFundamentalFrequencies = null
        mStatus.value = ""
        mParam.value = ""

        mAudioEncoderDecoderFramework?.addListener(mListener)

        if (mInputFile != null) {
            harmonicAnalyzerSink.mSampleRate = mInputFileSampleRate
        } else {
            harmonicAnalyzerSink.mSampleRate = mSampleRate
        }
        harmonicAnalyzerSink.mFftSize = FFT_SIZE
        if (mPlaySineSweep.value) {
            harmonicAnalyzerSink.mFundamentalBins = IntArray(mChannelCount)
        }

        mAudioEncoderDecoderFramework?.start()
    }

    private fun onStopTest() {
        mStartButtonEnabled.value = true
        mStopButtonEnabled.value = false
        mShareButtonEnabled.value = true
        mPlayButtonEnabled.value = true
        mSpinnersEnabled.value = true

        mInputFileStream?.close()
        mEncodedFileOutputStream?.close()
        mAudioEncoderDecoderFramework?.stop()
        if (mAutoExit) {
            finish()
        }
    }

    private fun onShareOutputFile() {
        shareWaveFile(mOutputFile!!)
    }

    private fun onShareEncodedData() {
        shareFile(mEncodedFile!!, mEncodedFileType)
    }

    private inner class MyHarmonicAnalyzerListener : HarmonicAnalyzerListener {
        override fun onMeasurement(analysisCount: Int, results: ArrayList<HarmonicAnalyzer.Result>) {
            val numberOfChannels = results.size
            val numberOfSamples = results[0].buffer!!.size
            val numberOfFrames = numberOfSamples / numberOfChannels
            mWaveforms = mutableListOf<FloatArray?>()
            for (channel in 0 until numberOfChannels) {
                mWaveforms!!.add(results[channel].buffer)
            }
            if (mInputFile == null) {
                mTopBins = IntArray(results.size)
                mTopFrequencies = FloatArray(results.size)
                if (mFundamentalFrequencies == null) {
                    mFundamentalFrequencies = MutableList(results.size) { mutableListOf<Float>() }
                }
                for (channel in 0 until results.size) {
                    val bins = results[channel].bins!!
                    val maxValue = bins.max()
                    val bestBin = bins.indexOfFirst { it == maxValue }
                    val topFrequency = mAudioEncoderDecoderFramework?.calculateBinFrequency(bestBin)
                    mTopBins!![channel] = bestBin
                    mTopFrequencies!![channel] = topFrequency!!.toFloat()
                    mFundamentalFrequencies!![channel]!!.add(topFrequency.toFloat())
                }
            }

            if (mInputFile != null) {
                inputFileOnMeasurement(analysisCount, results)
            } else if (mPlaySineSweep.value) {
                sineSweepOnMeasurement(analysisCount, results)
            } else {
                sineOnMeasurement(analysisCount, results)
            }

            mParam.value = String.format("Decoded Sample Rate = %d Hz\nFFT size = %d\nFundamental Bins = %s\nInitial Target Frequencies = %s",
                mAudioEncoderDecoderFramework?.harmonicAnalyzerSink?.mSampleRate,
                mAudioEncoderDecoderFramework?.harmonicAnalyzerSink?.mFftSize,
                Arrays.toString(mAudioEncoderDecoderFramework?.harmonicAnalyzerSink?.mFundamentalBins),
                mAudioEncoderDecoderFramework?.getInitialFrequencies().toString())

            mOutputLogFile?.let {
                it.appendText("onMeasurement: " + analysisCount + "\n")
                it.appendText(mParam.value + "\n")
                it.appendText(mStatus.value + "\n")
                if (mTopFrequencies != null) {
                    it.appendText("Top Frequencies: " + mTopFrequencies!!.joinToString() + "\n")
                }
            }

            if (results[0].endOfStream) {
                onStopTest()
            }
            mCallbackCount++
            if (mMaxCallbacks > 0 && mCallbackCount >= mMaxCallbacks) {
                onStopTest()
            }
        }
    }

    private fun inputFileOnMeasurement(analysisCount: Int, results: ArrayList<HarmonicAnalyzer.Result>) {
        mStatus.value = """
                analysis #%04d
            """.trimIndent().format(
            analysisCount)
    }

    private fun sineOnMeasurement(analysisCount: Int, results: ArrayList<HarmonicAnalyzer.Result>) {
        val totalHarmomicDistortionArray = ArrayList<String>(results.size)
        val totalHarmomicDistortionPlusNoiseArray = ArrayList<String>(results.size)
        val signalNoiseRatioDBArray = ArrayList<String>(results.size)
        val peakAmplitudeArray = ArrayList<String>(results.size)
        for (result in results) {
            totalHarmomicDistortionArray.add("%6.4f%c".format(result.totalHarmonicDistortion * 100.0, '%'))
            totalHarmomicDistortionPlusNoiseArray.add("%6.4f%c".format(result.totalHarmonicDistortionPlusNoise * 100.0, '%'))
            signalNoiseRatioDBArray.add("%6.2f dB".format(result.signalNoiseRatioDB))
            peakAmplitudeArray.add("%6.2f dB".format(amplitudeToDecibels(result.peakAmplitude)))
        }
        mStatus.value = """
                analysis #%04d
                THD   = [%s]
                THD+N = [%s]
                SNR   = [%s]
                nPeak = [%s]
            """.trimIndent().format(
            analysisCount,
            totalHarmomicDistortionArray.joinToString(),
            totalHarmomicDistortionPlusNoiseArray.joinToString(),
            signalNoiseRatioDBArray.joinToString(),
            peakAmplitudeArray.joinToString())
        mFrequencyBins = mutableListOf<FloatArray?>()
        for (channel in 0 until results.size) {
            val bins = results[channel].bins
            val decibelBins = FloatArray(bins!!.size)
            for (bucket in 0 until (bins.size)) {
                decibelBins[bucket] = amplitudeToDecibels(bins[bucket]
                    .toDouble()).toFloat()
            }
            mFrequencyBins!!.add(decibelBins)
        }
    }

    private fun sineSweepOnMeasurement(analysisCount: Int, results: ArrayList<HarmonicAnalyzer.Result>) {
        if (mSpectrograms == null) {
            mSpectrograms = mutableListOf<MutableList<FloatArray?>?>()
            for (channel in 0 until results.size) {
                mSpectrograms!!.add(mutableListOf<FloatArray?>())
            }
        }
        for (channel in 0 until results.size) {
            var bins = remapToLog(results[channel].bins!!, WAVEFORM_HEIGHT)
            for (i in 0 until (bins.size)) {
                bins[i] = amplitudeToDecibels(bins[i].toDouble()).toFloat()
            }
            mSpectrograms!![channel]!!.add(bins)
            if (mSpectrograms!![channel]!!.size > SPECTOGRAM_WIDTH) {
                mSpectrograms!![channel]!!.removeAt(0)
            }
        }

        val peakAmplitudeArray = ArrayList<String>(results.size)
        for (result in results) {
            peakAmplitudeArray.add("%6.2fdB".format(amplitudeToDecibels(result.peakAmplitude)))
        }
        mStatus.value = """
                    analysis #%04d
                    nPeak  = [%s]
                """.trimIndent().format(
            analysisCount,
            peakAmplitudeArray.joinToString()
        )
    }

    private fun getTimestampString(): String? {
        val df: DateFormat = SimpleDateFormat("yyyyMMdd-HHmmss")
        val now: Date = Calendar.getInstance().getTime()
        return df.format(now)
    }

    private fun createFile(timestamp: String?, extension: String): File {
        // Get directory and filename
        val dir = getExternalFilesDir(Environment.DIRECTORY_MUSIC)
        return File(
            dir,
            "soundchecker" + "_" + timestamp + "." + extension
        )
    }

    fun shareWaveFile(file: File) {
        shareFile(file, "audio/wav")
    }

    fun shareFile(file: File, type: String) {
        // Share file via GMail, Drive or other method.
        val sharingIntent = Intent(Intent.ACTION_SEND)
        sharingIntent.type = type
        val subjectText = file.name
        sharingIntent.putExtra(Intent.EXTRA_SUBJECT, subjectText)
        val uri = FileProvider.getUriForFile(
            this,
            this.applicationContext.packageName.toString() + ".provider",
            file
        )
        sharingIntent.putExtra(Intent.EXTRA_STREAM, uri)
        sharingIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        startActivity(Intent.createChooser(sharingIntent, "Share using:"))
    }

    open fun getSelectedFileUnplayableReason(): String {
        if (mInputFile == null) {
            return getString(R.string.file_not_selected)
        }
        return ""
    }

    private fun getSelectedFileName(): String {
        if (mInputFile == null) {
            return ""
        }
        val file = mInputFile!!
        val cursor = contentResolver.query(file, null, null, null, null)
        checkNotNull(cursor) {
            Toast.makeText(this, "Cannot get name of the selected file", Toast.LENGTH_LONG).show()
        }
        val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
        cursor.moveToFirst()
        val name = cursor.getString(index)
        cursor.close()
        return name
    }

    private fun updateMediaCodecList() {
        val mediaCodecList = MediaCodecList(MediaCodecList.REGULAR_CODECS)
        val mediaCodecInfos: Array<MediaCodecInfo> = mediaCodecList.codecInfos
        mAudioCodecs = mutableListOf<MediaCodecInfo>()
        mAudioCodecStrings = mutableListOf<String>()
        for (mediaCodecInfo in mediaCodecInfos) {
            if (mediaCodecInfo.isEncoder) {
                for (type in mediaCodecInfo.getSupportedTypes()) {
                    val codecCapabilities: MediaCodecInfo.CodecCapabilities =
                        mediaCodecInfo.getCapabilitiesForType(type)
                    val audioCapabilities: MediaCodecInfo.AudioCapabilities? =
                        codecCapabilities.audioCapabilities
                    if (audioCapabilities != null) {
                        if (mInputFile == null || isFormatSupported(mediaCodecInfo, type,
                                mInputFileSampleRate, mInputFileNumChannels)) {
                            mAudioCodecs!!.add(mediaCodecInfo)
                            mAudioCodecStrings!!.add(mediaCodecInfo.name)
                            break
                        }
                    }
                }
            }
        }
        mSelectedCodec = mAudioCodecs!!.get(0)
        mAudioCodecText.value = mAudioCodecStrings!!.get(0)
        updateAvailableOutputFormats()
    }

    private fun updateAvailableOutputFormats() {
        val mediaCodecInfo = mSelectedCodec!!
        mAvailableOutputFormats = mutableListOf<String>()
        for (type in mediaCodecInfo.getSupportedTypes()) {
            val codecCapabilities: MediaCodecInfo.CodecCapabilities =
                mediaCodecInfo.getCapabilitiesForType(type)
            val audioCapabilities: MediaCodecInfo.AudioCapabilities? =
                codecCapabilities.audioCapabilities
            if (audioCapabilities != null) {
                if (mInputFile == null || isFormatSupported(mediaCodecInfo, type,
                        mInputFileSampleRate, mInputFileNumChannels)) {
                    mAvailableOutputFormats!!.add(type)
                }
            }
        }
        mOutputFormatText.value = mAvailableOutputFormats!!.get(0)
        updateSelectedFormat()
    }

    private fun isFormatSupported(mediaCodecInfo: MediaCodecInfo, outputFormatType: String,
                                  inputSampleRate: Int, inputChannelCount: Int) : Boolean {
        val codecCapabilities = mediaCodecInfo.getCapabilitiesForType(outputFormatType)
        val audioCapabilities = codecCapabilities.audioCapabilities
        if (audioCapabilities.supportedSampleRates != null) {
            var sampleRateFound = false
            for (sampleRate in audioCapabilities.supportedSampleRates) {
                if (sampleRate == inputSampleRate) {
                    sampleRateFound = true
                    break
                }
            }
            if (!sampleRateFound) {
                return false
            }
        } else {
            var sampleRateFound = false
            for (sampleRateRange in audioCapabilities.supportedSampleRateRanges) {
                if (inputSampleRate >= sampleRateRange.lower && inputSampleRate <=
                        sampleRateRange.upper) {
                    sampleRateFound = true
                    break
                }
            }
            if (!sampleRateFound) {
                return false
            }
        }
        var channelCountFound = false
        for (channelCountRange in audioCapabilities.inputChannelCountRanges) {
            if (inputChannelCount >= channelCountRange.lower && inputChannelCount <=
                    channelCountRange.upper) {
                channelCountFound = true
                break
            }
        }
        if (!channelCountFound) {
            return false
        }
        return true
    }

    private fun updateSelectedFormat() {
        val mediaCodecInfo = mSelectedCodec!!
        var type = mOutputFormatText.value

        mAvailableAacProfiles = mutableListOf<Int>()
        for (aacProfile in AAC_CODEC_PROFILES_TO_STRING) {
            val aacFormat = MediaFormat()

            aacFormat.setString(MediaFormat.KEY_MIME, type)
            aacFormat.setInteger(
                MediaFormat.KEY_AAC_PROFILE,
                aacProfile.key
            )

            val codecCapabilities = mediaCodecInfo.getCapabilitiesForType(type)
            if (codecCapabilities.isFormatSupported(aacFormat)) {
                mAvailableAacProfiles!!.add(aacProfile.key)
            }
        }
        if (mAvailableAacProfiles!!.isNotEmpty()) {
            mAacProfile = mAvailableAacProfiles!![0]
            mAacProfileText.value = AAC_CODEC_PROFILES_TO_STRING[mAvailableAacProfiles!![0]].toString()
        }
        updateSelectedCodecType(type)
    }

    private fun updateSelectedCodecType(type: String) {
        val mediaCodecInfo = mSelectedCodec!!
        val codecCapabilities = mediaCodecInfo.getCapabilitiesForType(type)
        val audioCapabilities = codecCapabilities.audioCapabilities
        if (audioCapabilities.supportedSampleRates != null) {
            mAvailableSampleRates = audioCapabilities.supportedSampleRates.toMutableList()
        } else {
            mAvailableSampleRates = mutableListOf<Int>()
            mAvailableSampleRates!!.add(audioCapabilities.supportedSampleRateRanges.get(0).lower)
            for (bitRate in DEFAULT_SAMPLE_RATES) {
                if (bitRate > mAvailableSampleRates!!.last() &&
                        bitRate < audioCapabilities.supportedSampleRateRanges.get(0).upper) {
                    mAvailableSampleRates!!.add(bitRate)
                }
            }
            if (audioCapabilities.supportedSampleRateRanges.get(0).upper >
                    mAvailableSampleRates!!.last()) {
                mAvailableSampleRates!!.add(audioCapabilities.supportedSampleRateRanges.get(0)
                    .upper)
            }
        }
        mAvailableChannelCounts = mutableListOf<Int>()
        for (channelCountRange in audioCapabilities.inputChannelCountRanges) {
            for (channelCount in channelCountRange.lower..channelCountRange.upper) {
                mAvailableChannelCounts!!.add(channelCount)
            }
        }
        mAvailableBitRates = mutableListOf<Int>()
        mAvailableBitRates!!.add(audioCapabilities.bitrateRange.lower)
        for (bitRate in DEFAULT_BITRATES) {
            if (bitRate > mAvailableBitRates!!.last() &&
                    bitRate < audioCapabilities.bitrateRange.upper) {
                mAvailableBitRates!!.add(bitRate)
            }
        }
        if (audioCapabilities.bitrateRange.upper > mAvailableBitRates!!.last()) {
            mAvailableBitRates!!.add(audioCapabilities.bitrateRange.upper)
        }

        updateCodecStatus()
        updateCodecSpecificSpinnerValues()
    }

    private fun updateCodecSpecificSpinnerValues() {
        mSampleRate = mAvailableSampleRates!![0]
        mSampleRateText.value = mAvailableSampleRates!![0].toString()
        mChannelCount = mAvailableChannelCounts!![0]
        mChannelCountText.value = mAvailableChannelCounts!![0].toString()
        mBitrate = mAvailableBitRates!![0]
        mBitrateText.value = mAvailableBitRates!![0].toString()
        mFlacCompressionLevel = FLAC_COMPRESSION_LEVELS[0]
        mFlacCompressionLevelText.value = FLAC_COMPRESSION_LEVELS[0].toString()
        mEncoderDelay = ENCODER_DELAYS[0]
        mEncoderDelayText.value = ENCODER_DELAYS[0].toString()
    }

    private fun updateCodecStatus() {
        val mediaCodecInfo = mSelectedCodec!!
        val type = mOutputFormatText.value
        val report = StringBuffer()
        report.append("Name: ${mediaCodecInfo.name}\n")
        report.append("Canonical Name: ${mediaCodecInfo.canonicalName}\n")
        report.append("Is Alias: ${mediaCodecInfo.isAlias}\n")
        report.append("Is Hardware Accelerated: ${mediaCodecInfo.isHardwareAccelerated}\n")
        report.append("Is Software Only: ${mediaCodecInfo.isSoftwareOnly}\n")
        report.append("Is Vendor: ${mediaCodecInfo.isVendor}\n")
        report.append("Is Encoder: ${mediaCodecInfo.isEncoder}\n")
        report.append("Supported Types: ${Arrays.toString(mediaCodecInfo.supportedTypes)}\n")
        val codecCapabilities = mediaCodecInfo.getCapabilitiesForType(type)
        val audioCapabilities = codecCapabilities.audioCapabilities
        if (audioCapabilities != null) {
            report.append("Audio Type: $type\n")
            report.append("Bitrate Range: ${audioCapabilities.bitrateRange}\n")
            report.append("Input Channel Count Ranges: ${Arrays.toString(audioCapabilities
                .inputChannelCountRanges)}\n")
            report.append("Min Input Channel Count: ${audioCapabilities
                .minInputChannelCount}\n")
            report.append("Max Input Channel Count: ${audioCapabilities
                .maxInputChannelCount}\n")
            report.append("Supported Sample Rate Ranges: ${Arrays.toString(audioCapabilities
                .supportedSampleRateRanges)}\n")
            report.append("Supported Sample Rates: ${Arrays.toString(audioCapabilities
                .supportedSampleRates)}\n")
            var supportedAACProfileStrings = emptyArray<String>()
            mAvailableAacProfiles?.forEach { aacProfile ->
                supportedAACProfileStrings += AAC_CODEC_PROFILES_TO_STRING[aacProfile].toString()
            }
            report.append("Supported AAC Profiles: ${Arrays.toString(supportedAACProfileStrings)}\n")
        }
        report.append("Is Encoder: ${mediaCodecInfo.isEncoder}")
        mCodecStatus.value = report.toString()
    }

    override fun onStop() {
        super.onStop()

        mPlayAudioMonotonicCounter++ // Stop the current playing audio stream
    }

    companion object {
        const val TAG = "AudioEncoderDecoderActivity"

        private const val FFT_SIZE = 1024
        // Arbitrary number of frequencies to print. This helps us understand the output better.
        private const val INITIAL_FREQUENCIES_TO_PRINT = 5
        private val AUDIO_FORMAT_FLAC = MediaFormat.MIMETYPE_AUDIO_FLAC
        private val AUDIO_FORMAT_AAC = MediaFormat.MIMETYPE_AUDIO_AAC
        private val DEFAULT_SAMPLE_RATES = listOf(8000, 16000, 32000, 44100, 48000, 96000, 192000)
        private val DEFAULT_BITRATES = listOf(6000, 10000, 20000, 64000, 128000)
        // Arbitrary list of encoder delays in frames. These are used by the decoders to clip the early frames
        private val ENCODER_DELAYS = listOf(0, 1000, 2000, 4000, 8000, 16000)
        private val FLAC_COMPRESSION_LEVELS = (0..8).toList()
        private val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_FLOAT
        private val WAVEFORM_HEIGHT = 200
        private val SPECTOGRAM_WIDTH = 300
        private val AUDIO_FORMAT_TO_MEDIA_MUXER_OUTPUT_FORMAT = mapOf(
                MediaFormat.MIMETYPE_AUDIO_AAC to MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4,
                MediaFormat.MIMETYPE_AUDIO_AMR_NB to MediaMuxer.OutputFormat.MUXER_OUTPUT_3GPP,
                MediaFormat.MIMETYPE_AUDIO_AMR_WB to MediaMuxer.OutputFormat.MUXER_OUTPUT_3GPP,
                MediaFormat.MIMETYPE_AUDIO_OPUS to MediaMuxer.OutputFormat.MUXER_OUTPUT_OGG,
        )
        private val MEDIA_MUXER_OUTPUT_FORMAT_TO_EXTENSION = mapOf(
            MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4 to "mp4",
            MediaMuxer.OutputFormat.MUXER_OUTPUT_3GPP to "3gp",
            MediaMuxer.OutputFormat.MUXER_OUTPUT_OGG to "ogg",
        )
        private val MEDIA_MUXER_OUTPUT_FORMAT_TO_STRING = mapOf(
            MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4 to "audio/mp4",
            MediaMuxer.OutputFormat.MUXER_OUTPUT_3GPP to "audio/3gpp",
            MediaMuxer.OutputFormat.MUXER_OUTPUT_OGG to "audio/ogg",
        )
        private val AAC_CODEC_PROFILES_TO_STRING = mapOf(
            MediaCodecInfo.CodecProfileLevel.AACObjectLC to "AACObjectLC",
            MediaCodecInfo.CodecProfileLevel.AACObjectLD to "AACObjectLD",
            MediaCodecInfo.CodecProfileLevel.AACObjectHE to "AACObjectHE",
            MediaCodecInfo.CodecProfileLevel.AACObjectHE_PS to "AACObjectHE_PS",
            MediaCodecInfo.CodecProfileLevel.AACObjectELD to "AACObjectELD",
            MediaCodecInfo.CodecProfileLevel.AACObjectERLC to "AACObjectERLC",
            MediaCodecInfo.CodecProfileLevel.AACObjectERScalable to "AACObjectERScalable",
            MediaCodecInfo.CodecProfileLevel.AACObjectLTP to "AACObjectLTP",
            MediaCodecInfo.CodecProfileLevel.AACObjectMain to "AACObjectMain",
            MediaCodecInfo.CodecProfileLevel.AACObjectSSR to "AACObjectSSR",
            MediaCodecInfo.CodecProfileLevel.AACObjectScalable to "AACObjectScalable",
            MediaCodecInfo.CodecProfileLevel.AACObjectXHE to "AACObjectXHE",
        )

        private const val MIN_DECIBELS = -120F

        const val INTENT_EXTRA_SOURCE = "source"
        const val INTENT_EXTRA_INPUT_FILE = "input_file"
        const val INTENT_EXTRA_OUTPUT_FILE = "output_file"
        const val INTENT_EXTRA_CODEC_NAME = "codec_name"
        const val INTENT_EXTRA_MIME_TYPE = "mime_type"
        const val INTENT_EXTRA_SAMPLE_RATE = "sample_rate"
        const val INTENT_EXTRA_CHANNEL_COUNT = "channel_count"
        const val INTENT_EXTRA_BITRATE = "bitrate"
        const val INTENT_EXTRA_AAC_PROFILE = "aac_profile"
        const val INTENT_EXTRA_FLAC_COMPRESSION = "flac_compression"
        const val INTENT_EXTRA_ENCODER_DELAY = "encoder_delay"
        const val INTENT_EXTRA_COUNT = "count"
        const val INTENT_EXTRA_AUTO_START = "auto_start"
        const val INTENT_EXTRA_AUTO_EXIT = "auto_exit"
    }
}
