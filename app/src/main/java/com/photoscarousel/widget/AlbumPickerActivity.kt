package com.photoscarousel.widget

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ProgressBar
import android.widget.TextView
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import retrofit2.HttpException
import java.text.DateFormat
import java.util.Date

/**
 * Configure Activity for the Widget.
 * Signs the user in, opens the Google Photos picker, then downloads the picked photos for the widget.
 */
class AlbumPickerActivity : AppCompatActivity() {

    private companion object {
        const val TAG = "AlbumPicker"
    }

    private var appWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID
    private lateinit var photosManager: GooglePhotosManager
    private lateinit var tvStatus: TextView
    private lateinit var progressBar: ProgressBar
    private lateinit var btnPick: Button
    private lateinit var btnLink: Button
    private lateinit var etLink: EditText

    private val consentLauncher = registerForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
        Log.d(TAG, "Consent result code=${result.resultCode}")
        if (result.resultCode != Activity.RESULT_OK) {
            showIdle("Google Photos access was not granted (result code ${result.resultCode}). Tap Pick photos to retry.")
            return@registerForActivityResult
        }
        lifecycleScope.launch {
            runFlow {
                val token = photosManager.authorizationFromIntent(result.data).accessToken
                Log.d(TAG, "Consent granted, token present=${token != null}")
                proceed(token ?: error("Google returned no access token"))
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setResult(Activity.RESULT_CANCELED)
        setContentView(R.layout.activity_album_picker)

        tvStatus = findViewById(R.id.tvStatus)
        progressBar = findViewById(R.id.progressBar)
        btnPick = findViewById(R.id.btnPick)

        appWidgetId = intent.getIntExtra(
            AppWidgetManager.EXTRA_APPWIDGET_ID,
            AppWidgetManager.INVALID_APPWIDGET_ID
        )
        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            finish()
            return
        }

        photosManager = GooglePhotosManager(this)
        btnLink = findViewById(R.id.btnLink)
        etLink = findViewById(R.id.etLink)
        btnPick.setOnClickListener { beginPicking() }
        btnLink.setOnClickListener { lifecycleScope.launch { runFlow { useAlbumLink() } } }
    }

    private suspend fun useAlbumLink() {
        val link = etLink.text.toString().trim()
        if (!SharedAlbumFetcher.isAlbumLink(link)) {
            showIdle("Paste a Google Photos share link (https://photos.app.goo.gl/… or https://photos.google.com/share/…).")
            return
        }
        showBusy("Reading album…")
        val result = withContext(Dispatchers.IO) { SharedAlbumFetcher.fetch(link) }
        saveWidgetChoiceAndFinish(
            Album(
                id = link,
                title = result.title,
                webUrl = result.finalUrl,
                photos = result.photos,
                sourceUrl = link
            )
        )
    }

    private fun beginPicking() {
        lifecycleScope.launch { runFlow { startPicking() } }
    }

    private suspend fun startPicking() {
        showBusy("Signing in…")
        val auth = photosManager.authorize()
        Log.d(TAG, "authorize() hasResolution=${auth.hasResolution()}")
        if (auth.hasResolution()) {
            consentLauncher.launch(IntentSenderRequest.Builder(auth.pendingIntent!!.intentSender).build())
        } else {
            proceed(auth.accessToken!!)
        }
    }

    private suspend fun proceed(token: String) {
        showBusy("Starting Google Photos picker…")
        val session = photosManager.createSession(token)
        startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(session.pickerUri + "/autoclose")))
        showBusy("Pick your photos in Google Photos, then come back here.")

        try {
            if (!photosManager.awaitPicked(token, session)) {
                showIdle("Timed out waiting for a selection.")
                return
            }
            showBusy("Downloading photos…")
            val items = photosManager.listPickedPhotos(token, session.id)
            if (items.isEmpty()) {
                showIdle("No photos were selected.")
                return
            }
            val photos = photosManager.downloadPhotos(token, items, WidgetPreferencesRepository(this).newPhotoDir(appWidgetId)) { done, total ->
                tvStatus.text = "Downloading photos… $done / $total"
            }
            val album = Album(
                id = session.id,
                title = "Photos · " + DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date()),
                webUrl = "https://photos.google.com",
                photos = photos
            )
            saveWidgetChoiceAndFinish(album)
        } finally {
            photosManager.deleteSession(token, session.id)
        }
    }

    /** Runs [block], reporting failures in the UI instead of crashing. */
    private suspend fun runFlow(block: suspend () -> Unit) {
        try {
            block()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Picker flow failed", e)
            val detail = (e as? HttpException)?.response()?.errorBody()?.string()?.take(400)
            showIdle("Failed: ${e.message ?: e.javaClass.simpleName}${detail?.let { "\n$it" } ?: ""}")
        }
    }

    private fun showBusy(message: String) {
        tvStatus.text = message
        progressBar.visibility = View.VISIBLE
        btnPick.isEnabled = false
        btnLink.isEnabled = false
    }

    private fun showIdle(message: String) {
        tvStatus.text = message
        progressBar.visibility = View.GONE
        btnPick.isEnabled = true
        btnLink.isEnabled = true
    }

    private fun saveWidgetChoiceAndFinish(album: Album) {
        val repository = WidgetPreferencesRepository(this)
        repository.saveSelectedAlbum(appWidgetId, album)
        repository.setCurrentPhotoIndex(appWidgetId, 0)

        PhotoWidgetProvider.updateWidget(this, AppWidgetManager.getInstance(this), appWidgetId)

        setResult(Activity.RESULT_OK, Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId))
        finish()
    }
}
