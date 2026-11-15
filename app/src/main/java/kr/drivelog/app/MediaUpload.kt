package kr.drivelog.app

import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.security.MessageDigest
import kotlinx.coroutines.ensureActive
import kotlin.coroutines.coroutineContext

internal const val MaximumUploadBytes = 8L * 1024 * 1024 * 1024
internal class UploadValidationError(message: String) : IOException(message)
internal data class UploadMetadata(val length: Long, val sha256: String)

/** Read in bounded chunks; never load a camera recording into memory. */
internal suspend fun inspectUpload(open: () -> InputStream, limit: Long = MaximumUploadBytes): UploadMetadata {
    val digest = MessageDigest.getInstance("SHA-256")
    var length = 0L
    open().use { input ->
        val buffer = ByteArray(64 * 1024)
        while (true) {
            coroutineContext.ensureActive()
            val count = input.read(buffer)
            if (count == -1) break
            length += count
            if (length > limit) throw UploadValidationError("영상은 파일당 최대 8 GiB까지 전송할 수 있어요.")
            digest.update(buffer, 0, count)
        }
    }
    if (length == 0L) throw UploadValidationError("비어 있는 영상은 전송할 수 없어요.")
    return UploadMetadata(length, digest.digest().joinToString("") { "%02x".format(it) })
}

internal suspend fun copyUpload(input: InputStream, output: OutputStream, length: Long) {
    var sent = 0L
    val buffer = ByteArray(64 * 1024)
    while (true) {
        coroutineContext.ensureActive()
        val count = input.read(buffer)
        if (count == -1) break
        sent += count
        if (sent > length) throw UploadValidationError("파일이 변경됐어요. 영상을 다시 선택하세요.")
        output.write(buffer, 0, count)
    }
    if (sent != length) throw UploadValidationError("파일이 변경됐어요. 영상을 다시 선택하세요.")
}
