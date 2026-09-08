package com.seweryn.radiocar.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.seweryn.radiocar.MainActivity
import com.seweryn.radiocar.R
import com.seweryn.radiocar.data.model.Station
import com.seweryn.radiocar.data.repository.StationRepository

@UnstableApi
class RadioPlayerService : MediaSessionService() {

    private lateinit var player: ExoPlayer
    private var mediaSession: MediaSession? = null
    private lateinit var repository: StationRepository
    private var currentStation: Station? = null

    override fun onCreate() {
        super.onCreate()
        repository = StationRepository(this)
        createNotificationChannel()

        val audioAttributes = AudioAttributes.Builder()
            .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
            .setUsage(C.USAGE_MEDIA)
            .build()

        // Robust HTTP DataSource with cross-protocol redirect support and standard User-Agent
        val httpDataSourceFactory = DefaultHttpDataSource.Factory()
            .setUserAgent("RadioCar/1.0 (Linux; Android; ExoPlayer)")
            .setAllowCrossProtocolRedirects(true)
            .setKeepPostFor302Redirects(true)
            .setConnectTimeoutMs(15000)
            .setReadTimeoutMs(15000)

        val dataSourceFactory = DefaultDataSource.Factory(this, httpDataSourceFactory)
        val mediaSourceFactory = DefaultMediaSourceFactory(this)
            .setDataSourceFactory(dataSourceFactory)

        player = ExoPlayer.Builder(this)
            .setMediaSourceFactory(mediaSourceFactory)
            .setAudioAttributes(audioAttributes, true)
            .setWakeMode(C.WAKE_MODE_NETWORK)
            .setHandleAudioBecomingNoisy(true)
            .build()

        player.addListener(object : Player.Listener {
            override fun onMediaMetadataChanged(mediaMetadata: MediaMetadata) {
                // ICY Metadata received from Icecast/Shoutcast stream
                handleIcyMetadata(mediaMetadata)
            }

            override fun onPlayerError(error: PlaybackException) {
                Log.e("RadioPlayerService", "Player error for station ${currentStation?.name}: ${error.message} (code: ${error.errorCode})", error)
            }
        })

        val openAppIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this, 0, openAppIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        mediaSession = MediaSession.Builder(this, player)
            .setSessionActivity(pendingIntent)
            .setCallback(CustomMediaSessionCallback())
            .build()

        // Prepare last active station so it is ready on Bluetooth connect
        val lastStationId = repository.getLastStationId()
        val station = repository.getStationById(lastStationId) ?: repository.stations.value.firstOrNull { !it.isEmpty }
        if (station != null) {
            playStation(station, autoPlay = false)
        }
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? {
        return mediaSession
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action
        if (action == ACTION_PLAY_STATION) {
            val stationId = intent.getIntExtra(EXTRA_STATION_ID, -1)
            if (stationId != -1) {
                repository.getStationById(stationId)?.let {
                    playStation(it, autoPlay = true)
                }
            }
        } else if (action == ACTION_RELOAD_STREAM) {
            reloadCurrentStream()
        }
        return super.onStartCommand(intent, flags, startId)
    }

    fun playStation(station: Station, autoPlay: Boolean = true) {
        if (station.streamUrl.isBlank()) return
        currentStation = station
        repository.saveLastStationId(station.id)

        try {
            val metadata = MediaMetadata.Builder()
                .setTitle(station.name)
                .setArtist("Radio Car")
                .setAlbumTitle(station.name)
                .setDisplayTitle(station.name)
                .setArtworkUri(if (station.logoUrl.isNotBlank()) Uri.parse(station.logoUrl) else null)
                .build()

            val mediaItem = MediaItem.Builder()
                .setMediaId(station.id.toString())
                .setUri(station.streamUrl)
                .setMediaMetadata(metadata)
                .build()

            player.setMediaItem(mediaItem)
            player.prepare()
            if (autoPlay) {
                player.play()
            }
        } catch (_: Exception) {}
    }

    fun reloadCurrentStream() {
        currentStation?.let {
            playStation(it, autoPlay = true)
        } ?: run {
            player.prepare()
            player.play()
        }
    }

    private fun handleIcyMetadata(mediaMetadata: MediaMetadata) {
        val station = currentStation ?: return
        val rawTitle = mediaMetadata.title?.toString()
        if (!rawTitle.isNullOrBlank()) {
            var artist = ""
            var title = rawTitle.trim()

            // Often stream title is "Artist - Song Title"
            if (rawTitle.contains(" - ")) {
                val parts = rawTitle.split(" - ", limit = 2)
                artist = parts[0].trim()
                title = parts[1].trim()
            }

            // Update session and bluetooth metadata so car display shows the song & artist
            val updatedMetadata = MediaMetadata.Builder()
                .setTitle(title)
                .setArtist(if (artist.isNotBlank()) artist else station.name)
                .setAlbumTitle(station.name)
                .setDisplayTitle(title)
                .setSubtitle(artist)
                .setArtworkUri(if (station.logoUrl.isNotBlank()) Uri.parse(station.logoUrl) else null)
                .build()

            val currentItem = player.currentMediaItem
            if (currentItem != null) {
                val updatedItem = currentItem.buildUpon()
                    .setMediaMetadata(updatedMetadata)
                    .build()
                player.replaceMediaItem(player.currentMediaItemIndex, updatedItem)
            }
        }
    }

    private fun playNextStation() {
        val list = repository.stations.value.filter { !it.isEmpty }
        if (list.isEmpty()) return
        val currentIndex = list.indexOfFirst { it.id == currentStation?.id }
        val nextIndex = if (currentIndex != -1) (currentIndex + 1) % list.size else 0
        playStation(list[nextIndex], autoPlay = true)
    }

    private fun playPrevStation() {
        val list = repository.stations.value.filter { !it.isEmpty }
        if (list.isEmpty()) return
        val currentIndex = list.indexOfFirst { it.id == currentStation?.id }
        val prevIndex = if (currentIndex > 0) currentIndex - 1 else list.size - 1
        playStation(list[prevIndex], autoPlay = true)
    }

    private inner class CustomMediaSessionCallback : MediaSession.Callback {
        override fun onPlaybackResumption(
            mediaSession: MediaSession,
            controller: MediaSession.ControllerInfo
        ): com.google.common.util.concurrent.ListenableFuture<androidx.media3.session.MediaSession.MediaItemsWithStartPosition> {
            // Auto resume playback on car Bluetooth connect
            currentStation?.let {
                playStation(it, autoPlay = true)
            }
            return super.onPlaybackResumption(mediaSession, controller)
        }

        // Support steering wheel previous/next buttons
        override fun onCustomCommand(
            session: MediaSession,
            controller: MediaSession.ControllerInfo,
            customCommand: androidx.media3.session.SessionCommand,
            args: android.os.Bundle
        ): com.google.common.util.concurrent.ListenableFuture<androidx.media3.session.SessionResult> {
            when (customCommand.customAction) {
                ACTION_NEXT -> playNextStation()
                ACTION_PREV -> playPrevStation()
                ACTION_RELOAD -> reloadCurrentStream()
            }
            return com.google.common.util.concurrent.Futures.immediateFuture(
                androidx.media3.session.SessionResult(androidx.media3.session.SessionResult.RESULT_SUCCESS)
            )
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Odtwarzanie Radia",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Powiadomienie odtwarzacza w tle i integracji z autem"
                setShowBadge(false)
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    override fun onDestroy() {
        mediaSession?.run {
            player.release()
            release()
            mediaSession = null
        }
        super.onDestroy()
    }

    companion object {
        const val CHANNEL_ID = "radio_car_playback_channel"
        const val ACTION_PLAY_STATION = "com.seweryn.radiocar.ACTION_PLAY_STATION"
        const val ACTION_RELOAD_STREAM = "com.seweryn.radiocar.ACTION_RELOAD_STREAM"
        const val EXTRA_STATION_ID = "extra_station_id"

        const val ACTION_NEXT = "com.seweryn.radiocar.ACTION_NEXT"
        const val ACTION_PREV = "com.seweryn.radiocar.ACTION_PREV"
        const val ACTION_RELOAD = "com.seweryn.radiocar.ACTION_RELOAD"
    }
}
