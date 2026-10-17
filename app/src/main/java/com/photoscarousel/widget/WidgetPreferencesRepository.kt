package com.photoscarousel.widget

import android.content.Context
import com.google.gson.Gson
import java.io.File

/** [Photo.url] is either the absolute path of a locally cached image or an http(s) base URL (shared albums). */
data class Photo(val title: String, val url: String)

/** [sourceUrl] is the shared-album link to re-read periodically; null for albums made with the picker. */
data class Album(
    val id: String,
    val title: String,
    val webUrl: String,
    val photos: List<Photo>,
    val sourceUrl: String? = null
)

/** Per-widget state (selected album + current index), stored in SharedPreferences. */
class WidgetPreferencesRepository(context: Context) {

    private val appContext = context.applicationContext
    private val prefs = appContext.getSharedPreferences("widget_prefs", Context.MODE_PRIVATE)
    private val gson = Gson()

    /** Directory holding the cached photos of one widget; each pick uses its own sub-directory. */
    fun newPhotoDir(appWidgetId: Int): File =
        File(appContext.filesDir, "photos/$appWidgetId/${System.currentTimeMillis()}")

    fun saveSelectedAlbum(appWidgetId: Int, album: Album) {
        prefs.edit()
            .putString("album_$appWidgetId", gson.toJson(album))
            .putInt("active_widget", appWidgetId)
            .apply()
        // Drop photos cached by earlier picks of this widget.
        val keep = album.photos.firstOrNull()?.takeIf { !it.url.startsWith("http") }?.let { File(it.url).parentFile }
        File(appContext.filesDir, "photos/$appWidgetId").listFiles()
            ?.filter { it != keep }
            ?.forEach { it.deleteRecursively() }
    }

    fun getSelectedAlbum(appWidgetId: Int): Album? =
        prefs.getString("album_$appWidgetId", null)?.let {
            runCatching { gson.fromJson(it, Album::class.java) }.getOrNull()
        }

    fun getCurrentPhotoIndex(appWidgetId: Int): Int = prefs.getInt("index_$appWidgetId", 0)

    fun setCurrentPhotoIndex(appWidgetId: Int, index: Int) {
        prefs.edit().putInt("index_$appWidgetId", index).apply()
        prefs.edit().putInt("active_widget", appWidgetId).apply()
    }

    fun clear(appWidgetId: Int) {
        prefs.edit().remove("album_$appWidgetId").remove("index_$appWidgetId").apply()
        File(appContext.filesDir, "photos/$appWidgetId").deleteRecursively()
    }

    // For PhotosGlanceWidget, which is not per-instance: shows the most recently used widget's album.
    private fun activeWidgetId(): Int? = prefs.getInt("active_widget", -1).takeIf { it != -1 }

    fun getActiveAlbum(): Album? = activeWidgetId()?.let(::getSelectedAlbum)

    fun getActivePhotoIndex(): Int = activeWidgetId()?.let(::getCurrentPhotoIndex) ?: 0
}
