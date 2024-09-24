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

import android.media.MediaCodec

open class AudioSource(errorCallback: AudioErrorCallback? = null)
        : AudioEndpoint(true, errorCallback) {
    open fun pull(buffer: ByteArray, numFrames: Int): MediaCodec.BufferInfo {
        // Default implementation, do nothing
        return MediaCodec.BufferInfo()
    }

    open fun pull(buffer: FloatArray, numFrames: Int): Int {
        // Default implementation, do nothing
        return 0
    }

    open fun render(buffer: FloatArray, offset: Int, stride: Int, numFrames: Int): Int {
        // Default implementation, do nothing
        return 0
    }

    open fun pull(numBytes: Int, buffer: ByteArray): MediaCodec.BufferInfo {
        // Default implementation, do nothing
        return MediaCodec.BufferInfo()
    }

    fun connect(sink: AudioSink) {
        sink.setSource(this)
    }
}
