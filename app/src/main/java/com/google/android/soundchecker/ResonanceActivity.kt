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

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.unit.dp

import com.google.android.soundchecker.utils.AudioTrackSink
import com.google.android.soundchecker.utils.MultiSineSource
import com.google.android.soundchecker.utils.ui.PortFader

class ResonanceActivity : ComponentActivity() {

    private val mAudioSink = AudioTrackSink()
    private val mSineSource = MultiSineSource()

    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        mSineSource.connect(mAudioSink)
        mSineSource.addPartial(100.0f, 250.0f, 400.0f)
        mSineSource.addPartial(300.0f, 750.0f, 1200.0f)
        mSineSource.addPartial(1000.0f, 2500.0f, 4000.0f)
        setContent {
            Scaffold(
                    topBar = {
                        TopAppBar(
                                title = { Text(title.toString()) },
                                modifier = Modifier.shadow(elevation = 4.dp)
                        )
                    }
            ) { paddingValues ->
                Column(modifier = Modifier.padding(paddingValues = paddingValues)) {
                    for (i in 0 until mSineSource.size()) {
                        val freqPort = mSineSource.getFrequencyPort(i)
                        PortFader(controlPort = freqPort,
                                verticalAlignment = Alignment.CenterVertically)
                        Spacer(modifier = Modifier.width(4.dp))
                        val amplitudePort = mSineSource.getAmplitudePort(i)
                        PortFader(controlPort = amplitudePort,
                                verticalAlignment = Alignment.CenterVertically)
                        Spacer(modifier = Modifier.width(4.dp))
                    }
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        mAudioSink.start()
    }

    override fun onStop() {
        mAudioSink.stop()
        super.onStop()
    }
}
