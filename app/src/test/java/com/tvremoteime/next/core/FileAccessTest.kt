package com.tvremoteime.next.core

import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class FileAccessTest {
    @get:Rule val temporary = TemporaryFolder()

    @Test fun decodedFilenamesRemainLiteral() {
        val root = temporary.newFolder("root")
        assertEquals(File(root, "中文 + 100%25.txt").canonicalFile, FileAccess.resolve(root, "/中文 + 100%25.txt"))
        assertNull(FileAccess.resolve(root, "../root-other/file"))
        assertNull(FileAccess.resolve(root, "folder/../file"))
        assertNull(FileAccess.resolve(root, "file\u0000.txt"))
        assertEquals(root.canonicalFile, FileAccess.resolve(root, ""))
    }

    @Test fun copyingIntoDescendantFailsWithoutRecursing() {
        val root = temporary.newFolder("root")
        val source = File(root, "source").apply { mkdir() }
        File(source, "keep.txt").writeText("keep")
        assertThrows(IllegalArgumentException::class.java) { FileAccess.copy(source, source) }
        assertFalse(File(source, "source").exists())
        assertEquals("keep", File(source, "keep.txt").readText())
    }

    @Test fun movePreservesTreeAndRefusesOverwrite() {
        val source = temporary.newFolder("source")
        File(source, "nested").mkdir()
        File(source, "nested/a.txt").writeText("payload")
        val target = temporary.newFolder("target")
        FileAccess.copy(source, target, move = true)
        assertFalse(source.exists())
        assertEquals("payload", File(target, "source/nested/a.txt").readText())
        source.mkdir()
        assertThrows(IllegalArgumentException::class.java) { FileAccess.copy(source, target) }
    }
}
