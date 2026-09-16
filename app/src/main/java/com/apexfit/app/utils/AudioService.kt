package com.apexfit.app.utils

import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

object AudioService {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO.limitedParallelism(1))
    private val audioMutex = Mutex()
    @Volatile
    private var audioTrack: AudioTrack? = null

    suspend fun playBeep() {
        playSynthesizedAudioTone(880.0, 150, android.media.AudioAttributes.USAGE_ASSISTANCE_SONIFICATION, android.media.AudioAttributes.CONTENT_TYPE_SONIFICATION)
    }

    suspend fun playRestTimerComplete(context: android.content.Context) {
        vibrate(context, 500)
        playSynthesizedAudioTone(1100.0, 350, android.media.AudioAttributes.USAGE_NOTIFICATION, android.media.AudioAttributes.CONTENT_TYPE_SONIFICATION)
    }

    private fun vibrate(context: android.content.Context, durationMs: Long) {
        val vibrator = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
            val vibratorManager = context.getSystemService(android.content.Context.VIBRATOR_MANAGER_SERVICE) as android.os.VibratorManager
            vibratorManager.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(android.content.Context.VIBRATOR_SERVICE) as android.os.Vibrator
        }

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            vibrator.vibrate(android.os.VibrationEffect.createOneShot(durationMs, android.os.VibrationEffect.DEFAULT_AMPLITUDE))
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(durationMs)
        }
    }

    suspend fun playSynthesizedAudioTone(frequencyHz: Double, durationMs: Int, context: android.content.Context? = null, usage: Int = android.media.AudioAttributes.USAGE_ASSISTANCE_SONIFICATION, contentType: Int = android.media.AudioAttributes.CONTENT_TYPE_SONIFICATION) {
        val audioManager = context?.getSystemService(android.content.Context.AUDIO_SERVICE) as? android.media.AudioManager
        val focusRequest = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O && audioManager != null) {
            android.media.AudioFocusRequest.Builder(android.media.AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
                .setAudioAttributes(
                    android.media.AudioAttributes.Builder()
                        .setUsage(usage)
                        .setContentType(contentType)
                        .build()
                )
                .build()
                .also { audioManager.requestAudioFocus(it) }
        } else null
        try {
        audioMutex.withLock {
            try {
                val sampleRate = 8000
                val numSamples = durationMs * sampleRate / 1000
                val sample = DoubleArray(numSamples)
                val generatedSnd = ShortArray(numSamples)

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

                val currentTrack = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
                    AudioTrack.Builder()
                        .setAudioAttributes(
                            android.media.AudioAttributes.Builder()
                                .setUsage(usage)
                                .setContentType(contentType)
                                .build()
                        )
                        .setAudioFormat(
                            android.media.AudioFormat.Builder()
                                .setEncoding(android.media.AudioFormat.ENCODING_PCM_16BIT)
                                .setSampleRate(sampleRate)
                                .setChannelMask(android.media.AudioFormat.CHANNEL_OUT_MONO)
                                .build()
                        )
                        .setBufferSizeInBytes(numSamples * 2)
                        .setTransferMode(AudioTrack.MODE_STATIC)
                        .build()
                } else {
                    @Suppress("DEPRECATION")
                    AudioTrack(
                        AudioManager.STREAM_MUSIC,
                        sampleRate,
                        AudioFormat.CHANNEL_OUT_MONO,
                        AudioFormat.ENCODING_PCM_16BIT,
                        numSamples * 2,
                        AudioTrack.MODE_STATIC
                    )
                }
                
                // Release previous track if still active to avoid native resource exhaustion
                audioTrack?.let {
                    try {
                        it.stop()
                        it.release()
                    } catch (e: Exception) {
                        android.util.Log.e("ApexFit", "Error playing synthesized frequency preview track stop: ${e.message}", e)
                    }
                }
                
                audioTrack = currentTrack
                currentTrack.write(generatedSnd, 0, numSamples)
                currentTrack.play()
                try {
                    delay(durationMs.toLong() + 50)
                    currentTrack.stop()
                } finally {
                    currentTrack.release()
                    if (audioTrack == currentTrack) {
                        audioTrack = null
                    }
                }
            } catch (e: Exception) {
                android.util.Log.e("ApexFit", "Error in playSynthesizedAudioTone: ${e.message}", e)
            }
        }
        } finally {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                focusRequest?.let { audioManager?.abandonAudioFocusRequest(it) }
            }
        }
    }

    fun release() {
        audioTrack?.let {
            try {
                it.stop()
                it.release()
            } catch (e: Exception) {
                android.util.Log.e("ApexFit", "Error releasing AudioTrack: ${e.message}", e)
            }
        }
        audioTrack = null
    }
}
