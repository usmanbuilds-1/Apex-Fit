package com.example.utils

import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import kotlinx.coroutines.*
import kotlinx.coroutines.newSingleThreadContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

object AudioService {
    @OptIn(ObsoleteCoroutinesApi::class)
    private val scope = CoroutineScope(newSingleThreadContext("audio"))
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

    suspend fun playMusicSynthNote() {
        // Plays a random single note from {C3, E3, G3, A3} (one note per call, not a chord)
        val notes = listOf(130.81, 164.81, 196.00, 220.00)  // C3, E3, G3, A3
        val randomNote = notes.random()
        playSynthesizedAudioTone(randomNote, 80, android.media.AudioAttributes.USAGE_ASSISTANCE_SONIFICATION, android.media.AudioAttributes.CONTENT_TYPE_SONIFICATION)
    }

    suspend fun playSynthesizedAudioTone(frequencyHz: Double, durationMs: Int, usage: Int = android.media.AudioAttributes.USAGE_ASSISTANCE_SONIFICATION, contentType: Int = android.media.AudioAttributes.CONTENT_TYPE_SONIFICATION) {
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
                delay(durationMs.toLong() + 50)
                currentTrack.release()
                if (audioTrack == currentTrack) {
                    audioTrack = null
                }
            } catch (e: Exception) {
                android.util.Log.e("ApexFit", "Error in playSynthesizedAudioTone: ${e.message}", e)
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
