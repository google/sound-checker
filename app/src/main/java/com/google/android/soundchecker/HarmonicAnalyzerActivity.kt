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
import android.media.AudioManager
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
import com.google.android.soundchecker.utils.deviceDisplayName
import com.google.android.soundchecker.utils.ui.AudioDeviceListEntry
import com.google.android.soundchecker.utils.ui.WaveformDisplay


class HarmonicAnalyzerActivity : ComponentActivity() {
    companion object {
        const val TAG = "HarmonicAnalyzerActivity"

        private const val AUTO_SELECTED_DEVICE_ID = 0
        val AUTO_SELECTED_DEVICE_ENTRY = AudioDeviceListEntry(AUTO_SELECTED_DEVICE_ID, "Auto Select")

        private const val SAMPLE_RATE = 48000
        private const val FFT_SIZE = 1024
        private const val AVERAGE_SIZE = 24
        private val FREQUENCIES = listOf(1000, 1500, 2000)
        private var BIN_VALUES = buildList(FREQUENCIES.size) {
            FREQUENCIES.forEach { frequency ->
                add(calculateNearestBin(frequency.toDouble()))
            }
        }
        private var BINS = buildList(BIN_VALUES.size) {
            BIN_VALUES.forEach { bin ->
                add(String.format("%8.3f Hz, bin#%d", calculateBinFrequency(bin), bin))
            }
        }

        private const val MIN_DECIBELS = -250F

        private fun calculateBinFrequency(bin: Int): Double {
            return SAMPLE_RATE.toDouble() * bin / FFT_SIZE
        }

        private fun calculateNearestBin(frequency: Double): Int {
            return (FFT_SIZE * frequency / SAMPLE_RATE).roundToInt()
        }
    }

    private var mInputDevices = MutableStateFlow(ArrayList<AudioDeviceListEntry>())
    private var mOutputDevices = MutableStateFlow(ArrayList<AudioDeviceListEntry>())

    private var mSelectedInputDevice: AudioDeviceInfo? = null
    private var mSelectedOutputDevice: AudioDeviceInfo? = null
    private var mSelectedInputChannelIndex = 0
    private var mSelectedOutputChannelIndex = 0

    private lateinit var mAudioManager: AudioManager
    private val mDeviceCallback = DeviceConnectionListener()

    private var mStartButtonEnabled = mutableStateOf(true)
    private var mStopButtonEnabled = mutableStateOf(false)

    private var mParam = mutableStateOf("")
    private var mStatus = mutableStateOf("")
    private var mUseLogDisplay = mutableStateOf(true)
    private var mBins: FloatArray? = null

    private lateinit var mRequestPermissionLauncher: ActivityResultLauncher<String>

    private var mFrequencySpinnerEnabled = mutableStateOf(true)
    private var mFrequencyBinText = mutableStateOf("")

