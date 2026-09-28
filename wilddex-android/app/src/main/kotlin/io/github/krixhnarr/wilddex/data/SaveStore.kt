package io.github.krixhnarr.wilddex.data

import android.content.Context
import android.graphics.Bitmap
import io.github.krixhnarr.wilddex.core.PlayerState
import java.io.File
import java.util.concurrent.Executors

// Progress lives in one JSON file; capture photos are JPEGs beside it.
// Writes go to a temp file first so a crash can't leave a half-written save.

class SaveStore(context: Context) {
    private val dir = context.filesDir
    private val file = File(dir, "wilddex.json")
    private val photos = File(dir, "photos").apply { mkdirs() }
    private val io = Executors.newSingleThreadExecutor()

    fun load(): PlayerState = try {
        if (file.exists()) PlayerState.fromJson(file.readText()) else PlayerState().also { it.migrate() }
    } catch (e: Exception) {
        PlayerState().also { it.migrate() }
    }

    fun save(state: PlayerState) {
        state.savedAt = System.currentTimeMillis()
        val text = state.toJson()
        io.execute {
            val tmp = File(dir, "wilddex.json.tmp")
            tmp.writeText(text)
            tmp.renameTo(file)
        }
    }

    fun savePhoto(key: String, bitmap: Bitmap) {
        io.execute { File(photos, "$key.jpg").outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 84, it) } }
    }

    fun photo(key: String): File? = File(photos, "$key.jpg").takeIf { it.exists() }

    /** Photos as data URLs, for a backup file. */
    fun photosAsDataUrls(keys: Collection<String>): Map<String, String> = keys.mapNotNull { k ->
        photo(k)?.let { k to "data:image/jpeg;base64," + android.util.Base64.encodeToString(it.readBytes(), android.util.Base64.NO_WRAP) }
    }.toMap()

    /** Replaces all photos with the ones from a backup (data URLs from the web or Android app). */
    fun restorePhotos(backup: Map<String, String>) {
        io.execute {
            photos.listFiles()?.forEach { it.delete() }
            for ((k, url) in backup) {
                val b64 = url.substringAfter("base64,", "")
                if (b64.isEmpty()) continue
                val bytes = android.util.Base64.decode(b64, android.util.Base64.DEFAULT)
                val bmp = android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: continue
                File(photos, "$k.jpg").outputStream().use { bmp.compress(Bitmap.CompressFormat.JPEG, 84, it) }
            }
        }
    }

    fun deletePhoto(key: String) { io.execute { File(photos, "$key.jpg").delete() } }

    fun wipePhotos() { photos.listFiles()?.forEach { it.delete() } }
}
