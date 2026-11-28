package com.apexfit.app.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.media.ExifInterface
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import java.io.File
import java.time.LocalDate
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ProgressPhotoStoreTest {
    @Test fun privateCopiesSurviveSourceRemovalStripMetadataAndDeleteBothFiles() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val store = ProgressPhotoStore(context)
        val source = File(context.cacheDir, "photo-source-test.jpg")
        val root = File(context.noBackupFilesDir, "private_progress_photos_v1")
        try {
            store.clear()
            val bitmap = Bitmap.createBitmap(64, 32, Bitmap.Config.ARGB_8888)
            bitmap.eraseColor(Color.BLUE)
            source.outputStream().use { assertTrue(bitmap.compress(Bitmap.CompressFormat.JPEG, 90, it)) }
            bitmap.recycle()
            ExifInterface(source.path).apply {
                setAttribute(ExifInterface.TAG_MAKE, "Private camera")
                setAttribute(ExifInterface.TAG_ORIENTATION, "6")
                saveAttributes()
            }
            val photo = store.add(Uri.fromFile(source), LocalDate.now(), PhotoPose.FRONT)
            assertTrue(source.delete())
            assertEquals(photo, ProgressPhotoStore(context).list().single())
            val dir = root.listFiles()!!.single()
            assertEquals(setOf("photo.jpg", "thumb.jpg"), dir.listFiles()!!.map { it.name }.toSet())
            assertNull(ExifInterface(File(dir, "photo.jpg").path).getAttribute(ExifInterface.TAG_MAKE))
            assertNull(ExifInterface(File(dir, "thumb.jpg").path).getAttribute(ExifInterface.TAG_MAKE))
            val restored = store.bitmap(photo, false)!!
            assertEquals(32, restored.width)
            assertEquals(64, restored.height)
            restored.recycle()
            store.delete(photo)
            assertTrue(store.list().isEmpty())
            assertFalse(dir.exists())
        } finally {
            source.delete()
            store.clear()
        }
    }

    @Test fun corruptInputDoesNotLeavePendingCopiesAndMissingFilesAreRecoverable() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val store = ProgressPhotoStore(context)
        val root = File(context.noBackupFilesDir, "private_progress_photos_v1")
        val source = File(context.cacheDir, "photo-invalid-test.jpg")
        try {
            store.clear()
            source.writeText("not a photo")
            assertTrue(runCatching { store.add(Uri.fromFile(source), LocalDate.now(), PhotoPose.OTHER) }.isFailure)
            assertTrue(store.list().isEmpty())
            assertTrue(root.listFiles()!!.isEmpty())
            val id = java.util.UUID.randomUUID().toString()
            val missing = File(root, "${LocalDate.now()}_SIDE_$id")
            assertTrue(missing.mkdir())
            val photo = store.list().single()
            assertFalse(photo.available)
            assertNull(store.bitmap(photo, true))
            store.delete(photo)
            assertTrue(store.list().isEmpty())
            assertTrue(File(root, ".pending-crash").mkdir())
            assertTrue(File(root, ".deleted-crash").mkdir())
            assertTrue(store.list().isEmpty())
            assertTrue(root.listFiles()!!.isEmpty())
        } finally {
            source.delete()
            store.clear()
        }
    }
}
