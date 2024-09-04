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
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder
import androidx.core.app.NotificationCompat

class FilePlayerService : Service() {
    companion object {
        private const val NOTIFICATION_CHANNEL_ID = "SoundChecker"
        private const val CHANNEL_NAME = "FilePlayer"
        private const val NOTIFICATION_ID = 1

        const val TITLE = "title"
        const val SONG_NAME = "song_name"
    }

    private var mTitle: String = ""
    private var mSongName: String = ""

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(NOTIFICATION_ID, getNotification(),
            ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
        if (intent != null) {
            mTitle = intent.getStringExtra(TITLE).toString()
            mSongName = intent.getStringExtra(SONG_NAME).toString()
        }
        return START_NOT_STICKY
    }

    override fun onBind(p0: Intent?): IBinder? {
        return null
    }

    private fun getNotification(): Notification {
        val channel = NotificationChannel(
                NOTIFICATION_CHANNEL_ID, CHANNEL_NAME, NotificationManager.IMPORTANCE_NONE)

        val notificationManager = getSystemService(
            NotificationManager::class.java
        )
        notificationManager.createNotificationChannel(channel)

        return NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setContentTitle("SoundChecker $mTitle")
            .setContentText("Playing $mSongName")
            .setSmallIcon(R.mipmap.ic_launcher_foreground)
            .build()
    }

}
