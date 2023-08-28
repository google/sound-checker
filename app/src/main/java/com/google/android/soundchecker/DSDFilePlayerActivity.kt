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

import android.annotation.TargetApi
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.widget.Toast

import com.google.android.soundchecker.dsd.DopWrapper
import com.google.android.soundchecker.utils.AudioTrackSink
import com.google.android.soundchecker.dsd.DsfReader

@TargetApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
class DSDFilePlayerActivity : BitPerfectFilePlayerActivity() {
    private var mFileReader: DsfReader? = null
    private var mDop: DopWrapper? = null
    private var mAudioTrackSink: AudioTrackSink? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        mTag = "DsdFilePlayerActivity"
    }

    override fun start() {
        super.start()
        mFileReader = DsfReader(contentResolver.openInputStream(mFile!!)!!)
        if (!mFileReader!!.prepareForReadingData()) {
            Toast.makeText(this, getString(R.string.unable_to_open_selected_file), Toast.LENGTH_LONG).show()
            Log.e(mTag, getString(R.string.unable_to_open_selected_file))
            sendStopPlaybackMsg(0)
            return
        }
        if (mFileReader!!.getAudioFormatBuilder() == null) {
            Toast.makeText(this, getString(R.string.unable_to_parse_selected_file), Toast.LENGTH_LONG).show()
            Log.e(mTag, getString(R.string.unable_to_parse_selected_file))
            sendStopPlaybackMsg(0)
            return
        }
        val format = mFileReader!!.getAudioFormatBuilder()!!.build()
        val deviceFormat = mPlaybackConfigurationDiscover.onPlaybackConfigured(format)
        mDop = DopWrapper(mFileReader!!, deviceFormat.encoding)
        mAudioTrackSink = AudioTrackSink()
        mDop!!.connect(mAudioTrackSink!!)
        mAudioTrackSink!!.mEncoding = deviceFormat.encoding
        mAudioTrackSink!!.mChannelMask = deviceFormat.channelMask
        mAudioTrackSink!!.mSampleRate = deviceFormat.sampleRate
        mAudioTrackSink!!.start()
    }

    override fun stop() {
        super.stop()
        if (mAudioTrackSink != null) {
            mAudioTrackSink!!.stop()
        }
    }
}