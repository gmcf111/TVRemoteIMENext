package com.tvremoteime.next.control

import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayOutputStream

class ControlRegressionTest {
    @Test fun normalPngIsNeverModified() {
        val png = byteArrayOf(-119, 80, 78, 71, 13, 10, 26, 10, 0, 13, 10, 42)
        assertArrayEquals(png, PngScreenshot.normalize(png))
    }

    @Test fun terminalNewlineTranslationIsReversedOnlyOnce() {
        val png = byteArrayOf(-119, 80, 78, 71, 13, 10, 26, 10, 13, 13, 10, 10, 42)
        val translated = ByteArrayOutputStream().apply {
            for (b in png) { if (b == 10.toByte()) write(13); write(b.toInt()) }
        }.toByteArray()
        assertArrayEquals(png, PngScreenshot.normalize(translated))
        assertNull(PngScreenshot.normalize("Permission denied".toByteArray()))
    }

    @Test fun failedPreferredChannelFallsBackInOrder() {
        val visited = mutableListOf<String>()
        val channels = listOf("root", "ime", "adb").map { id -> object : ControlChannel {
            override val id = id
            override val capabilities = setOf(Capability.KEY, Capability.TEXT)
            override fun isAvailable() = true
        } }
        val router = ChannelRouter(channels)
        assertTrue(router.execute(Capability.KEY) { visited.add(it.id); it.id == "adb" })
        assertEquals(listOf("root", "ime", "adb"), visited)
        assertEquals("ime", router.pick(Capability.TEXT)?.id)
        assertNull(router.pick(Capability.TOUCH))
    }

    @Test fun shellTextCannotExpandVariablesOrCommands() {
        assertEquals("input text 'hello%sworld'", ShellCommands.text("hello world"))
        assertEquals("'Bob'\"'\"'s \"TV\"; $(id) `\$HOME` \\ path'", ShellCommands.quote("Bob's \"TV\"; $(id) `\$HOME` \\ path"))
        assertThrows(IllegalArgumentException::class.java) { ShellCommands.quote("a\u0000b") }
    }
}
