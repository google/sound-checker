[Home](../README.md)

# How to Use Sound-checker

## Bit perfect player

The bit-perfect playback is supported since Android API level 34. The bit-perfect player will only
be able to start playback on Android devices that support [bit-perfect mixer behavior](https://developer.android.com/reference/android/media/AudioMixerAttributes#MIXER_BEHAVIOR_BIT_PERFECT),
which can be found via [AudioManager.getPreferredMixerAttributes](https://developer.android.com/reference/kotlin/android/media/AudioManager?hl=en#getpreferredmixerattributes).

USB device will be required to perform bit-perfect playback.

### MQA File Player

MQA File Player can be used to play FLAC files that contain MQA content.

After selecting a file, click `START` button to start playback.

The MQA file player will use a customized ExoPlayer to stream the MQA content as PCM 24 bit or 32 bit
according to the metadata from the flac file. Before starting the playback, the MQA file player
will call [AudioManager.setPreferredMixerAttributes](https://developer.android.com/reference/kotlin/android/media/AudioManager?hl=en#setpreferredmixerattributes)
to setup the right preferred mixer attributes according to the information from the flac extractor.
The preferred mixer attributes will be cleared after the playback is stopped.

### DSD File Player

DSD File Player can be used to play dsf files that contain DSD content.

After selecting a file, click `START` button to start playback.

The DSD file player will wrap the DSD content in PCM according to [DoP open standard](https://dsd-guide.com/dop-open-standard).
The PCM will be either PCM 24 bit or 32 bit according to the connected USB device's capabilities.
Before starting playback, the MQA file player will call [AudioManager.setPreferredMixerAttributes](https://developer.android.com/reference/kotlin/android/media/AudioManager?hl=en#setpreferredmixerattributes)
to setup the right preferred mixer attributes. The preferred mixer attributes will be cleared after
the playback is stopped.

Note that the wrapper for DSD filer player only works for 64FS DSD.

## Test Resonance

The resonance test is used to help figure out the resonant frequency of the speaker on Android devices.

The test creates three audio sine wave players. Each player has sliders for frequency and amplitude.
Slide the frequency slider bar to change to frequency to find the resonant frequency. The ranges of
three sine wave player are [100Hz, 400Hz], [300Hz, 1200Hz] and [1000Hz, 4000Hz].

## Test THD and SNR

The THD and SNR test is used to calculate the [total harmonic distortion](https://en.wikipedia.org/wiki/Total_harmonic_distortion) and [signal to noise ratio](https://en.wikipedia.org/wiki/Signal-to-noise_ratio).

The test allows you to select output device, input device, channel, frequency bin.
The calculated result will be updated automatically.
The graph at the bottom shows the decibels of every bin.
