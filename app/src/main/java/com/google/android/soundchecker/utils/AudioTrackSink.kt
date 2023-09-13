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

import android.media.AudioAttributes
import android.media.AudioDeviceInfo
import android.media.AudioFormat
import android.media.AudioTrack
import android.util.Log

import java.nio.ByteBuffer

class AudioTrackSink(var preferredDevice: AudioDeviceInfo? = null,
                     var selectedChannelIndex: Int = -1) : AudioSink() {
    private var mAudioSource: AudioSource? = null
    private var mAudioTrack: AudioTrack? = null
    private var mFramesWritten: Long = 0
    private var mThread: AudioThread? = null
    private var mBuffer: ByteArray? = null
    private var mFloatBuffer: FloatArray? = null

    init {
        // Default values, can be override
        mEncoding = AudioFormat.ENCODING_PCM_FLOAT
    }

    override fun setSource(source: AudioSource?) {
        mAudioSource = source
    }

    private fun open() {
        mThread = object : AudioThread() {
            override fun run() {
                runAudioLoop()
            }
        }
        if (mBuffer == null && selectedChannelIndex < 0) {
            mBuffer = ByteArray(getBytesPerFrame() * FRAMES_PER_BURST)
        }
        if (mFloatBuffer == null && selectedChannelIndex >= 0) {
            mFloatBuffer = FloatArray(getChannelCount() * FRAMES_PER_BURST)
        }
        if (mAudioTrack == null) {
            mAudioTrack = createAudioTrack(0)
            mAudioTrack?.preferredDevice = preferredDevice
        }
    }

    private fun close() {
        mAudioTrack?.release()
        mAudioTrack = null
    }

    override fun start() {
        open()
        checkNotNull(mAudioTrack) {
            Log.e(TAG, "Failed to create AudioTrack")
        }
        mAudioTrack!!.play()
        checkNotNull(mThread) {
            Log.e(TAG, "Failed to create AudioThread")
        }
        mThread!!.start()
    }

    override fun stop() {
        mThread?.stop()
        mAudioTrack?.stop()
        mFramesWritten = 0
        close()
    }

    fun runAudioLoop() {
        while (mThread?.isEnabled() == true) {
            val audioSource = mAudioSource!!
            val audioTrack = mAudioTrack!!
            val numWritten = if (selectedChannelIndex < 0) {
                val buffer = mBuffer!!
                val framesRead = audioSource.pull(buffer, FRAMES_PER_BURST)
                val sizeInByte = framesRead * getBytesPerFrame()
                val byteBuffer = ByteBuffer.wrap(buffer, 0, sizeInByte)
                audioTrack.write(byteBuffer, sizeInByte, AudioTrack.WRITE_BLOCKING)
            } else {
                val floatBuffer = mFloatBuffer!!
                val framesRead = audioSource.render(
                        floatBuffer,
                        selectedChannelIndex,
                        getChannelCount(),
                        FRAMES_PER_BURST
                )
                mAudioTrack!!.write(
                        floatBuffer,
                        0,
                        framesRead * getChannelCount(),
                        AudioTrack.WRITE_BLOCKING)
            }
            if (numWritten < 0) {
                Log.e(TAG, "Failed to write, result=$numWritten")
                break
            }
        }
    }

    private fun createAudioTrack(bufferSizeInFrames: Int): AudioTrack {
        val bufferSizeBytes: Int
        if (bufferSizeInFrames <= 0) {
            val minBufferSizeBytes = AudioTrack.getMinBufferSize(
                    mSampleRate, AudioFormat.CHANNEL_OUT_MONO, mEncoding
            ) * getChannelCount()
            bufferSizeBytes = 8 * minBufferSizeBytes
            Log.i(
                    TAG, ("createAudioTrack: bufferSizeInFrames = " + bufferSizeInFrames
                    + ", minBufferSizeBytes = " + minBufferSizeBytes
                    + ", bufferSizeBytes = " + bufferSizeBytes)
            )
        } else {
            bufferSizeBytes = bufferSizeInFrames * getChannelCount() * BYTES_PER_FLOAT
            Log.i(
                    TAG, ("createAudioTrack: bufferSizeInFrames = " + bufferSizeInFrames
                    + ", bufferSizeBytes = " + bufferSizeBytes)
            )
        }
        val attributesBuilder: AudioAttributes.Builder = AudioAttributes.Builder()
                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
        val attributes: AudioAttributes = attributesBuilder.build()
        val format: AudioFormat = getAudioFormat()
        return AudioTrack.Builder()
                .setAudioAttributes(attributes)
                .setAudioFormat(format)
                .setBufferSizeInBytes(bufferSizeBytes)
                .build()
    }

    val routedOutputDevice: AudioDeviceInfo?
        get() {
            if (mAudioTrack == null) return null else return mAudioTrack!!.routedDevice
        }

    companion object {
        private const val TAG = "AudioTrackSink"
        private const val BYTES_PER_FLOAT = 4
        private const val FRAMES_PER_BURST = 4 * 48
    }
}