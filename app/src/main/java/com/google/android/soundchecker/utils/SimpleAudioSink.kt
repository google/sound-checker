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
import android.media.AudioTimestamp
import android.media.AudioTrack
import android.util.Log
import android.util.Pair

import java.nio.ByteBuffer
import java.util.ArrayDeque

import com.google.android.exoplayer2.C
import com.google.android.exoplayer2.Format
import com.google.android.exoplayer2.PlaybackParameters
import com.google.android.exoplayer2.audio.AudioAttributes
import com.google.android.exoplayer2.audio.AudioSink
import com.google.android.exoplayer2.audio.AudioSink.SinkFormatSupport
import com.google.android.exoplayer2.audio.AudioSink.WriteException
import com.google.android.exoplayer2.audio.AuxEffectInfo
import com.google.android.exoplayer2.util.MimeTypes
import com.google.android.exoplayer2.util.Util

class SimpleAudioSink internal constructor(
        discover: PlaybackConfigurationDiscover,
        bitPerfectRequired: Boolean) : AudioSink {
    private val mBitPerfectRequired: Boolean
    private var mListener: AudioSink.Listener? = null
    private val mPlaybackConfigurationDiscover: PlaybackConfigurationDiscover
    private var mAudioTrack: AudioTrack? = null
    private var mAudioSessionId = -1
    private var mFormat: AudioFormat? = null
    private var mAttributes: AudioAttributes
    private var mInputFormat: Format? = null
    private var mPendingFormat: AudioFormat? = null
    private var mOutputFormat: AudioFormat? = null
    private var mPlaybackParams: PlaybackParameters
    private var mConverter: PcmConverter? = null
    private var mIsFlushed = false
    private var mIsPlaying = false
    private var mHandledEndOfStream = false
    private var mLastPosition: Long = 0
    private var mLastBuffer: ByteBuffer? = null
    private var mFrameSize: Long = 0
    private var mFramesWritten: Long = 0
    private val mPresentationTimeCollection = ArrayDeque<Pair<Long, Long>>()
    override fun setListener(listener: AudioSink.Listener) {
        mListener = listener
    }

    override fun supportsFormat(format: Format): Boolean {
        return getFormatSupport(format) != AudioSink.SINK_FORMAT_UNSUPPORTED
    }

    override fun getFormatSupport(format: Format): @SinkFormatSupport Int {
        if (MimeTypes.AUDIO_RAW != format.sampleMimeType) {
            return AudioSink.SINK_FORMAT_UNSUPPORTED
        }
        return if (!Util.isEncodingLinearPcm(format.pcmEncoding)) {
            AudioSink.SINK_FORMAT_UNSUPPORTED
        } else AudioSink.SINK_FORMAT_SUPPORTED_DIRECTLY
    }

    override fun getCurrentPositionUs(sourceEnded: Boolean): Long {
        if (!isAudioTrackInitialized) {
            return AudioSink.CURRENT_POSITION_NOT_SET
        }
        val timestamp = AudioTimestamp()
        if (mAudioTrack!!.getTimestamp(timestamp)) {
            while (mPresentationTimeCollection.size > 1) {
                val peekTime = mPresentationTimeCollection.peek()!!
                if (peekTime.first >= timestamp.framePosition) {
                    break
                }
                mPresentationTimeCollection.remove()
            }
            val recentTime = mPresentationTimeCollection.peek()!!
            mLastPosition = (recentTime.second + (timestamp.framePosition - recentTime.first)
                    * C.MICROS_PER_SECOND / mFormat!!.sampleRate)
        }
        return mLastPosition
    }

    @Throws(AudioSink.ConfigurationException::class)
    override fun configure(inputFormat: Format, specifiedBufferSize: Int, outputChannels: IntArray?) {
        Log.i(TAG, "configure, encoding=" + inputFormat.pcmEncoding + " format=" + inputFormat)
        if (MimeTypes.AUDIO_RAW != inputFormat.sampleMimeType) {
            throw AudioSink.ConfigurationException(
                    "Unsupported mimetype:" + inputFormat.sampleMimeType, inputFormat
            )
        }
        val encoding = when (inputFormat.pcmEncoding) {
            C.ENCODING_PCM_8BIT -> AudioFormat.ENCODING_PCM_8BIT
            C.ENCODING_PCM_16BIT -> AudioFormat.ENCODING_PCM_16BIT
            C.ENCODING_PCM_24BIT -> AudioFormat.ENCODING_PCM_24BIT_PACKED
            C.ENCODING_PCM_32BIT -> AudioFormat.ENCODING_PCM_32BIT
            C.ENCODING_PCM_FLOAT -> AudioFormat.ENCODING_PCM_FLOAT
            else -> throw AudioSink.ConfigurationException(
                    "Unsupported encoding:" + inputFormat.pcmEncoding, inputFormat
            )
        }
        val channelMask = when (inputFormat.channelCount) {
            1 -> AudioFormat.CHANNEL_OUT_MONO
            2 -> AudioFormat.CHANNEL_OUT_STEREO
            else -> throw AudioSink.ConfigurationException(
                    "Unsupported channel count:" + inputFormat.channelCount, inputFormat
            )
        }
        if (inputFormat.sampleRate <= 0) {
            throw AudioSink.ConfigurationException(
                    "Invalid sample rate:" + inputFormat.sampleRate, inputFormat
            )
        }
        mPendingFormat = getAudioFormat(encoding, channelMask, inputFormat.sampleRate)
        mInputFormat = inputFormat
        if (mPendingFormat == mFormat) {
            mPendingFormat = null
            return
        }
        mOutputFormat = if (mBitPerfectRequired) {
            mPlaybackConfigurationDiscover.onPlaybackConfigured(mPendingFormat!!)
        } else {
            mPendingFormat
        }
    }

    override fun play() {
        if (isAudioTrackInitialized) {
            mAudioTrack!!.play()
            mIsPlaying = true
        }
    }

    override fun handleDiscontinuity() {
        // Do nothing
    }

    @Throws(AudioSink.InitializationException::class, WriteException::class)
    override fun handleBuffer(
            buffer: ByteBuffer, presentationTimeUs: Long,
            encodedAccessUnitCount: Int,
    ): Boolean {
        if (mPendingFormat != null) {
            if (isAudioTrackInitialized) {
                reset()
            }
            buildAudioTrack(mOutputFormat)
            mFormat = mPendingFormat
            if (mFormat != mOutputFormat) {
                mConverter = PcmConverter(mFormat!!.encoding, mOutputFormat!!.encoding)
            }
            mPendingFormat = null
        }
        if (!isAudioTrackInitialized) {
            return false
        }
        if (buffer !== mLastBuffer) {
            mPresentationTimeCollection.add(Pair(mFramesWritten, presentationTimeUs))
        }
        mLastBuffer = buffer
        val outputBuffer = if (mConverter == null) buffer else mConverter!!.getOutputBuffer(buffer)
        val bytesWritten =
                mAudioTrack!!.write(outputBuffer, outputBuffer.remaining(), AudioTrack.WRITE_NON_BLOCKING)
        if (bytesWritten < 0) {
            Log.e(TAG, "Error when writing:$bytesWritten")
            return false
        }
        val framesWritten = bytesWritten / mFrameSize.toInt()
        if (outputBuffer !== buffer) {
            buffer.position(buffer.position() + framesWritten * mFormat!!.frameSizeInBytes)
        }
        mFramesWritten += framesWritten.toLong()
        return !buffer.hasRemaining()
    }

    @Throws(WriteException::class)
    override fun playToEndOfStream() {
        flush()
    }

    override fun isEnded(): Boolean {
        return !isAudioTrackInitialized || mHandledEndOfStream && !hasPendingData()
    }

    override fun hasPendingData(): Boolean {
        return isAudioTrackInitialized && (mIsPlaying || !mIsFlushed)
    }

    init {
        mPlaybackConfigurationDiscover = discover
        mAttributes = AudioAttributes.Builder().setUsage(C.USAGE_MEDIA).build()
        mPlaybackParams = PlaybackParameters(1.0f, 1.0f)
        mBitPerfectRequired = bitPerfectRequired
    }

    override fun setPlaybackParameters(playbackParameters: PlaybackParameters) {
        mPlaybackParams = PlaybackParameters(
                Util.constrainValue(playbackParameters.speed, MIN_PLAYBACK_SPEED, MAX_PLAYBACK_SPEED),
                Util.constrainValue(playbackParameters.pitch, MIN_PITCH, MAX_PITCH)
        )
    }

    override fun getPlaybackParameters(): PlaybackParameters {
        return mPlaybackParams
    }

    override fun setSkipSilenceEnabled(skipSilenceEnabled: Boolean) {
        // Do nothing
    }

    override fun getSkipSilenceEnabled(): Boolean {
        return false
    }

    override fun setAudioAttributes(audioAttributes: AudioAttributes) {
        if (mAttributes == audioAttributes) {
            return
        }
        mAttributes = audioAttributes
        flush()
    }

    override fun getAudioAttributes(): AudioAttributes? {
        return mAttributes
    }

    override fun setAudioSessionId(audioSessionId: Int) {
        mAudioSessionId = audioSessionId
        flush()
    }

    override fun setAuxEffectInfo(auxEffectInfo: AuxEffectInfo) {
        // Do nothing
    }

    override fun enableTunnelingV21() {
        // Do nothing
    }

    override fun disableTunneling() {
        // Do nothing
    }

    override fun setVolume(volume: Float) {
        if (isAudioTrackInitialized) {
            mAudioTrack!!.setVolume(volume)
        }
    }

    override fun pause() {
        if (isAudioTrackInitialized) {
            mAudioTrack!!.pause()
            mIsPlaying = false
            mIsFlushed = false
        }
    }

    override fun flush() {
        if (isAudioTrackInitialized) {
            pause()
            mAudioTrack!!.flush()
            mIsFlushed = true
        }
    }

    override fun experimentalFlushWithoutAudioTrackRelease() {
        // Do nothing
    }

    override fun reset() {
        if (isAudioTrackInitialized) {
            flush()
            mAudioTrack?.release()
            mAudioTrack = null
            mHandledEndOfStream = false
            mLastBuffer = null
        }
    }

    private val isAudioTrackInitialized: Boolean
        get() = mAudioTrack != null

    private fun getAudioFormat(encoding: Int, channelMask: Int, sampleRate: Int): AudioFormat {
        return AudioFormat.Builder()
                .setSampleRate(sampleRate)
                .setEncoding(encoding)
                .setChannelMask(channelMask)
                .build()
    }

    @Throws(AudioSink.InitializationException::class)
    private fun buildAudioTrack(format: AudioFormat?) {
        val builder = AudioTrack.Builder()
                .setAudioAttributes(mAttributes.audioAttributesV21.audioAttributes)
                .setAudioFormat(format!!)
        if (mAudioSessionId > 0) {
            builder.setSessionId(mAudioSessionId)
        }
        mAudioTrack = builder.build()
        checkNotNull(mAudioTrack) {
            Log.e(TAG, "Failed to create AudioTrack")
        }
        val state = mAudioTrack!!.state
        if (state != AudioTrack.STATE_INITIALIZED) {
            reset()
            throw AudioSink.InitializationException(
                    state, format.sampleRate, format.channelMask, 0,
                    mInputFormat!!, false, null
            )
        }
        mLastPosition = 0
        mIsFlushed = false
        mIsPlaying = false
        mLastBuffer = null
        mFramesWritten = 0
        mFrameSize = format.frameSizeInBytes.toLong()
        mPresentationTimeCollection.clear()
    }

    companion object {
        private const val TAG = "SimpleAudioSink"
        const val MIN_PLAYBACK_SPEED = 0.1f
        const val MAX_PLAYBACK_SPEED = 8f
        const val MIN_PITCH = 0.1f

        /** The maximum allowed pitch factor. Higher values will be constrained to fall in range.  */
        const val MAX_PITCH = 8f
    }
}
