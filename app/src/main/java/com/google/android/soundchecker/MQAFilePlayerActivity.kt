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
import android.content.Context
import android.os.Build
import android.os.Bundle
import android.widget.Toast

import com.google.android.exoplayer2.DefaultRenderersFactory
import com.google.android.exoplayer2.ExoPlayer
import com.google.android.exoplayer2.MediaItem
import com.google.android.exoplayer2.audio.AudioSink

import com.google.android.soundchecker.utils.SimpleAudioSink

@TargetApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
class MQAFilePlayerActivity : BitPerfectFilePlayerActivity() {

    private var mExoPlayer: ExoPlayer? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        mTag = "MQAFilePlayerActivity"
    }

    override fun startPlayback() {
        super.startPlayback()
        checkNotNull(mFile) {
            Toast.makeText(this, "The selected file is null", Toast.LENGTH_LONG).show()
        }
        val file = mFile!!
        mExoPlayer = ExoPlayer.Builder(this, MyRendererFactory(this)).build()
        val exoPlayer = mExoPlayer!!
        exoPlayer.addMediaItem(MediaItem.fromUri(file))
        exoPlayer.prepare()
        exoPlayer.play()
    }

    override fun stopPlayback() {
        mExoPlayer?.stop()
        super.stopPlayback()
    }

    inner class MyRendererFactory(context: Context?) :
            DefaultRenderersFactory(context!!) {
        override fun buildAudioSink(
                context: Context, enableFloatOutput: Boolean,
                enableAudioTrackPlaybackParams: Boolean, enableOffload: Boolean,
        ): AudioSink {
            return SimpleAudioSink(mPlaybackConfigurationDiscover, true)
        }
    }
}