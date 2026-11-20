package com.example.audio

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin

class SynthSoundEngine {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val sampleRate = 22050
    @Volatile
    var enabled: Boolean = true

    private var musicJob: Job? = null
    @Volatile
    private var musicRequested: Boolean = false

    private val pentatonicHz = doubleArrayOf(
        523.25, 587.33, 659.25, 783.99, 880.00, 1046.50, 1174.66, 1318.51
    )

    fun setMusicPlaying(shouldPlay: Boolean) {
        musicRequested = shouldPlay
        if (shouldPlay && enabled) {
            startMusicLoop()
        } else {
            stopMusicLoop()
        }
    }

    private fun startMusicLoop() {
        if (musicJob?.isActive == true) return
        musicJob = scope.launch {
            var track: AudioTrack? = null
            try {
                val minBuf = AudioTrack.getMinBufferSize(
                    sampleRate,
                    AudioFormat.CHANNEL_OUT_MONO,
                    AudioFormat.ENCODING_PCM_16BIT
                ).coerceAtLeast(4096)

                track = AudioTrack.Builder()
                    .setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_GAME)
                            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                            .build()
                    )
                    .setAudioFormat(
                        AudioFormat.Builder()
                            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                            .setSampleRate(sampleRate)
                            .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                            .build()
                    )
                    .setBufferSizeInBytes(minBuf * 2)
                    .setTransferMode(AudioTrack.MODE_STREAM)
                    .build()

                track.play()

                // 112 BPM smooth Synthwave driving loop (16th-note step = ~0.1339s)
                val stepSamples = (sampleRate * 0.134).toInt()
                val stepBuffer = ShortArray(stepSamples)

                // 4-chord progression (Am -> F -> C -> G) in Hz
                val bassRoots = doubleArrayOf(110.0, 87.31, 130.81, 98.0)
                val arpPatterns = arrayOf(
                    doubleArrayOf(220.0, 261.63, 329.63, 392.0, 440.0, 392.0, 329.63, 261.63),
                    doubleArrayOf(174.61, 220.0, 261.63, 329.63, 349.23, 329.63, 261.63, 220.0),
                    doubleArrayOf(261.63, 329.63, 392.0, 493.88, 523.25, 493.88, 392.0, 329.63),
                    doubleArrayOf(196.0, 246.94, 293.66, 392.0, 440.0, 392.0, 293.66, 246.94)
                )

                var stepIndex = 0
                var bassPhase = 0.0
                var leadPhase = 0.0
                var padPhase = 0.0

                while (isActive && musicRequested && enabled) {
                    val chordIdx = (stepIndex / 16) % bassRoots.size
                    val beatInBar = stepIndex % 16
                    val bassFreq = bassRoots[chordIdx]
                    val arpFreq = arpPatterns[chordIdx][beatInBar % 8]
                    val padFreq = bassFreq * 2.0
                    val isKick = (beatInBar % 4 == 0)
                    val isAccent = (beatInBar % 2 == 0)

                    for (i in 0 until stepSamples) {
                        val t = i.toDouble() / stepSamples

                        // Warm driving synth bass (octave pulse on offbeats)
                        val curBassFreq = if (isAccent) bassFreq else bassFreq * 2.0
                        bassPhase += 2.0 * PI * curBassFreq / sampleRate
                        val bassEnv = exp(-3.8 * t)
                        val bass = (sin(bassPhase) * 0.72 + sin(bassPhase * 2.0) * 0.28) * bassEnv * 0.14

                        // Soft dreamy arpeggio chime
                        leadPhase += 2.0 * PI * arpFreq / sampleRate
                        val leadEnv = (i.toDouble() / (sampleRate * 0.01)).coerceAtMost(1.0) * exp(-5.2 * t)
                        val lead = sin(leadPhase) * leadEnv * 0.095

                        // Warm background pad hum
                        padPhase += 2.0 * PI * padFreq / sampleRate
                        val pad = sin(padPhase) * 0.045

                        // Subtle soft kick thump on quarter beats
                        val kick = if (isKick) {
                            val kFreq = 115.0 * exp(-18.0 * t) + 42.0
                            sin(2.0 * PI * kFreq * (i.toDouble() / sampleRate)) * exp(-14.0 * t) * 0.16
                        } else 0.0

                        val mix = (bass + lead + pad + kick).coerceIn(-0.9, 0.9)
                        stepBuffer[i] = (mix * Short.MAX_VALUE).toInt().toShort()
                    }

                    val written = track.write(stepBuffer, 0, stepBuffer.size)
                    if (written < 0) break
                    stepIndex = (stepIndex + 1) % 64
                }
            } catch (_: Throwable) {
                // Gracefully ignore if device audio hardware is unavailable
            } finally {
                try {
                    track?.pause()
                    track?.flush()
                    track?.stop()
                    track?.release()
                } catch (_: Throwable) {
                }
            }
        }
    }

    private fun stopMusicLoop() {
        musicJob?.cancel()
        musicJob = null
    }

    fun playStarPickup(combo: Int) {
        if (!enabled) return
        val baseFreq = pentatonicHz[combo.coerceIn(0, pentatonicHz.lastIndex)]
        playTone(startFreq = baseFreq, endFreq = baseFreq * 1.18, durationMs = 95, volume = 0.22f)
    }

    fun playPowerUp() {
        if (!enabled) return
        scope.launch {
            playBuffer(buildChordBuffer(doubleArrayOf(523.25, 659.25, 783.99, 1046.50), 260, 0.24f))
        }
    }

    fun playShieldHit() {
        if (!enabled) return
        playTone(startFreq = 260.0, endFreq = 95.0, durationMs = 190, volume = 0.28f)
    }

    fun playSmash() {
        if (!enabled) return
        playTone(startFreq = 420.0, endFreq = 920.0, durationMs = 130, volume = 0.25f)
    }

    fun playBiomeShift() {
        if (!enabled) return
        scope.launch {
            playBuffer(buildChordBuffer(doubleArrayOf(392.0, 493.88, 587.33, 783.99), 340, 0.18f))
        }
    }

    fun playButtonTap() {
        if (!enabled) return
        playTone(startFreq = 640.0, endFreq = 760.0, durationMs = 45, volume = 0.16f)
    }

    private fun playTone(startFreq: Double, endFreq: Double, durationMs: Int, volume: Float) {
        scope.launch {
            val count = (sampleRate * durationMs) / 1000
            val pcm = ShortArray(count)
            var phase = 0.0
            for (i in 0 until count) {
                val t = i.toDouble() / count
                val freq = startFreq + (endFreq - startFreq) * t
                phase += 2.0 * PI * freq / sampleRate
                val attack = (i.toDouble() / (sampleRate * 0.008)).coerceAtMost(1.0)
                val decay = exp(-4.2 * t)
                val sample = sin(phase) * attack * decay * volume
                pcm[i] = (sample * Short.MAX_VALUE).toInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
            }
            playBuffer(pcm)
        }
    }

    private fun buildChordBuffer(freqs: DoubleArray, durationMs: Int, volume: Float): ShortArray {
        val count = (sampleRate * durationMs) / 1000
        val pcm = ShortArray(count)
        for (i in 0 until count) {
            val timeSec = i.toDouble() / sampleRate
            var sum = 0.0
            for ((idx, f) in freqs.withIndex()) {
                val delay = idx * 0.035
                if (timeSec >= delay) {
                    val localT = timeSec - delay
                    val env = exp(-5.0 * localT)
                    sum += sin(2.0 * PI * f * localT) * env
                }
            }
            val normalized = (sum / freqs.size) * volume
            pcm[i] = (normalized * Short.MAX_VALUE).toInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
        }
        return pcm
    }

    private fun playBuffer(pcm: ShortArray) {
        try {
            val byteLen = pcm.size * 2
            val track = AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_GAME)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setSampleRate(sampleRate)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .build()
                )
                .setBufferSizeInBytes(byteLen.coerceAtLeast(1024))
                .setTransferMode(AudioTrack.MODE_STATIC)
                .build()

            track.write(pcm, 0, pcm.size)
            track.play()
            val sleepMs = (pcm.size * 1000L / sampleRate) + 40L
            Thread.sleep(sleepMs)
            track.stop()
            track.release()
        } catch (_: Throwable) {
            // Gracefully ignore if device audio hardware is unavailable
        }
    }

    fun release() {
        stopMusicLoop()
        scope.cancel()
    }
}
