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
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.unit.dp

class MainActivity : ComponentActivity() {
    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val items = listOf(
                Pair(getString(R.string.mqa_player), MQAFilePlayerActivity::class.java),
                Pair(getString(R.string.dsd_player), DSDFilePlayerActivity::class.java),
                Pair(getString(R.string.test_resonance), ResonanceActivity::class.java),
                Pair(getString(R.string.test_harmonic_distortion), HarmonicAnalyzerActivity::class.java)
        )
        setContent {
            Scaffold(
                    topBar = {
                        TopAppBar(title = { Text(getString(R.string.app_name)) },
                                  modifier = Modifier.shadow(elevation = 4.dp))
                    }
            ) { paddingValues ->
                TestsList(tests = items, modifier = Modifier.padding(paddingValues)) { clazz ->
                    startActivity(Intent(this, clazz))
                }
            }
        }
    }
}

@Composable
fun TestsList(tests: List<Pair<String, Class<out ComponentActivity>>>,
              modifier: Modifier,
              onItemClick: (Class<out ComponentActivity>) -> Unit) {
    LazyColumn(modifier = modifier,
               verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(tests) { test ->
            Button(
                shape = RoundedCornerShape(10.dp),
                onClick = { onItemClick(test.second) },
                modifier = Modifier.fillMaxWidth())
            {
                Text(text = test.first, style = MaterialTheme.typography.headlineSmall)
            }
        }
    }
}

/*@Composable
fun TestItem(testInfo: Pair<String, Class<out ComponentActivity>>) {
    val context = LocalContext.current
    Button(
            shape = RoundedCornerShape(10.dp),
            onClick = {
                val intent = Intent(context, testInfo.second)
                context.startActivity(intent)
            },
            modifier = Modifier
                    .padding(all = 4.dp)
                    .fillMaxWidth()
    ) {
        Text(text = testInfo.first,
             style = MaterialTheme.typography.headlineSmall)
    }
}*/
