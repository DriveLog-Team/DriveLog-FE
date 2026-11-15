package kr.drivelog.app

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class MediaUploadTest {
    @Test fun computesActualByteLengthAndLowercaseSha256() = runBlocking {
        val metadata = inspectUpload({ ByteArrayInputStream("abc".toByteArray()) })
        assertEquals(3L, metadata.length)
        assertEquals("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad", metadata.sha256)
    }

    @Test fun rejectsEmptyAndOversizedSources() {
        assertThrows(UploadValidationError::class.java) { runBlocking { inspectUpload({ ByteArrayInputStream(byteArrayOf()) }) } }
        assertThrows(UploadValidationError::class.java) { runBlocking { inspectUpload({ ByteArrayInputStream(ByteArray(4)) }, 3) } }
    }

    @Test fun rejectsLengthChangesBetweenHashingAndSending() {
        for (size in listOf(2, 4)) {
            assertThrows(UploadValidationError::class.java) {
                runBlocking { copyUpload(ByteArrayInputStream(ByteArray(size)), ByteArrayOutputStream(), 3) }
            }
        }
    }

    @Test fun streamsMultipleBuffersWithoutChangingBytes() = runBlocking {
        val bytes = ByteArray(150_000) { (it % 251).toByte() }
        val output = ByteArrayOutputStream()
        copyUpload(ByteArrayInputStream(bytes), output, bytes.size.toLong())
        assertArrayEquals(bytes, output.toByteArray())
    }
}
