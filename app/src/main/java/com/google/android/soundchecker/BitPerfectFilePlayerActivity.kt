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
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioMixerAttributes
import android.os.Build
import android.os.Bundle
import android.util.Log

import com.google.android.soundchecker.utils.PlaybackConfigurationDiscover

/**
 * Base class for playing a file bit-perfectly. Currently, the framework only support
 * bit-perfect playback over USB.
 */
@TargetApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
open class BitPerfectFilePlayerActivity : BaseFilePlayerActivity() {
    private var mUsbDevice: AudioDeviceInfo? = null
    private var mBitPerfectMixerAttributes = ArrayList<AudioMixerAttributes>()
    private val mDeviceCallback = DeviceConnectionListener()

    protected var mTag = "BitPerfectFilePlayerActivity"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        mAudioManager!!.registerAudioDeviceCallback(mDeviceCallback, getHandler())
    }

    override fun getSelectedFileUnplayableReason(): String {
        val reason = super.getSelectedFileUnplayableReason();
        if (reason.isNotEmpty()) {
            return reason;
        }
        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.TIRAMISU) {
            return getString(R.string.sdk_required, 34);
        }
        if (mUsbDevice == null) {
            return getString(R.string.connect_usb)
        }
        if (mBitPerfectMixerAttributes.isEmpty()) {
            return getString(R.string.no_bit_perfect_mixer_attr)
        }
        return "";
    }

    protected val mPlaybackConfigurationDiscover: PlaybackConfigurationDiscover =
            object : PlaybackConfigurationDiscover {
                override fun onPlaybackConfigured(format: AudioFormat): AudioFormat {
                    Log.i(mTag, "playback config=$format")
                    val builder = AudioFormat.Builder(format)
                    if (mUsbDevice != null) {
                        // If there is any mixer attributes matching the requested configuration, use it.
                        for (mixerAttr in mBitPerfectMixerAttributes) {
                            if (mixerAttr.format == format) {
                                Log.i(mTag, "Found exactly matched mixer attributes=$mixerAttr")
                                mAudioManager!!.setPreferredMixerAttributes(
                                        mAttrs, mUsbDevice!!, mixerAttr)
                                return builder.build()
                            }
                        }
                        // There is not exactly matched mixer attributes, try greater bit depth
                        var greaterBitDepths = getGreaterBitDepth(format.encoding)
                        for (mixerAttr in mBitPerfectMixerAttributes) {
                            if (mixerAttr.format.channelMask == format.channelMask
                                    && mixerAttr.format.sampleRate == format.sampleRate
                                    && greaterBitDepths.contains(mixerAttr.format.encoding)) {
                                Log.i(mTag,
                                      "Use greater bit depth for mixer attributes=$mixerAttr")
                                mAudioManager!!.setPreferredMixerAttributes(
                                        mAttrs, mUsbDevice!!, mixerAttr)
                                builder.setEncoding(mixerAttr.format.encoding)
                                return builder.build()
                            }
                        }
                        Log.i(mTag, "Cannot find any suitable mixer attribute")
                    } else {
                        Log.i(mTag, "Usb device is not found")
                    }
                    return builder.build()
                }
            }

    private fun getGreaterBitDepth(encoding: Int): Set<Int> {
        return when (encoding) {
            AudioFormat.ENCODING_PCM_16BIT -> setOf(AudioFormat.ENCODING_PCM_24BIT_PACKED,
                    AudioFormat.ENCODING_PCM_32BIT)

            AudioFormat.ENCODING_PCM_24BIT_PACKED -> setOf(AudioFormat.ENCODING_PCM_32BIT)
            else -> emptySet()
        }
    }

    private fun scanForUsbDevice() {
        for (device in mAudioManager!!.getDevices(AudioManager.GET_DEVICES_OUTPUTS)) {
            mUsbDevice = null
            if (device.type == AudioDeviceInfo.TYPE_USB_HEADSET
                    || device.type == AudioDeviceInfo.TYPE_USB_DEVICE) {
                mUsbDevice = device
                for (mixerAttr in mAudioManager!!.getSupportedMixerAttributes(device)) {
                    if (mixerAttr.mixerBehavior == AudioMixerAttributes.MIXER_BEHAVIOR_BIT_PERFECT) {
                        Log.i(mTag, "find mixer attributes=$mixerAttr")
                        mBitPerfectMixerAttributes.add(mixerAttr)
                    }
                }
                break
            }
        }
    }

    private inner class DeviceConnectionListener : AudioDeviceCallback() {

        override fun onAudioDevicesAdded(addedDevices: Array<AudioDeviceInfo>) {
            Log.i(mTag, "onAudioDeviceAdded")
            scanForUsbDevice()
            sendUpdateUIMsg(0)
        }

        override fun onAudioDevicesRemoved(removedDevices: Array<AudioDeviceInfo>) {
            scanForUsbDevice()
            if (mUsbDevice == null) {
                Log.i(mTag, "Try to stop playback as there is not USB device connected")
                sendStopPlaybackMsg(0)
            }
            sendUpdateUIMsg(0)
        }
    }
}