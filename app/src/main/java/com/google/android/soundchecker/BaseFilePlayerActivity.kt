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

import android.Manifest.permission.POST_NOTIFICATIONS
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioManager.OnAudioFocusChangeListener
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import android.os.Message
import android.os.SystemClock
import android.provider.OpenableColumns
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.ui.unit.dp

import com.google.android.soundchecker.utils.AudioErrorCallback

/**
 * Base class for selecting file and playback. Note this file is a base class for UI related
 * operation. It doesn't contain the functionality of playback. The child class should implement
 * playback start and stop by overriding `onPlaybackButtonClicked`.
 */
open class BaseFilePlayerActivity : ComponentActivity(), OnAudioFocusChangeListener {
    companion object {
        // Messages that are handled by handler
        const val ON_PLAYBACK_STATE_CHANGED = 1
        const val START_PLAYBACK = 2
        const val STOP_PLAYBACK = 3
        const val UPDATE_UI = 10
    }

    protected val mAttrs = AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).build()
    protected var mTag = "BaseFilePlayerActivity"

    protected var mFile: Uri? = null
    protected var mFileName: String? = null
    protected var mIsPlaying = false
    protected lateinit var mAudioManager: AudioManager

    protected val mErrorCallback = FilePlaybackErrorCallback()

    protected var mRequestedEncoding = mutableStateOf(AudioFormat.ENCODING_INVALID)
    protected var mRequestedEncodingStr =
        mutableStateOf(toFriendlyEncoding(mRequestedEncoding.value))
    protected var mEncodingDropdownMenuEnabled = mutableStateOf(true)

    private lateinit var mFocusRequest: AudioFocusRequest

    private val mMsgHandlerThread = HandlerThread("audioSample_FilePlayer_MsgHandler")
    private lateinit var mHandler: MsgHandler

    private var mMsg = mutableStateOf("")
    private var mPlaybackButtonText = mutableStateOf("")

    private var mPlaybackErrorMsg = mutableStateOf("")

    private var mHasLostAudioFocus = false

    private val mBecomingNoisyReceiver = BecomingNoisyReceiver()
    private val mPlaybackStateChangedReceiver = PlaybackStateChangedReceiver()

    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            Scaffold(
                    topBar = {
                        TopAppBar(title = { Text(title.toString()) },
                                modifier = Modifier.shadow(elevation = 4.dp))
                    }
            ) { paddingValues ->
                Column(modifier = Modifier.padding(paddingValues = paddingValues)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        var fileName by remember { mutableStateOf("") }
                        val pickFileLauncher = rememberLauncherForActivityResult(
                                ActivityResultContracts.GetContent()
                        ) { uri ->
                            if (uri != null) {
                                mFile = uri
                                fileName = getSelectedFileName()
                                mMsg.value = getSelectedFileUnplayableReason()
                            }
                        }
                        Button(onClick = {
                            pickFileLauncher.launch("*/*")
                        }) {
                            Text(text = getString(R.string.select))
                        }
                        Spacer(modifier = Modifier.width(4.dp))

                        Text(text = fileName)
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    mMsg.value = getSelectedFileUnplayableReason()
                    mPlaybackButtonText.value = if (mIsPlaying) getString(R.string.stop) else
                        getString(R.string.start)
                    val msg by mMsg
                    val playbackButtonText by mPlaybackButtonText
                    Button(
                            onClick = { onPlaybackButtonClicked() },
                            enabled = msg.isEmpty()
                    ) {
                        Text(text = playbackButtonText)
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    if (getPotentialEncodings().isNotEmpty()) {
                        Row {
                            Text(text = "Encoding")
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
                                    getPotentialEncodings().forEachIndexed { _, encoding ->
                                        DropdownMenuItem(
                                            text = { Text(toFriendlyEncoding(encoding)) },
                                            onClick = {
                                                mRequestedEncoding.value = encoding
                                                mRequestedEncodingStr.value =
                                                    toFriendlyEncoding(mRequestedEncoding.value)
                                                expanded = false
                                            },
                                            enabled = mEncodingDropdownMenuEnabled.value
                                        )
                                    }
                                }
                                Text(
                                    text = mRequestedEncodingStr.value,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable(onClick = { expanded = true })
                                        .background(
                                            Color.Gray
                                        )
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    Text(text = msg)

                    val playbackErrorMsg by mPlaybackErrorMsg
                    Text(text = playbackErrorMsg)
                }
            }
        }
        mMsgHandlerThread.start()
        mHandler = MsgHandler(mMsgHandlerThread.looper)
        mAudioManager = getSystemService(AudioManager::class.java) as AudioManager
        mFocusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                .setAudioAttributes(mAttrs)
                .setAcceptsDelayedFocusGain(true)
                .setWillPauseWhenDucked(false)
                .setOnAudioFocusChangeListener(this, mHandler)
                .build()
        checkAndRequestNotificationPermission()
        val intent = Intent(this, FilePlayerService::class.java)
        intent.action = FilePlayerService.ACTION_START_SERVICE
        startForegroundService(intent)

        registerReceiver(mBecomingNoisyReceiver,
                IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY))

        val playbackStateIntentFilter = IntentFilter(FilePlayerService.ACTION_START_PLAYBACK)
        playbackStateIntentFilter.addAction(FilePlayerService.ACTION_STOP_PLAYBACK)
        registerReceiver(mPlaybackStateChangedReceiver, playbackStateIntentFilter,
                         RECEIVER_EXPORTED)
    }

    override fun onDestroy() {
        unregisterReceiver(mBecomingNoisyReceiver)
        unregisterReceiver(mPlaybackStateChangedReceiver)
        mAudioManager.abandonAudioFocusRequest(mFocusRequest)
        // Send the stop playback request via the message handler as it is
        // originally started from there. For the ExoPlayer use case, it can
        // only be stopped from the same thread.
        sendStopPlaybackMsg(0)
        mMsgHandlerThread.quitSafely()
        val intent = Intent(this, FilePlayerService::class.java)
        intent.action = FilePlayerService.ACTION_STOP_SERVICE
        startForegroundService(intent)
        super.onDestroy()
    }

    fun getHandler(): Handler {
        return mHandler
    }

    /**
     * Returns the reason why the current file is not playable. Returns empty string if the selected
     * file is playable.
     */
    open fun getSelectedFileUnplayableReason(): String {
        if (mFile == null) {
            return getString(R.string.file_not_selected)
        }
        return ""
    }

    open fun startPlayback() {
        mPlaybackErrorMsg.value = ""
        mAudioManager.requestAudioFocus(mFocusRequest)
        mHasLostAudioFocus = false
        mIsPlaying = true
        mPlaybackButtonText.value = getString(R.string.stop)
        val intent = Intent(this, FilePlayerService::class.java)
        intent.action = FilePlayerService.ACTION_PLAYBACK_STARTED
        intent.putExtra(FilePlayerService.TITLE, mTag)
        intent.putExtra(FilePlayerService.SONG_NAME, mFileName)
        startForegroundService(intent)
    }

    open fun stopPlayback() {
        mIsPlaying = false
        mPlaybackButtonText.value = getString(R.string.start)
        val intent = Intent(this, FilePlayerService::class.java)
        intent.action = FilePlayerService.ACTION_PLAYBACK_STOPPED
        intent.putExtra(FilePlayerService.TITLE, mTag)
        intent.putExtra(FilePlayerService.SONG_NAME, mFileName)
        startForegroundService(intent)
    }

    open fun getStartDelayAfterRegainingAudioFocus(): Int {
        return 0
    }

    open fun getPotentialEncodings(): IntArray {
        return IntArray(0)
    }

    fun toFriendlyEncoding(encoding: Int): String {
        return when (encoding) {
            AudioFormat.ENCODING_PCM_32BIT -> "PCM_I32"
            AudioFormat.ENCODING_PCM_24BIT_PACKED -> "PCM_I24"
            else -> "UNKNOWN"
        }
    }

    private fun getSelectedFileName(): String {
        if (mFile == null) {
            return ""
        }
        val file = mFile!!
        val cursor = contentResolver.query(file, null, null, null, null)
        checkNotNull(cursor) {
            Toast.makeText(this, "Cannot get name of the selected file", Toast.LENGTH_LONG).show()
        }
        val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
        cursor.moveToFirst()
        mFileName = cursor.getString(index)
        cursor.close()
        return mFileName!!
    }

    private fun onPlaybackButtonClicked() {
        sendMsg(ON_PLAYBACK_STATE_CHANGED, 0, 0, null, 0)
    }

    private fun sendStartPlaybackMsg(delay: Int) {
        sendMsg(START_PLAYBACK, 0, 0, null, delay)
    }

    protected fun sendStopPlaybackMsg(delay: Int) {
        sendMsg(STOP_PLAYBACK, 0, 0, null, delay)
    }

    protected fun sendUpdateUIMsg(delay: Int) {
        sendMsg(UPDATE_UI, 0, 0, null, delay)
    }

    private fun sendMsg(msg: Int, arg1: Int, arg2: Int, obj: Object?, delay: Int) {
        val time = SystemClock.uptimeMillis() + delay
        mHandler.sendMessageAtTime(mHandler.obtainMessage(msg, arg1, arg2, obj), time)
    }

    private fun clearMsg(msg: Int) {
        mHandler.removeMessages(msg)
    }

    inner class MsgHandler constructor(
            looper: Looper
    ) : Handler(looper) {
        override fun handleMessage(msg: Message) {
            when (msg.what) {
                ON_PLAYBACK_STATE_CHANGED -> {
                    if (mIsPlaying) {
                        stopPlayback()
                    } else {
                        startPlayback()
                    }
                }

                START_PLAYBACK -> {
                    if (!mIsPlaying) { // Only start when it is not playing
                        startPlayback()
                    }
                }

                STOP_PLAYBACK -> {
                    if (mIsPlaying) { // Only stop when it is playing
                        stopPlayback()
                    }
                }

                UPDATE_UI -> {
                    mMsg.value = getSelectedFileUnplayableReason()
                    if (!mIsPlaying) {
                        mPlaybackButtonText.value = getString(R.string.start)
                    } else {
                        mPlaybackButtonText.value = getString(R.string.stop)
                    }
                }

                else -> {
                    // Undefined message, ignore
                }
            }
        }
    }

    override fun onAudioFocusChange(focusChanged: Int) {
        when (focusChanged) {
            AudioManager.AUDIOFOCUS_LOSS,
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> {
                clearMsg(START_PLAYBACK)
                sendStopPlaybackMsg(0)
                mHasLostAudioFocus = true
            }

            AudioManager.AUDIOFOCUS_GAIN -> {
                sendStartPlaybackMsg(
                    if (mHasLostAudioFocus) getStartDelayAfterRegainingAudioFocus() else 0)
                mHasLostAudioFocus = false
            }

            else -> {
                // Need to handle other cases
            }
        }
    }

    inner class BecomingNoisyReceiver : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (AudioManager.ACTION_AUDIO_BECOMING_NOISY == intent.action) {
                // The external device is disconnected, stop playback.
                sendStopPlaybackMsg(0)
            }
        }
    }

    inner class FilePlaybackErrorCallback : AudioErrorCallback() {
        override fun onError(error: Int, msg: String) {
            if (error != AudioErrorCallback.SUCCESS) {
                Log.e(mTag, "Stop playback when receiving error=$error, msg=$msg")
                mPlaybackErrorMsg.value = msg
                stopPlayback()
            }
        }
    }

    inner class PlaybackStateChangedReceiver : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                FilePlayerService.ACTION_START_PLAYBACK -> {
                    sendStartPlaybackMsg(0)
                }

                FilePlayerService.ACTION_STOP_PLAYBACK -> {
                    sendStopPlaybackMsg(0)
                }

                else -> {
                    // Unknown action, ignore
                }
            }
        }
    }

    private fun checkAndRequestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            checkSelfPermission(POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                registerForActivityResult(
                        ActivityResultContracts.RequestPermission()) { }.launch(POST_NOTIFICATIONS)
        }
    }
}
