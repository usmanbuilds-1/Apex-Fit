package com.apexfit.app.utils

import android.app.Application
import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import kotlinx.coroutines.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

object AudioService {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO.limitedParallelism(1))
    private val audioMutex = Mutex()

    @Volatile
    private var pooledBeepTrack: AudioTrack? = null
    private var pooledTrackBufferSize: Int = 0

    suspend fun playBeep(context: Context) {
        val appContext = context.applicationContext ?: context
        playSynthesizedAudioTone(
            frequencyHz = 880.0,
            durationMs = 150,
            context = appContext,
            usage = AudioAttributes.USAGE_ASSISTANCE_SONIFICATION,
            contentType = AudioAttributes.CONTENT_TYPE_SONIFICATION
        )
    }

    suspend fun playRestTimerComplete(context: Context) {
        val appContext = context.applicationContext ?: context
        vibrate(appContext, 500)
        playSynthesizedAudioTone(
            frequencyHz = 1100.0,
            durationMs = 350,
            context = appContext,
            usage = AudioAttributes.USAGE_NOTIFICATION,
            contentType = AudioAttributes.CONTENT_TYPE_SONIFICATION
        )
    }

    private fun vibrate(context: Context, durationMs: Long) {
        try {
            val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
                vibratorManager?.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator?.vibrate(VibrationEffect.createOneShot(durationMs, VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                @Suppress("DEPRECATION")
                vibrator?.vibrate(durationMs)
            }
        } catch (e: Exception) {
            Log.e("ApexFit", "Error vibrating: ${e.message}", e)
        }
    }

    private fun getOrCreateBeepTrack(
        sampleRate: Int,
        numSamples: Int,
        usage: Int = AudioAttributes.USAGE_ASSISTANCE_SONIFICATION,
        contentType: Int = AudioAttributes.CONTENT_TYPE_SONIFICATION
    ): AudioTrack {
        val requiredBytes = numSamples * 2
        val existing = pooledBeepTrack
        if (existing != null &&
            existing.state == AudioTrack.STATE_INITIALIZED &&
            pooledTrackBufferSize >= requiredBytes
        ) {
            return existing
        }

        existing?.let {
            try {
                it.stop()
                it.release()
            } catch (e: Exception) {
                Log.e("ApexFit", "Error releasing outdated pooled track: ${e.message}", e)
            }
        }

        val bufferSize = maxOf(requiredBytes, 8000)
        val track = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(usage)
                        .setContentType(contentType)
                        .build()
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setSampleRate(sampleRate)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .build()
                )
                .setBufferSizeInBytes(bufferSize)
                .setTransferMode(AudioTrack.MODE_STATIC)
                .build()
        } else {
            @Suppress("DEPRECATION")
            AudioTrack(
                AudioManager.STREAM_MUSIC,
                sampleRate,
                AudioFormat.CHANNEL_OUT_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                bufferSize,
                AudioTrack.MODE_STATIC
            )
        }
        pooledBeepTrack = track
        pooledTrackBufferSize = bufferSize
        return track
    }

    suspend fun playSynthesizedAudioTone(
        frequencyHz: Double,
        durationMs: Int,
        context: Context? = null,
        usage: Int = AudioAttributes.USAGE_ASSISTANCE_SONIFICATION,
        contentType: Int = AudioAttributes.CONTENT_TYPE_SONIFICATION
    ) {
        audioMutex.withLock {
            try {
                val sampleRate = 8000
                val numSamples = durationMs * sampleRate / 1000
                val sample = DoubleArray(numSamples)
                val generatedSnd = ShortArray(numSamples)

                withContext(Dispatchers.Default) {
                    for (i in 0 until numSamples) {
                        val angle = 2.0 * Math.PI * i / (sampleRate / frequencyHz)
                        var amplitude = Math.sin(angle)

                        // Add linear envelope to prevent popping (fade in/out)
                        val fadeSamples = (sampleRate * 0.01).toInt().coerceAtMost(numSamples / 2) // 10ms fade
                        if (i < fadeSamples) {
                            amplitude *= (i.toDouble() / fadeSamples)
                        } else if (i > numSamples - fadeSamples) {
                            amplitude *= ((numSamples - i).toDouble() / fadeSamples)
                        }

                        sample[i] = amplitude
                    }
                    var idx = 0
                    for (dVal in sample) {
                        val val1 = (dVal * 32767).toInt().toShort()
                        generatedSnd[idx++] = val1
                    }
                }

                val currentTrack = getOrCreateBeepTrack(sampleRate, numSamples, usage, contentType)
                if (currentTrack.state == AudioTrack.STATE_INITIALIZED) {
                    try {
                        currentTrack.stop()
                    } catch (_: Exception) {}
                    try {
                        currentTrack.reloadStaticData()
                    } catch (_: Exception) {}
                }

                currentTrack.write(generatedSnd, 0, numSamples)
                currentTrack.play()
                delay(durationMs.toLong() + 50)
                try {
                    currentTrack.stop()
                } catch (_: Exception) {}
            } catch (e: Exception) {
                Log.e("ApexFit", "Error in playSynthesizedAudioTone: ${e.message}", e)
            }
        }
    }

    fun release() {
        pooledBeepTrack?.let {
            try {
                it.stop()
                it.release()
            } catch (e: Exception) {
                Log.e("ApexFit", "Error releasing AudioTrack: ${e.message}", e)
            }
        }
        pooledBeepTrack = null
        pooledTrackBufferSize = 0
    }
}
