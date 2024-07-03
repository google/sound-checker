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
    private var mAudioTrackSink: AudioTrackSink? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        mTag = "DsdFilePlayerActivity"
    }

    override fun startPlayback() {
        super.startPlayback()
        checkNotNull(mFile) {
            Toast.makeText(this, "Selected file is null", Toast.LENGTH_LONG).show()
        }
        val fileReader = DsfReader(contentResolver.openInputStream(mFile!!)!!)
        if (!fileReader.prepareForReadingData()) {
            Toast.makeText(this, getString(R.string.unable_to_open_selected_file), Toast.LENGTH_LONG).show()
            Log.e(mTag, getString(R.string.unable_to_open_selected_file))
            sendStopPlaybackMsg(0)
            return
        }
        if (fileReader.getAudioFormatBuilder() == null) {
            Toast.makeText(this, getString(R.string.unable_to_parse_selected_file), Toast.LENGTH_LONG).show()
            Log.e(mTag, getString(R.string.unable_to_parse_selected_file))
            sendStopPlaybackMsg(0)
            return
        }
        val formatBuilder = fileReader.getAudioFormatBuilder()
        checkNotNull(formatBuilder) {
            Toast.makeText(
                this, "Unable to get audio format for the selected file", Toast.LENGTH_LONG).show()
        }
        val deviceFormat = mPlaybackConfigurationDiscover.onPlaybackConfigured(
            formatBuilder.build())
        val dopWrapper = DopWrapper(fileReader, deviceFormat.encoding)
        mAudioTrackSink = AudioTrackSink()
        val audioTrackSink = mAudioTrackSink!!
        dopWrapper.connect(audioTrackSink)
        audioTrackSink.mEncoding = deviceFormat.encoding
        audioTrackSink.mChannelMask = deviceFormat.channelMask
        audioTrackSink.mSampleRate = deviceFormat.sampleRate
        audioTrackSink.start()
    }

    override fun stopPlayback() {
        super.stopPlayback()
        mAudioTrackSink?.stop()
    }
}
