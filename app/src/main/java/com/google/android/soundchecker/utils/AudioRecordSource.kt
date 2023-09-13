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

import android.annotation.SuppressLint
import android.media.AudioDeviceInfo
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Log
import android.widget.Toast

class AudioRecordSource(var preferredDevice: AudioDeviceInfo? = null,
                        var selectedChannelIndex: Int = -1) : AudioSource() {
    private var mAudioRecord: AudioRecord? = null
    private var mTransferBuffer: FloatArray? = null

    init {
        mEncoding = AudioFormat.ENCODING_PCM_FLOAT
    }

    // The permission must be granted before opening
    @SuppressLint("MissingPermission")
    fun open() {
        mAudioRecord = AudioRecord.Builder()
                .setAudioFormat(getAudioFormat())
                .setAudioSource(AUDIO_SOURCE)
                .build()
        if (mAudioRecord == null) {
            throw RuntimeException("Could not make the AudioRecord! format = ${getAudioFormat()}")
        }
        mAudioRecord!!.preferredDevice = preferredDevice
    }

    private fun close() {
        if (mAudioRecord != null) {
            mAudioRecord!!.release()
            mAudioRecord = null
        }
    }

    override fun render(buffer: FloatArray, offset: Int, stride: Int, numFrames: Int): Int {
        val numSamples = numFrames * getChannelCount()
        if (mTransferBuffer == null || mTransferBuffer!!.size < numSamples) {
            mTransferBuffer = FloatArray(numSamples)
        }
        checkNotNull(mTransferBuffer) {
            Log.e(TAG, "Failed to init internal buffer")
        }
        val framesRead = pull(mTransferBuffer!!, numFrames)
        // Copy just the channel we want.
        var srcIndex = selectedChannelIndex
        var destIndex = offset
        for (i in 0 until framesRead) {
            buffer[destIndex] = mTransferBuffer!![srcIndex]
            srcIndex += getChannelCount()
            destIndex += stride
        }
        return framesRead
    }

    override fun pull(buffer: FloatArray, numFrames: Int): Int {
        if (mAudioRecord == null) {
            Log.w(TAG, "pull data while AudioRecord is null")
            return 0
        }
        val sampleReads = mAudioRecord!!.read(
                buffer, 0, numFrames * getChannelCount(), AudioRecord.READ_BLOCKING)
        if (sampleReads < 0) return sampleReads
        return sampleReads / getChannelCount()
    }

    fun start() {
        open()
        mAudioRecord?.startRecording()
    }

    fun stop() {
        mAudioRecord?.stop()
        close()
    }

    val routedDevice: AudioDeviceInfo?
        get() = if (mAudioRecord == null) null else mAudioRecord?.routedDevice

    companion object {
        private const val TAG = "AudioRecordSource"
        private const val AUDIO_SOURCE = MediaRecorder.AudioSource.VOICE_RECOGNITION
    }
}