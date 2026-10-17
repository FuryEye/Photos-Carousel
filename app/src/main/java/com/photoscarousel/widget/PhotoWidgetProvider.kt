package com.photoscarousel.widget

import android.app.AlarmManager
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.util.Log
import android.widget.RemoteViews
import com.bumptech.glide.Glide
import com.bumptech.glide.load.DataSource
import com.bumptech.glide.load.engine.GlideException
import com.bumptech.glide.request.RequestListener
import com.bumptech.glide.request.target.AppWidgetTarget
import com.bumptech.glide.request.target.Target
import java.io.File
import kotlin.concurrent.thread

/**
 * Android AppWidgetProvider for the photo carousel.
 *
 * Tapping the photo opens the album in Google Photos, the arrows move manually, and a one-shot
 * alarm re-armed every [ADVANCE_INTERVAL_MS] advances all widgets automatically. Shared-album
 * widgets are also re-read from the web on every [onUpdate].
 */
class PhotoWidgetProvider : AppWidgetProvider() {

    companion object {
        const val ACTION_NEXT_PHOTO = "com.photoscarousel.widget.ACTION_NEXT_PHOTO"
        const val ACTION_PREV_PHOTO = "com.photoscarousel.widget.ACTION_PREV_PHOTO"
        const val ACTION_AUTO_ADVANCE = "com.photoscarousel.widget.ACTION_AUTO_ADVANCE"
        const val EXTRA_WIDGET_ID = "appWidgetId"
        const val ADVANCE_INTERVAL_MS = 15_000L

        /** [skipped] counts consecutive items skipped because they failed to load (bounds the retry chain). */
        fun updateWidget(context: Context, appWidgetManager: AppWidgetManager, appWidgetId: Int, skipped: Int = 0) {
            val repository = WidgetPreferencesRepository(context)
            val album = repository.getSelectedAlbum(appWidgetId)
            val currentIndex = repository.getCurrentPhotoIndex(appWidgetId)

            val views = RemoteViews(context.packageName, R.layout.widget_photos_carousel)

            if (album == null || album.photos.isEmpty()) {
                // Empty state: tap to configure
                views.setTextViewText(R.id.tv_album_title, "Tap to Select Album")
                views.setTextViewText(R.id.tv_photo_caption, "Google Photos Carousel")
                views.setTextViewText(R.id.tv_counter, "")
                views.setImageViewResource(R.id.iv_photo, R.drawable.placeholder_photos)

                val configIntent = Intent(context, AlbumPickerActivity::class.java).apply {
                    putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
                }
                val configPendingIntent = PendingIntent.getActivity(
                    context, appWidgetId, configIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                views.setOnClickPendingIntent(R.id.widget_container, configPendingIntent)
                appWidgetManager.updateAppWidget(appWidgetId, views)
                return
            }

            val safeIndex = currentIndex.coerceIn(0, album.photos.size - 1)
            val currentPhoto = album.photos[safeIndex]

            // 1. Update text metadata
            views.setTextViewText(R.id.tv_album_title, album.title)
            views.setTextViewText(R.id.tv_photo_caption, currentPhoto.title)
            views.setTextViewText(R.id.tv_counter, "${safeIndex + 1} / ${album.photos.size}")

            // 2. Open Google Photos app when tapping the photo
            val albumUrl = album.webUrl.ifEmpty { "https://photos.google.com/album/${album.id}" }
            val photosAppIntent = Intent(Intent.ACTION_VIEW).apply {
                data = Uri.parse(albumUrl)
                setPackage("com.google.android.apps.photos")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            val openAlbumPendingIntent = PendingIntent.getActivity(
                context, appWidgetId * 10, photosAppIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.iv_photo, openAlbumPendingIntent)
            views.setOnClickPendingIntent(R.id.layout_info, openAlbumPendingIntent)

            // 3. Manual previous / next
            views.setOnClickPendingIntent(R.id.btn_next, navIntent(context, appWidgetId, ACTION_NEXT_PHOTO, 1))
            views.setOnClickPendingIntent(R.id.btn_prev, navIntent(context, appWidgetId, ACTION_PREV_PHOTO, 2))

            // 4. Load the photo (local file for picked photos, resized web URL for shared albums)
            val model: Any = if (currentPhoto.url.startsWith("http")) currentPhoto.url + "=w1000-h1000" else File(currentPhoto.url)
            Glide.with(context.applicationContext)
                .asBitmap()
                .load(model)
                .centerCrop()
                .listener(object : RequestListener<Bitmap> {
                    override fun onLoadFailed(e: GlideException?, model: Any?, target: Target<Bitmap>, isFirstResource: Boolean): Boolean {
                        Log.e("PhotoWidget", "Photo load failed: $model", e)
                        if (skipped < album.photos.size - 1) {
                            // Unloadable item (e.g. a video): move on to the next one instead of freezing.
                            repository.setCurrentPhotoIndex(appWidgetId, (safeIndex + 1) % album.photos.size)
                            updateWidget(context, appWidgetManager, appWidgetId, skipped + 1)
                        } else {
                            val reason = e?.rootCauses?.firstOrNull()?.message ?: "unknown error"
                            views.setTextViewText(R.id.tv_counter, "Couldn't load photo: $reason")
                            appWidgetManager.updateAppWidget(appWidgetId, views)
                        }
                        return false
                    }

                    override fun onResourceReady(
                        resource: Bitmap, model: Any, target: Target<Bitmap>?, dataSource: DataSource, isFirstResource: Boolean
                    ): Boolean = false
                })
                .into(AppWidgetTarget(context, R.id.iv_photo, views, appWidgetId))

            appWidgetManager.updateAppWidget(appWidgetId, views)
        }

        private fun navIntent(context: Context, appWidgetId: Int, action: String, offset: Int): PendingIntent {
            val intent = Intent(context, PhotoWidgetProvider::class.java).apply {
                this.action = action
                putExtra(EXTRA_WIDGET_ID, appWidgetId)
            }
            return PendingIntent.getBroadcast(
                context, appWidgetId * 10 + offset, intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        }

        private fun autoAdvancePendingIntent(context: Context): PendingIntent {
            val intent = Intent(context, PhotoWidgetProvider::class.java).apply { action = ACTION_AUTO_ADVANCE }
            return PendingIntent.getBroadcast(
                context, 0, intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        }

        /**
         * One-shot, non-waking alarm: it fires while the phone is awake and is simply postponed
         * while it sleeps, so it costs no battery in the background.
         */
        fun scheduleAutoAdvance(context: Context) {
            val alarms = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            alarms.set(AlarmManager.RTC, System.currentTimeMillis() + ADVANCE_INTERVAL_MS, autoAdvancePendingIntent(context))
        }

        private fun cancelAutoAdvance(context: Context) {
            (context.getSystemService(Context.ALARM_SERVICE) as AlarmManager).cancel(autoAdvancePendingIntent(context))
        }

        private fun shiftPhoto(context: Context, appWidgetId: Int, delta: Int) {
            val repository = WidgetPreferencesRepository(context)
            val size = repository.getSelectedAlbum(appWidgetId)?.photos?.size ?: return
            if (size == 0) return
            val next = ((repository.getCurrentPhotoIndex(appWidgetId) + delta) % size + size) % size
            repository.setCurrentPhotoIndex(appWidgetId, next)
            updateWidget(context, AppWidgetManager.getInstance(context), appWidgetId)
        }
    }

    override fun onEnabled(context: Context) = scheduleAutoAdvance(context)

    override fun onDisabled(context: Context) = cancelAutoAdvance(context)

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        for (appWidgetId in appWidgetIds) {
            updateWidget(context, appWidgetManager, appWidgetId)
        }
        scheduleAutoAdvance(context)
        // Shared-album widgets: re-read the album in the background and redraw if photos were added/removed.
        val pending = goAsync()
        val appContext = context.applicationContext
        thread {
            try {
                for (appWidgetId in appWidgetIds) {
                    if (SharedAlbumFetcher.refresh(appContext, appWidgetId)) {
                        updateWidget(appContext, appWidgetManager, appWidgetId)
                    }
                }
            } finally {
                pending.finish()
            }
        }
    }

    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        val repository = WidgetPreferencesRepository(context)
        appWidgetIds.forEach(repository::clear)
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        when (intent.action) {
            ACTION_AUTO_ADVANCE -> {
                val manager = AppWidgetManager.getInstance(context)
                val ids = manager.getAppWidgetIds(ComponentName(context, PhotoWidgetProvider::class.java))
                if (ids.isEmpty()) return // no widgets left: let the alarm chain end
                ids.forEach { shiftPhoto(context, it, 1) }
                scheduleAutoAdvance(context)
            }
            ACTION_NEXT_PHOTO, ACTION_PREV_PHOTO -> {
                val appWidgetId = intent.getIntExtra(EXTRA_WIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
                if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) return
                shiftPhoto(context, appWidgetId, if (intent.action == ACTION_NEXT_PHOTO) 1 else -1)
            }
        }
    }
}
