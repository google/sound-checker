# Automated Testing with Intents

The `AudioEncoderDecoderActivity` can be launched with intents to automate testing. This is useful for running tests from the command line using `adb`.

## Starting the Activity

To start the activity with an intent, use the following `adb` command:

```bash
adb shell am start -n com.google.android.soundchecker/.MainActivity -a android.intent.action.VIEW --es test encoder_decoder [options]
```

## Intent Extras

The following intent extras are available to configure the test:

| Extra Key | Type | Description |
|---|---|---|
| `source` | String | The input source for the test. Can be `file`, `sine`, or `sinesweep`. |
| `input_file` | String | The name to the input file. This should be in the /storage/emulated/0/Android/data/com.google.android.soundchecker/files/Music directory. Required if `source` is `file`. |
| `output_file` | String | The name of the output file where the results of each callback will be written. This file will be saved in the app's external music directory. |
| `codec_name` | String | The name of the audio codec to use (e.g., `c2.android.aac.encoder`). |
| `mime_type` | String | The MIME type of the output format (e.g., `audio/mp4a-latm`). |
| `sample_rate` | Int | The sample rate in Hz. |
| `channel_count` | Int | The number of audio channels. |
| `bitrate` | Int | The bitrate in bits per second. |
| `aac_profile` | Int | The AAC profile to use. See https://developer.android.com/reference/android/media/MediaCodecInfo.CodecProfileLevel |
| `flac_compression` | Int | The FLAC compression level (0-8). |
| `encoder_delay` | Int | The encoder delay in frames. |
| `count` | Int | The number of callbacks to run the test for. The test will stop after this many callbacks. |
| `auto_start` | Boolean | If `true`, the test will start automatically when the activity is launched. |
| `auto_exit` | Boolean | If `true`, the activity will exit automatically after the test is finished. |

## Examples

### Example 1: Encode a WAV file to AAC

This example encodes a WAV file located at `/sdcard/Music/input.wav` to an AAC audio file and writes the analysis to `/sdcard/Music/output.txt`.

```bash
adb shell am start -n com.google.android.soundchecker/.MainActivity -a android.intent.action.VIEW \
  --es test encoder_decoder \
  --es source file \
  --es input_file soundchecker_20250916-144021.wav \
  --es output_file output.txt \
  --es codec_name c2.android.aac.encoder \
  --es mime_type audio/mp4a-latm \
  --ei bitrate 128000 \
  --ei aac_profile 2 \
  --ei count 100 \
  --ez auto_start true \
  --ez auto_exit true
```

### Example 2: Run a sine wave test

This example runs a test with a 1kHz sine wave at 48kHz sample rate, stereo channels, and saves the output as FLAC.

```bash
adb shell am start -n com.google.android.soundchecker/.MainActivity -a android.intent.action.VIEW \
  --es test encoder_decoder \
  --es source sine \
  --es output_file sine_output.txt \
  --es codec_name c2.android.flac.encoder \
  --es mime_type audio/flac \
  --ei sample_rate 48000 \
  --ei channel_count 2 \
  --ei flac_compression 0 \
  --ei count 50 \
  --ez auto_start true \
  --ez auto_exit true
```

### Example 3: Run a sine sweep test

This example runs a test with a sine sweep.

```bash
adb shell am start -n com.google.android.soundchecker/.MainActivity -a android.intent.action.VIEW \
  --es test encoder_decoder \
  --es source sinesweep \
  --es output_file sinesweep_output.txt \
  --es codec_name c2.android.opus.encoder \
  --es mime_type audio/opus \
  --ei sample_rate 48000 \
  --ei channel_count 2 \
  --ei bitrate 64000 \
  --ei count 200 \
  --ez auto_start true \
  --ez auto_exit true
```

### Example 4: Run a sine sweep test with a specific encoder delay

This example runs a test with a sine sweep and an encoder delay of 1000 frames.

```bash
adb shell am start -n com.google.android.soundchecker/.MainActivity -a android.intent.action.VIEW \
  --es test encoder_decoder \
  --es source sinesweep \
  --es output_file sinesweep_output.txt \
  --es codec_name c2.android.opus.encoder \
  --es mime_type audio/opus \
  --ei sample_rate 48000 \
  --ei channel_count 1 \
  --ei bitrate 64000 \
  --ei encoder_delay 1000 \
  --ei count 200 \
  --ez auto_start true \
  --ez auto_exit true
```