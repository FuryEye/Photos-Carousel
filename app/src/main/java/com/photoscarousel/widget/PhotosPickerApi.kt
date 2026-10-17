package com.photoscarousel.widget

import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

// Models for the Google Photos Picker API (https://developers.google.com/photos/picker/reference/rest).
// Kept by proguard-rules.pro because Gson maps them by field name.

data class PickingConfig(val maxItemCount: String)

data class CreateSessionRequest(val pickingConfig: PickingConfig? = null)

data class PollingConfig(val pollInterval: String? = null, val timeoutIn: String? = null)

data class PickerSession(
    val id: String,
    val pickerUri: String,
    val pollingConfig: PollingConfig? = null,
    val mediaItemsSet: Boolean = false
)

data class MediaFile(val baseUrl: String, val mimeType: String? = null, val filename: String? = null)

data class PickedMediaItem(val id: String, val type: String? = null, val mediaFile: MediaFile? = null)

data class ListMediaItemsResponse(
    val mediaItems: List<PickedMediaItem>? = null,
    val nextPageToken: String? = null
)

interface PhotosPickerApi {

    @POST("v1/sessions")
    suspend fun createSession(
        @Header("Authorization") auth: String,
        @Body body: CreateSessionRequest
    ): PickerSession

    @GET("v1/sessions/{id}")
    suspend fun getSession(
        @Header("Authorization") auth: String,
        @Path("id") id: String
    ): PickerSession

    @GET("v1/mediaItems")
    suspend fun listMediaItems(
        @Header("Authorization") auth: String,
        @Query("sessionId") sessionId: String,
        @Query("pageSize") pageSize: Int = 100,
        @Query("pageToken") pageToken: String? = null
    ): ListMediaItemsResponse

    @DELETE("v1/sessions/{id}")
    suspend fun deleteSession(
        @Header("Authorization") auth: String,
        @Path("id") id: String
    ): Response<Unit>
}
