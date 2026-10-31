package com.drivemate.tests.cat02

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.media.MediaPlayer
import android.os.SystemClock
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.drivemate.app.nav.PhoneSpeakerPlayer
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * P10 regression (owner screenshot, 31 Oct): "Phone-speaker guidance could not be started without
 * verified speaker routing." The app asked for the speaker before the player had its data source,
 * which Android always refuses, so no phone-speaker prompt ever started. This runs the app's real
 * setup on the emulator's own speaker: Android must accept the speaker and play there.
 * The emulator has no Bluetooth: a car connection is checked on the owner's phone, not here.
 */
@RunWith(AndroidJUnit4::class)
class P10PhoneSpeakerPlayerScreenTest {
    private val context: Context = InstrumentationRegistry.getInstrumentation().targetContext
    private val audio = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    /** Half a second of a quiet 440 Hz tone, as a 16-bit mono WAV (like the voice engine's file). */
    private fun tone(): File {
        val rate = 16_000; val samples = rate / 2
        val b = ByteBuffer.allocate(44 + samples * 2).order(ByteOrder.LITTLE_ENDIAN)
        b.put("RIFF".toByteArray()).putInt(36 + samples * 2).put("WAVEfmt ".toByteArray()).putInt(16)
            .putShort(1).putShort(1).putInt(rate).putInt(rate * 2).putShort(2).putShort(16)
            .put("data".toByteArray()).putInt(samples * 2)
        repeat(samples) { b.putShort((Math.sin(2 * Math.PI * 440 * it / rate) * 2000).toInt().toShort()) }
        return File.createTempFile("guidance-test-", ".wav", context.cacheDir).apply { writeBytes(b.array()) }
    }

    @Test fun phoneSpeakerPromptIsAcceptedAndPlaysOnTheSpeaker() {
        val speaker = audio.getDevices(AudioManager.GET_DEVICES_OUTPUTS).firstOrNull { it.type == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER }
        assertNotNull("the emulator has a built-in speaker", speaker)
        val file = tone()
        val attributes = AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE)
            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build()
        val player = MediaPlayer()
        try {
            assertTrue("Android accepts the phone speaker once the player has its sound",
                PhoneSpeakerPlayer.open(player, file.absolutePath, attributes, speaker!!))
            assertEquals(AudioDeviceInfo.TYPE_BUILTIN_SPEAKER, player.preferredDevice?.type)
            player.prepare()
            player.setVolume(0f, 0f) // muted, as the app primes it
            player.isLooping = true
            player.start()
            val until = SystemClock.elapsedRealtime() + 3_000
            var routed: Int? = null
            while (SystemClock.elapsedRealtime() < until) {
                routed = player.routedDevice?.type
                if (routed == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER) break
                SystemClock.sleep(50)
            }
            assertEquals("Android plays the prompt on the phone speaker", AudioDeviceInfo.TYPE_BUILTIN_SPEAKER, routed)
        } finally {
            runCatching { player.release() }
            file.delete()
        }
    }
}
