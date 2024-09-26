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

package com.google.android.soundchecker.utils

abstract class AudioErrorCallback {
    companion object {
        // Error code, keep synced with AudioSystem
        const val SUCCESS = 0
        const val ERROR = -1
        const val BAD_VALUE = -2
        const val INVALID_OPERATION = -3
        const val PERMISSION_DENIED = -4
        const val NO_INIT = -5
        const val DEAD_OBJECT = -6
        const val WOULD_BLOCK = -7
    }

    abstract fun onError(error: Int, msg: String)
}
