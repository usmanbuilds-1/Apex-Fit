package com.apexfit.app.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.time.LocalDate
import java.util.UUID

enum class PhotoPose { FRONT, SIDE, BACK, OTHER }
data class ProgressPhoto(val id: String, val date: LocalDate, val pose: PhotoPose, val available: Boolean)

/** No source URI, original bytes, EXIF, or external/cache copy survives a successful import. */
class ProgressPhotoStore(context: Context) {
    private val app = context.applicationContext
    private val root = File(app.noBackupFilesDir, "private_progress_photos_v1")
    companion object {
        private val lock = Any()
        private const val MAX_INPUT = 32L * 1024 * 1024
        private const val MAX_DIMENSION = 2048
        private const val THUMB_DIMENSION = 480
    }
    private fun directory(photo: ProgressPhoto): File {
        require(UUID.fromString(photo.id).toString() == photo.id)
        return File(root, "${photo.date}_${photo.pose.name}_${photo.id}")
    }
    private fun parse(file: File): ProgressPhoto? = runCatching {
        val fields = file.name.split('_')
        require(file.isDirectory && fields.size == 3)
        val id = UUID.fromString(fields[2]).toString()
        require(id == fields[2])
        ProgressPhoto(id, LocalDate.parse(fields[0]), PhotoPose.valueOf(fields[1]),
            File(file, "photo.jpg").isFile && File(file, "thumb.jpg").isFile)
    }.getOrNull()
    private fun ensureRoot() { check(root.isDirectory || root.mkdirs()) }
    private fun cleanup() {
        root.listFiles().orEmpty().filter { it.name.startsWith(".pending-") || it.name.startsWith(".deleted-") }
            .forEach { check(it.deleteRecursively()) { "Could not clear temporary photo files." } }
    }
    suspend fun list(): List<ProgressPhoto> = withContext(Dispatchers.IO) { synchronized(lock) {
        ensureRoot()
        cleanup()
        root.listFiles().orEmpty().mapNotNull(::parse).sortedWith(compareByDescending<ProgressPhoto> { it.date }.thenBy { it.id })
    } }
    suspend fun add(uri: Uri, date: LocalDate, pose: PhotoPose): ProgressPhoto = withContext(Dispatchers.IO) { synchronized(lock) {
        require(date <= LocalDate.now())
        ensureRoot()
        cleanup()
        val photo = ProgressPhoto(UUID.randomUUID().toString(), date, pose, true)
        val staging = File(root, ".pending-${photo.id}")
        check(staging.mkdir())
        val raw = File(staging, "source.tmp")
        try {
            app.contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(raw).use { output ->
                    val buffer = ByteArray(64 * 1024)
                    var total = 0L
                    while (true) {
                        val n = input.read(buffer)
                        if (n < 0) break
                        total += n
                        require(total <= MAX_INPUT) { "Image too large." }
                        output.write(buffer, 0, n)
                    }
                }
            } ?: error("Image unavailable.")
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(raw.path, bounds)
            require(bounds.outWidth in 1..50000 && bounds.outHeight in 1..50000)
            require(bounds.outWidth.toLong() * bounds.outHeight <= 200_000_000L)
            var sample = 1
            while (maxOf(bounds.outWidth, bounds.outHeight) / sample > MAX_DIMENSION) sample *= 2
            val decoded = BitmapFactory.decodeFile(raw.path, BitmapFactory.Options().apply {
                inSampleSize = sample; inPreferredConfig = Bitmap.Config.ARGB_8888
            }) ?: error("Unsupported image.")
            val orientation = runCatching { ExifInterface(raw.path).getAttributeInt(
                ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL) }.getOrDefault(ExifInterface.ORIENTATION_NORMAL)
            var corrected: Bitmap? = null
            var thumb: Bitmap? = null
            try {
                val matrix = Matrix()
                when (orientation) {
                    2 -> matrix.setScale(-1f, 1f)
                    3 -> matrix.setRotate(180f)
                    4 -> matrix.setScale(1f, -1f)
                    5 -> { matrix.setRotate(90f); matrix.postScale(-1f, 1f) }
                    6 -> matrix.setRotate(90f)
                    7 -> { matrix.setRotate(-90f); matrix.postScale(-1f, 1f) }
                    8 -> matrix.setRotate(-90f)
                }
                val image = Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, matrix, true)
                corrected = image
                writeJpeg(image, File(staging, "photo.jpg"), 90)
                val scale = minOf(1f, THUMB_DIMENSION.toFloat() / maxOf(image.width, image.height))
                val preview = Bitmap.createScaledBitmap(image, (image.width * scale).toInt().coerceAtLeast(1),
                    (image.height * scale).toInt().coerceAtLeast(1), true)
                thumb = preview
                writeJpeg(preview, File(staging, "thumb.jpg"), 85)
            } finally {
                if (thumb != null && thumb !== corrected && thumb !== decoded) thumb.recycle()
                if (corrected != null && corrected !== decoded) corrected.recycle()
                decoded.recycle()
            }
            check(raw.delete())
            check(staging.renameTo(directory(photo))) { "Could not commit private photo." }
            photo
        } finally {
            if (staging.exists()) check(staging.deleteRecursively()) { "Could not remove temporary image." }
        }
    } }
    private fun writeJpeg(bitmap: Bitmap, file: File, quality: Int) {
        FileOutputStream(file).use { stream ->
            check(bitmap.compress(Bitmap.CompressFormat.JPEG, quality, stream))
            stream.fd.sync()
        }
    }
    suspend fun bitmap(photo: ProgressPhoto, thumbnail: Boolean): Bitmap? = withContext(Dispatchers.IO) { synchronized(lock) {
        val file = File(directory(photo), if (thumbnail) "thumb.jpg" else "photo.jpg")
        if (!file.isFile) null else BitmapFactory.decodeFile(file.path)
    } }
    suspend fun delete(photo: ProgressPhoto) = withContext(Dispatchers.IO) { synchronized(lock) {
        val dir = directory(photo)
        if (dir.exists()) {
            val tomb = File(root, ".deleted-${photo.id}")
            check(dir.renameTo(tomb)) { "Could not remove photo." }
            check(tomb.deleteRecursively()) { "Could not delete photo files." }
        }
    } }
    suspend fun clear() = withContext(Dispatchers.IO) { synchronized(lock) {
        if (root.exists()) check(root.deleteRecursively()) { "Could not delete private progress photos." }
    } }
}
