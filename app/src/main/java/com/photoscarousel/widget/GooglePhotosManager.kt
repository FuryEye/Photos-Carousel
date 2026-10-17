package com.photoscarousel.widget

import android.app.Activity
import android.content.Intent
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.AuthorizationResult
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.common.api.Scope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.io.File
import java.io.IOException

/**
 * Talks to the Google Photos Picker API. The Library API can no longer list a user's albums
 * (since 2025-03-31), so the user picks photos in Google's own picker UI and we download them.
 * Picked media `baseUrl`s expire after about an hour, so photos are cached to local files.
 */
class GooglePhotosManager(private val activity: Activity) {

    companion object {
        const val PICKER_SCOPE = "https://www.googleapis.com/auth/photospicker.mediaitems.readonly"
        const val MAX_PHOTOS = 2000 // Picker API maximum per session
        private const val DOWNLOAD_SIZE = "=w800-h800"
        private const val POLL_TIMEOUT_MS = 10 * 60 * 1000L
    }

    private val http = OkHttpClient()
    private val api: PhotosPickerApi = Retrofit.Builder()
        .baseUrl("https://photospicker.googleapis.com/")
        .client(http)
        .addConverterFactory(GsonConverterFactory.create())
        .build()
        .create(PhotosPickerApi::class.java)

    private fun bearer(token: String) = "Bearer $token"

    /** Returns a result that either carries an access token or a [AuthorizationResult.pendingIntent] to consent. */
    suspend fun authorize(): AuthorizationResult {
        val request = AuthorizationRequest.builder()
            .setRequestedScopes(listOf(Scope(PICKER_SCOPE)))
            .build()
        return Identity.getAuthorizationClient(activity).authorize(request).await()
    }

    /** Parses the result of the consent intent launched from [AuthorizationResult.pendingIntent]. */
    fun authorizationFromIntent(data: Intent?): AuthorizationResult =
        Identity.getAuthorizationClient(activity).getAuthorizationResultFromIntent(data)

    suspend fun createSession(token: String): PickerSession =
        api.createSession(bearer(token), CreateSessionRequest(PickingConfig(MAX_PHOTOS.toString())))

    /** Polls until the user finishes picking. Returns false if the session timed out. */
    suspend fun awaitPicked(token: String, session: PickerSession): Boolean {
        val intervalMs = parseSeconds(session.pollingConfig?.pollInterval, 3.0).coerceAtLeast(2_000L)
        val timeoutMs = parseSeconds(session.pollingConfig?.timeoutIn, POLL_TIMEOUT_MS / 1000.0)
            .coerceAtMost(POLL_TIMEOUT_MS)
        var waited = 0L
        while (waited < timeoutMs) {
            delay(intervalMs)
            waited += intervalMs
            if (api.getSession(bearer(token), session.id).mediaItemsSet) return true
        }
        return false
    }

    suspend fun listPickedPhotos(token: String, sessionId: String): List<PickedMediaItem> {
        val items = mutableListOf<PickedMediaItem>()
        var pageToken: String? = null
        do {
            val page = api.listMediaItems(bearer(token), sessionId, pageToken = pageToken)
            items += page.mediaItems.orEmpty().filter { it.type == "PHOTO" && it.mediaFile != null }
            pageToken = page.nextPageToken
        } while (pageToken != null && items.size < MAX_PHOTOS)
        return items.take(MAX_PHOTOS)
    }

    /** Downloads each photo into [targetDir] and returns [Photo]s whose url is the local file path. */
    suspend fun downloadPhotos(
        token: String,
        items: List<PickedMediaItem>,
        targetDir: File,
        onProgress: (done: Int, total: Int) -> Unit = { _, _ -> }
    ): List<Photo> =
        withContext(Dispatchers.IO) {
            targetDir.mkdirs()
            items.mapIndexed { index, item ->
                val file = File(targetDir, "$index.jpg")
                val request = Request.Builder()
                    .url(item.mediaFile!!.baseUrl + DOWNLOAD_SIZE)
                    .header("Authorization", bearer(token))
                    .build()
                http.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) throw IOException("Photo download failed: HTTP ${response.code}")
                    file.outputStream().use { out -> response.body!!.byteStream().copyTo(out) }
                }
                withContext(Dispatchers.Main) { onProgress(index + 1, items.size) }
                Photo(item.mediaFile.filename ?: "Photo ${index + 1}", file.absolutePath)
            }
        }

    suspend fun deleteSession(token: String, sessionId: String) {
        runCatching { api.deleteSession(bearer(token), sessionId) }
    }

    /** Parses protobuf-style durations such as "5s" or "3.5s" into milliseconds. */
    private fun parseSeconds(value: String?, default: Double): Long =
        ((value?.removeSuffix("s")?.toDoubleOrNull() ?: default) * 1000).toLong()
}
