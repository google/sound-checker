/**
 * Copyright 2024 Google LLC
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

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat

class FilePlayerService : Service() {
    companion object {
        private const val TAG = "FilePlayerService"

        private const val NOTIFICATION_CHANNEL_ID = "SoundChecker"
        private const val CHANNEL_NAME = "FilePlayer"
        private const val NOTIFICATION_ID = 1

        const val TITLE = "title"
        const val SONG_NAME = "song_name"

        const val ACTION_START_SERVICE = "soundchecker.action.START_FILE_PLAYER_SERVICE"
        const val ACTION_STOP_SERVICE = "soundchecker.action.STOP_FILE_PLAYER_SERVICE"
        const val ACTION_START_PLAYBACK = "soundchecker.action.START_PLAYBACK"
        const val ACTION_PLAYBACK_STARTED = "soundchecker.action.PLAYBACK_STARTED"
        const val ACTION_STOP_PLAYBACK = "soundchecker.action.STOP_PLAYBACK"
        const val ACTION_PLAYBACK_STOPPED = "soundchecker.action.PLAYBACK_STOPPED"
    }

    private var mTitle: String = ""
    private var mSongName: String = ""

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START_SERVICE -> {
                startForeground(
                    NOTIFICATION_ID, getNotification(""),
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
                )
            }
            ACTION_PLAYBACK_STARTED -> {
                mTitle = intent.getStringExtra(TITLE).toString()
                mSongName = intent.getStringExtra(SONG_NAME).toString()
                startForeground(NOTIFICATION_ID, getNotification(ACTION_STOP_PLAYBACK),
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
            }
            ACTION_PLAYBACK_STOPPED -> {
                mTitle = intent.getStringExtra(TITLE).toString()
                mSongName = intent.getStringExtra(SONG_NAME).toString()
                startForeground(NOTIFICATION_ID, getNotification(ACTION_START_PLAYBACK),
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
            }
            ACTION_STOP_SERVICE -> {
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
            else -> {
                Log.e(TAG, "Unknown action=${intent?.action}")
            }
        }
        return START_NOT_STICKY
    }

    override fun onBind(p0: Intent?): IBinder? {
        return null
    }

    private fun getNotification(pendingAction: String): Notification {
        val channel = NotificationChannel(
                NOTIFICATION_CHANNEL_ID, CHANNEL_NAME, NotificationManager.IMPORTANCE_NONE)

        val notificationManager = getSystemService(
            NotificationManager::class.java
        )
        notificationManager.createNotificationChannel(channel)

        val builder = NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
                .setContentTitle("SoundChecker $mTitle")
                .setContentText("Playing $mSongName")
                .setSmallIcon(R.drawable.ic_file_playback_notification)

        if (pendingAction == ACTION_STOP_PLAYBACK) {
            val stopPlaybackIntent = Intent(pendingAction)
            val pendingStopPlaybackIntent = PendingIntent.getBroadcast(
                    this, 0, stopPlaybackIntent, PendingIntent.FLAG_IMMUTABLE)
            builder.addAction(R.drawable.stop_playback,
                    getString(R.string.stop), pendingStopPlaybackIntent)
        } else if (pendingAction == ACTION_START_PLAYBACK) {
            val startPlaybackIntent = Intent(pendingAction)
            val pendingStartPlaybackIntent = PendingIntent.getBroadcast(
                    this, 0, startPlaybackIntent, PendingIntent.FLAG_IMMUTABLE)
            builder.addAction(R.drawable.start_playback,
                    getString(R.string.start), pendingStartPlaybackIntent)
        }

        return builder.build()
    }

}
