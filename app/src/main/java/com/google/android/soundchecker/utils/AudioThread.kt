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

abstract class AudioThread : Runnable {
    private var mThread: Thread? = null
    private var mIsEnabled = false

    fun start() {
        mIsEnabled = true
        mThread = Thread(this)
        mThread!!.start()
    }

    fun stop() {
        mIsEnabled = false
        try {
            mThread!!.join(1000)
        } catch (e: InterruptedException) {
            e.printStackTrace()
        }
    }

    fun isEnabled(): Boolean {
        return mIsEnabled
    }

    companion object {
        protected const val TAG = "AudioThread"
    }
}