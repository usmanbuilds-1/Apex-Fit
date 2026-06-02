package com.example.utils

import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import kotlinx.coroutines.*

object AudioService {
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private var audioTrack: AudioTrack? = null

    fun playBeep() {
        playSynthesizedAudioTone(880.0, 150)
    }

    fun playRestTimerComplete() {
        playSynthesizedAudioTone(1100.0, 350)
    }

    fun playMusicSynthNote() {
        val notes = listOf(130.81, 164.81, 196.00, 220.00) // C3, E3, G3, A3
        val randomNote = notes.random()
        playSynthesizedAudioTone(randomNote, 80)
    }

    fun playSynthesizedAudioTone(frequencyHz: Double, durationMs: Int) {
        scope.launch {
            try {
                val sampleRate = 8000
                val numSamples = durationMs * sampleRate / 1000
                val sample = DoubleArray(numSamples)
                val generatedSnd = ShortArray(numSamples)

                for (i in 0 until numSamples) {
                    sample[i] = Math.sin(2 * Math.PI * i / (sampleRate / frequencyHz))
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
                                .setUsage(android.media.AudioAttributes.USAGE_MEDIA)
                                .setContentType(android.media.AudioAttributes.CONTENT_TYPE_MUSIC)
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
                        e.printStackTrace()
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
                e.printStackTrace()
            }
        }
    }

    fun release() {
        audioTrack?.let {
            try {
                it.stop()
                it.release()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        audioTrack = null
    }
}