    private var mHarmonicAnalyzerFramework: HarmonicAnalyzerFramework? = null
    private val mListener: MyHarmonicAnalyzerListener = MyHarmonicAnalyzerListener()
    private var mFundamentalBin = 0

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
        mRequestPermissionLauncher = registerForActivityResult(
                ActivityResultContracts.RequestPermission()
        ) { isGranted: Boolean ->
            if (isGranted) {
                runTest()
            } else {
                Toast.makeText(
                        this, R.string.missing_record_permission, Toast.LENGTH_LONG).show()
            }
        }
        initFrequencyItems()
        mAudioManager = getSystemService(AudioManager::class.java) as AudioManager
        updateDeviceList()
        mAudioManager.registerAudioDeviceCallback(mDeviceCallback, null)
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
                    DevicePicker(
                            type = AudioManager.GET_DEVICES_INPUTS,
                            devices = mInputDevices,
                            onItemSelected = { entry -> onInputDeviceSelected(entry) },
                            onChannelIndexChanged = {
                                channelIndex -> onInputChannelIndexChanged(channelIndex)
                            }
                    )
                    Spacer(modifier = Modifier.padding(4.dp))
                    Divider(color = Color.Gray, thickness = 1.dp)
                    DevicePicker(
                            type = AudioManager.GET_DEVICES_OUTPUTS,
                            devices = mOutputDevices,
                            onItemSelected = { entry -> onOutputDeviceSelected(entry) },
                            onChannelIndexChanged = {
                                channelIndex -> onOutputChannelIndexChanged(channelIndex) }
                    )
                    Spacer(modifier = Modifier.padding(4.dp))
                    Divider(color = Color.Gray, thickness = 1.dp)
                    Row {
                        Text(text = "Frequency (bin)")
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
                                BINS.forEachIndexed { index, bin ->
                                    DropdownMenuItem(
                                            text = { Text(bin) },
                                            onClick = {
                                                mFundamentalBin = BIN_VALUES[index]
                                                mFrequencyBinText.value = BINS[index]
                                                expanded = false
                                            },
                                            enabled = mFrequencySpinnerEnabled.value
                                    )
                                }
                            }
                            Text(
                                    text = mFrequencyBinText.value, modifier = Modifier
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
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO)
                == PackageManager.PERMISSION_GRANTED) {
            runTest()
        } else {
            mRequestPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    private fun runTest() {
        mStartButtonEnabled.value = false
        mStopButtonEnabled.value = true
        mFrequencySpinnerEnabled.value = false

        mHarmonicAnalyzerFramework = HarmonicAnalyzerFramework()
        checkNotNull(mHarmonicAnalyzerFramework) {
            Toast.makeText(
                this,
                "Failed to init harmonic analyzer framework",
                Toast.LENGTH_LONG).show()
        }
        val harmonicAnalyzerSink = mHarmonicAnalyzerFramework?.harmonicAnalyzerSink
        checkNotNull(harmonicAnalyzerSink) {
            Toast.makeText(this, "Failed to init harmonic analyzer sink", Toast.LENGTH_LONG).show()
        }

        mHarmonicAnalyzerFramework?.setInputDevice(mSelectedInputDevice)
        mHarmonicAnalyzerFramework?.setOutputDevice(mSelectedOutputDevice)
        mHarmonicAnalyzerFramework?.addListener(mListener)

        harmonicAnalyzerSink.mSampleRate = SAMPLE_RATE
        harmonicAnalyzerSink.mFftSize = FFT_SIZE
        harmonicAnalyzerSink.mFundamentalBin = mFundamentalBin

        mParam.value = String.format("Sample Rate = %6d Hz\nFFT size = %d",
                harmonicAnalyzerSink.mSampleRate,
                harmonicAnalyzerSink.mFftSize)

        mHarmonicAnalyzerFramework?.setInputChannelIndex(mSelectedInputChannelIndex)
        mHarmonicAnalyzerFramework?.setOutputChannelIndex(mSelectedOutputChannelIndex)

        mHarmonicAnalyzerFramework?.start()
    }

    private fun onStopTest() {
        mStartButtonEnabled.value = true
        mStopButtonEnabled.value = false
        mFrequencySpinnerEnabled.value = true

        mHarmonicAnalyzerFramework?.stop()
    }

    private fun initFrequencyItems() {
        mFundamentalBin = BIN_VALUES[0]
        mFrequencyBinText.value = BINS[0]
    }

    private fun onInputDeviceSelected(entry: AudioDeviceListEntry) {
        mSelectedInputDevice = entry.deviceInfo
    }

    private fun onOutputDeviceSelected(entry: AudioDeviceListEntry) {
        mSelectedOutputDevice = entry.deviceInfo
    }

    private fun onInputChannelIndexChanged(channelIndex: Int) {
        mSelectedInputChannelIndex = channelIndex
        mHarmonicAnalyzerFramework?.setInputChannelIndex(channelIndex)
    }

    private fun onOutputChannelIndexChanged(channelIndex: Int) {
        mSelectedOutputChannelIndex = channelIndex
        mHarmonicAnalyzerFramework?.setOutputChannelIndex(channelIndex)
    }

    private fun updateDeviceList() {
        val inputDevices = ArrayList<AudioDeviceListEntry>()
        val outputDevices = ArrayList<AudioDeviceListEntry>()
        for (deviceInfo in mAudioManager.getDevices(AudioManager.GET_DEVICES_ALL)) {
            if (deviceInfo.isSource) {
                inputDevices.add(AudioDeviceListEntry(
                    deviceInfo.id, deviceInfo.deviceDisplayName(), deviceInfo))
            } else if (deviceInfo.isSink) {
                outputDevices.add(AudioDeviceListEntry(
                    deviceInfo.id, deviceInfo.deviceDisplayName(), deviceInfo))
            }
        }
        if (inputDevices.isNotEmpty()) {
            inputDevices.add(0, AUTO_SELECTED_DEVICE_ENTRY)
        }
        if (outputDevices.isNotEmpty()) {
            outputDevices.add(0, AUTO_SELECTED_DEVICE_ENTRY)
        }
        mInputDevices.update { inputDevices }
        mOutputDevices.update { outputDevices }
    }

    private inner class DeviceConnectionListener : AudioDeviceCallback() {

        override fun onAudioDevicesAdded(addedDevices: Array<AudioDeviceInfo>) {
            Log.i(TAG, "onAudioDeviceAdded")
            updateDeviceList()
        }

        override fun onAudioDevicesRemoved(removedDevices: Array<AudioDeviceInfo>) {
            Log.i(TAG, "onAudioDevicesRemoved")
            updateDeviceList()
        }
    }

    private inner class MyHarmonicAnalyzerListener : HarmonicAnalyzerListener {
        override fun onMeasurement(analysisCount: Int, results: ArrayList<HarmonicAnalyzer.Result>) {
            val result = results[0] // Only one channel for now
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
