package com.coppersmith.music2.cast

import android.net.Uri
import androidx.annotation.OptIn
import androidx.media3.cast.DefaultMediaItemConverter
import androidx.media3.cast.MediaItemConverter
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.util.UnstableApi
import com.google.android.gms.cast.MediaInfo
import com.google.android.gms.cast.MediaQueueItem
import com.google.android.gms.cast.MediaMetadata as CastMediaMetadata
import java.util.UUID

@OptIn(UnstableApi::class)
class SafeMediaItemConverter : MediaItemConverter {

    private val defaultConverter = DefaultMediaItemConverter()

    override fun toMediaItem(mediaQueueItem: MediaQueueItem): MediaItem {
        val mediaInfo = mediaQueueItem.media ?: return MediaItem.EMPTY

        val customData = mediaInfo.customData
        if (customData != null && customData.has("mediaItem")) {
            try {
                return defaultConverter.toMediaItem(mediaQueueItem)
            } catch (_: Exception) {
                // Fallback if DefaultMediaItemConverter fails to parse customData
            }
        }

        val metadataBuilder = MediaMetadata.Builder()
        val castMetadata: CastMediaMetadata? = mediaInfo.metadata

        if (castMetadata != null) {
            if (castMetadata.containsKey(CastMediaMetadata.KEY_TITLE)) {
                metadataBuilder.setTitle(castMetadata.getString(CastMediaMetadata.KEY_TITLE))
            }
            if (castMetadata.containsKey(CastMediaMetadata.KEY_SUBTITLE)) {
                metadataBuilder.setSubtitle(castMetadata.getString(CastMediaMetadata.KEY_SUBTITLE))
            }
            if (castMetadata.containsKey(CastMediaMetadata.KEY_ARTIST)) {
                metadataBuilder.setArtist(castMetadata.getString(CastMediaMetadata.KEY_ARTIST))
            }
            if (castMetadata.containsKey(CastMediaMetadata.KEY_ALBUM_ARTIST)) {
                metadataBuilder.setAlbumArtist(castMetadata.getString(CastMediaMetadata.KEY_ALBUM_ARTIST))
            }
            if (castMetadata.containsKey(CastMediaMetadata.KEY_ALBUM_TITLE)) {
                metadataBuilder.setAlbumTitle(castMetadata.getString(CastMediaMetadata.KEY_ALBUM_TITLE))
            }
            if (castMetadata.images.isNotEmpty()) {
                metadataBuilder.setArtworkUri(castMetadata.images[0].url)
            }
            if (castMetadata.containsKey(CastMediaMetadata.KEY_COMPOSER)) {
                metadataBuilder.setComposer(castMetadata.getString(CastMediaMetadata.KEY_COMPOSER))
            }
            if (castMetadata.containsKey(CastMediaMetadata.KEY_DISC_NUMBER)) {
                metadataBuilder.setDiscNumber(castMetadata.getInt(CastMediaMetadata.KEY_DISC_NUMBER))
            }
            if (castMetadata.containsKey(CastMediaMetadata.KEY_TRACK_NUMBER)) {
                metadataBuilder.setTrackNumber(castMetadata.getInt(CastMediaMetadata.KEY_TRACK_NUMBER))
            }
        }

        val contentUrl = mediaInfo.contentUrl
        val contentId = mediaInfo.contentId
        val rawUri = contentUrl ?: (contentId ?: "")
        val uri = if (rawUri.isNotEmpty()) Uri.parse(rawUri) else Uri.EMPTY
        val mediaId = contentId ?: (contentUrl ?: UUID.randomUUID().toString())

        val builder = MediaItem.Builder()
            .setMediaId(mediaId)
            .setUri(uri)
            .setMediaMetadata(metadataBuilder.build())

        if (!mediaInfo.contentType.isNullOrEmpty()) {
            builder.setMimeType(mediaInfo.contentType)
        }

        return builder.build()
    }

    override fun toMediaQueueItem(mediaItem: MediaItem): MediaQueueItem {
        val mimeType = mediaItem.localConfiguration?.mimeType ?: "audio/mpeg"
        val uriStr = mediaItem.localConfiguration?.uri?.toString() ?: mediaItem.mediaId
        val castMetadata = CastMediaMetadata(CastMediaMetadata.MEDIA_TYPE_MUSIC_TRACK)

        mediaItem.mediaMetadata.title?.let { castMetadata.putString(CastMediaMetadata.KEY_TITLE, it.toString()) }
        mediaItem.mediaMetadata.subtitle?.let { castMetadata.putString(CastMediaMetadata.KEY_SUBTITLE, it.toString()) }
        mediaItem.mediaMetadata.artist?.let { castMetadata.putString(CastMediaMetadata.KEY_ARTIST, it.toString()) }
        mediaItem.mediaMetadata.albumArtist?.let { castMetadata.putString(CastMediaMetadata.KEY_ALBUM_ARTIST, it.toString()) }
        mediaItem.mediaMetadata.albumTitle?.let { castMetadata.putString(CastMediaMetadata.KEY_ALBUM_TITLE, it.toString()) }
        mediaItem.mediaMetadata.artworkUri?.let { castMetadata.addImage(com.google.android.gms.common.images.WebImage(it)) }

        val mediaInfo = MediaInfo.Builder(uriStr)
            .setStreamType(MediaInfo.STREAM_TYPE_LIVE)
            .setContentType(mimeType)
            .setContentUrl(uriStr)
            .setMetadata(castMetadata)
            .build()

        return MediaQueueItem.Builder(mediaInfo).build()
    }
}