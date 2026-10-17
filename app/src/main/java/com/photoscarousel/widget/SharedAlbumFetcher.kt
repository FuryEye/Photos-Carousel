package com.photoscarousel.widget

import android.content.Context
import android.text.Html
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException

/**
 * Reads the public page of a link-shared Google Photos album and extracts its image URLs.
 * This is unofficial: it relies on the page embedding `lh3.googleusercontent.com/pw/...` URLs
 * and may break if Google changes the page. All calls block, so run them off the main thread.
 */
object SharedAlbumFetcher {

    private const val USER_AGENT =
        "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0 Mobile Safari/537.36"

    private val http = OkHttpClient()
    private val photoRegex = Regex("""https://lh3\.googleusercontent\.com/pw/[\w-]+""")
    private val titleRegex = Regex("""<title>(.*?)</title>""", RegexOption.DOT_MATCHES_ALL)

    class Result(val title: String, val finalUrl: String, val photos: List<Photo>)

    fun isAlbumLink(url: String): Boolean {
        val parsed = url.toHttpUrlOrNull() ?: return false
        return parsed.isHttps && (parsed.host == "photos.app.goo.gl" || parsed.host == "photos.google.com")
    }

    fun fetch(url: String): Result {
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", USER_AGENT)
            .header("Accept-Language", "en")
            .build()
        http.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException("Could not open the link: HTTP ${response.code}")
            val finalUrl = response.request.url.toString()
            val html = response.body!!.string().replace("\\/", "/")

            val urls = photoRegex.findAll(html).map { it.value }.distinct().toList()
            if (urls.isEmpty()) {
                throw IOException("No photos found. Make sure the album is shared with a link.")
            }
            val title = titleRegex.find(html)?.groupValues?.get(1)
                ?.let { Html.fromHtml(it, Html.FROM_HTML_MODE_LEGACY).toString() }
                ?.removeSuffix(" - Google Photos")?.trim()
                ?.takeIf { it.isNotEmpty() && it != "Google Photos" }
                ?: "Shared album"
            return Result(title, finalUrl, urls.mapIndexed { i, base -> Photo("Photo ${i + 1}", base) })
        }
    }

    /** Re-reads a link-based widget's album. Returns true if the stored photos changed. */
    fun refresh(context: Context, appWidgetId: Int): Boolean {
        val repository = WidgetPreferencesRepository(context)
        val album = repository.getSelectedAlbum(appWidgetId) ?: return false
        val source = album.sourceUrl ?: return false
        val result = runCatching { fetch(source) }.getOrNull() ?: return false
        if (result.photos == album.photos) return false
        repository.saveSelectedAlbum(appWidgetId, album.copy(title = result.title, photos = result.photos))
        return true
    }
}
