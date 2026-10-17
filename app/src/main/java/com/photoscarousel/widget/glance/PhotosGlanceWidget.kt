package com.photoscarousel.widget.glance

import android.content.Context
import androidx.compose.ui.graphics.Color
import android.content.Intent
import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.*
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.layout.*
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.color.ColorProvider
import com.photoscarousel.widget.WidgetPreferencesRepository

/**
 * Jetpack Glance Compose Widget.
 * Delivers dynamic Material You theming and smooth gesture interactions.
 */
class PhotosGlanceWidget : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        provideContent {
            WidgetContent(context)
        }
    }

    @Composable
    private fun WidgetContent(context: Context) {
        val repo = WidgetPreferencesRepository(context)
        val album = repo.getActiveAlbum()
        val photoIndex = repo.getActivePhotoIndex()

        val activePhoto = album?.photos?.getOrNull(photoIndex)
        val albumUrl = album?.webUrl ?: "https://photos.google.com"

        // Intent to launch Google Photos app directly to this album
        val photosIntent = Intent(Intent.ACTION_VIEW, Uri.parse(albumUrl)).apply {
            setPackage("com.google.android.apps.photos")
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }

        Box(
            modifier = GlanceModifier
                .fillMaxSize()
                .cornerRadius(24.dp)
                .clickable(actionStartActivity(photosIntent))
        ) {
            // Overlay and Album Info
            Column(
                modifier = GlanceModifier
                    .fillMaxSize()
                    .padding(12.dp),
                verticalAlignment = Alignment.Bottom
            ) {
                Text(
                    text = album?.title ?: "Select Google Photos Album",
                    style = TextStyle(
                        color = ColorProvider(day = Color.White, night = Color.White),
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                )
                if (activePhoto != null) {
                    Text(
                        text = "${activePhoto.title}\n${photoIndex + 1}/${album?.photos?.size ?: 1}",
                        style = TextStyle(
                            color = ColorProvider(day = Color.White, night = Color.White),
                            fontSize = 12.sp
                        )
                    )
                }
            }
        }
    }
}

class PhotosGlanceWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = PhotosGlanceWidget()
}