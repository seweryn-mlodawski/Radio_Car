package com.seweryn.radiocar.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.Uri
import android.os.Build
import android.util.Log
import android.view.KeyEvent
import androidx.core.app.NotificationCompat
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.ForwardingPlayer
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.extractor.metadata.icy.IcyInfo
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.seweryn.radiocar.MainActivity
import com.seweryn.radiocar.data.model.Station
import com.seweryn.radiocar.data.repository.StationRepository

@UnstableApi
class RadioPlayerService : MediaSessionService() {

    private lateinit var player: ExoPlayer
    private lateinit var forwardingPlayer: ForwardingPlayer
    private var mediaSession: MediaSession? = null
    private lateinit var repository: StationRepository
    private var currentStation: Station? = null

    private var currentSongTitle: String = ""
    private var currentArtist: String = ""

    override fun onCreate() {
        super.onCreate()
        repository = StationRepository.getInstance(this)
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

        // Wrap ExoPlayer in ForwardingPlayer to expose SEEK_TO_NEXT/PREVIOUS to Bluetooth AVRCP
        forwardingPlayer = object : ForwardingPlayer(player) {
            override fun isCommandAvailable(command: @Player.Command Int): Boolean {
                return when (command) {
                    Player.COMMAND_SEEK_TO_NEXT,
                    Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM,
                    Player.COMMAND_SEEK_TO_PREVIOUS,
                    Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM,
                    Player.COMMAND_PLAY_PAUSE,
                    Player.COMMAND_STOP -> true
                    else -> super.isCommandAvailable(command)
                }
            }

            override fun getAvailableCommands(): Player.Commands {
                return super.getAvailableCommands().buildUpon()
                    .add(Player.COMMAND_SEEK_TO_NEXT)
                    .add(Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM)
                    .add(Player.COMMAND_SEEK_TO_PREVIOUS)
                    .add(Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM)
                    .build()
            }

            override fun seekToNext() {
                playNextStation()
            }

            override fun seekToNextMediaItem() {
                playNextStation()
            }

            override fun seekToPrevious() {
                playPrevStation()
            }

            override fun seekToPreviousMediaItem() {
                playPrevStation()
            }

            override fun getMediaMetadata(): MediaMetadata {
                return buildSessionMetadata()
            }
        }

        player.addListener(object : Player.Listener {
            override fun onMetadata(metadata: androidx.media3.common.Metadata) {
                for (i in 0 until metadata.length()) {
                    val entry = metadata.get(i)
                    if (entry is IcyInfo) {
                        handleIcyRawTitle(entry.title)
                    }
                }
            }

            override fun onMediaMetadataChanged(mediaMetadata: MediaMetadata) {
                val icyTitle = mediaMetadata.title?.toString()
                if (!icyTitle.isNullOrBlank()) {
                    handleIcyRawTitle(icyTitle)
                }
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

        mediaSession = MediaSession.Builder(this, forwardingPlayer)
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
        ensureForeground(currentStation?.name ?: "Sewer Mobile Radio")
        val action = intent?.action
        if (action == ACTION_PLAY_STATION) {
            val stationId = intent.getIntExtra(EXTRA_STATION_ID, -1)
            val stationName = intent.getStringExtra(EXTRA_STATION_NAME) ?: "Sewer Mobile Radio"
            val stationUrl = intent.getStringExtra(EXTRA_STATION_URL)
            val stationLogo = intent.getStringExtra(EXTRA_STATION_LOGO)

            if (!stationUrl.isNullOrBlank()) {
                val station = Station(
                    id = if (stationId != -1) stationId else 1,
                    name = stationName,
                    streamUrl = stationUrl,
                    logoUrl = stationLogo ?: "",
                    icon = "📻"
                )
                playStation(station, autoPlay = true)
            } else if (stationId != -1) {
                repository.getStationById(stationId)?.let {
                    playStation(it, autoPlay = true)
                }
            }
        } else if (action == ACTION_RELOAD_STREAM) {
            reloadCurrentStream()
        }
        return super.onStartCommand(intent, flags, startId)
    }

    private fun ensureForeground(stationName: String) {
        try {
            val notification = NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle(stationName.ifBlank { "Sewer Mobile Radio" })
                .setContentText("Odtwarzanie stacji...")
                .setSmallIcon(android.R.drawable.ic_media_play)
                .setOngoing(true)
                .setSilent(true)
                .build()

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(
                    NOTIFICATION_ID,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
                )
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
        } catch (e: Exception) {
            Log.e("RadioPlayerService", "Failed to startForeground: ${e.message}", e)
        }
    }

    fun playStation(station: Station, autoPlay: Boolean = true) {
        if (station.streamUrl.isBlank()) return
        currentStation = station
        currentSongTitle = ""
        currentArtist = ""
        repository.saveLastStationId(station.id)

        try {
            val initialMetadata = buildSessionMetadata()

            val mediaItem = MediaItem.Builder()
                .setMediaId(station.id.toString())
                .setUri(station.streamUrl)
                .setMediaMetadata(initialMetadata)
                .build()

            player.setMediaItem(mediaItem)
            player.playlistMetadata = initialMetadata
            player.prepare()
            if (autoPlay) {
                player.play()
            }
        } catch (e: Exception) {
            Log.e("RadioPlayerService", "Error playing station ${station.name}", e)
        }
    }

    fun reloadCurrentStream() {
        currentStation?.let {
            playStation(it, autoPlay = true)
        } ?: run {
            player.prepare()
            player.play()
        }
    }

    /**
     * Builds standard MediaMetadata formatted for Car displays (BMW iDrive AVRCP) and the phone UI.
     * - Artist (👤 Wykonawca): "Sewer Mobile Radio"
     * - AlbumTitle (💿 Płyta): Station name (e.g. "Antyradio Classic Rock")
     * - Title (🎵 Utwór): Song title or "Live"
     */
    private fun buildSessionMetadata(): MediaMetadata {
        val station = currentStation
        val stationName = station?.name ?: "Sewer Mobile Radio"
        val song = currentSongTitle.trim()
        val artist = currentArtist.trim()

        val titleForCar = when {
            song.isNotBlank() && artist.isNotBlank() -> "$artist - $song"
            song.isNotBlank() -> song
            else -> "Live"
        }

        return MediaMetadata.Builder()
            .setArtist("Sewer Mobile Radio") // 👤 Ikona człowieczka w BMW
            .setAlbumTitle(stationName)      // 💿 Ikona płyty w BMW
            .setTitle(titleForCar)           // 🎵 Ikona nutek w BMW
            .setDisplayTitle(if (song.isNotBlank()) song else stationName)
            .setSubtitle(if (artist.isNotBlank()) artist else "Live")
            .setArtworkUri(if (station != null && station.logoUrl.isNotBlank()) Uri.parse(station.logoUrl) else null)
            .build()
    }

    private fun handleIcyRawTitle(rawTitle: String?) {
        if (rawTitle.isNullOrBlank()) return
        val station = currentStation ?: return

        // Clean leading ?, \uFEFF, spaces, dashes
        val cleaned = rawTitle.trim()
            .trimStart('?', '\uFEFF', ' ', '-', '–', '\u0000')
            .trim()

        // If title equals station name or generic live, reset song title
        if (cleaned.isBlank() ||
            cleaned.equals(station.name, ignoreCase = true) ||
            cleaned.equals("Antyradio", ignoreCase = true) ||
            cleaned.equals("Live", ignoreCase = true)
        ) {
            currentSongTitle = ""
            currentArtist = ""
        } else {
            if (cleaned.contains(" - ")) {
                val parts = cleaned.split(" - ", limit = 2)
                currentArtist = parts[0].trim()
                currentSongTitle = parts[1].trim()
            } else if (cleaned.contains(" – ")) {
                val parts = cleaned.split(" – ", limit = 2)
                currentArtist = parts[0].trim()
                currentSongTitle = parts[1].trim()
            } else {
                currentSongTitle = cleaned
                currentArtist = ""
            }
        }

        val updatedMeta = buildSessionMetadata()
        player.playlistMetadata = updatedMeta
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
        ): com.google.common.util.concurrent.ListenableFuture<MediaSession.MediaItemsWithStartPosition> {
            // Auto resume playback on car Bluetooth connect
            currentStation?.let {
                playStation(it, autoPlay = true)
            }
            return super.onPlaybackResumption(mediaSession, controller)
        }

        // Support steering wheel previous/next buttons from Bluetooth AVRCP and headset keys
        override fun onMediaButtonEvent(
            session: MediaSession,
            controllerInfo: MediaSession.ControllerInfo,
            intent: Intent
        ): Boolean {
            val keyEvent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                intent.getParcelableExtra(Intent.EXTRA_KEY_EVENT, KeyEvent::class.java)
            } else {
                @Suppress("DEPRECATION")
                intent.getParcelableExtra(Intent.EXTRA_KEY_EVENT)
            }
            if (keyEvent != null && keyEvent.action == KeyEvent.ACTION_DOWN) {
                when (keyEvent.keyCode) {
                    KeyEvent.KEYCODE_MEDIA_NEXT,
                    KeyEvent.KEYCODE_MEDIA_FAST_FORWARD,
                    KeyEvent.KEYCODE_MEDIA_SKIP_FORWARD,
                    KeyEvent.KEYCODE_MEDIA_STEP_FORWARD -> {
                        playNextStation()
                        return true
                    }
                    KeyEvent.KEYCODE_MEDIA_PREVIOUS,
                    KeyEvent.KEYCODE_MEDIA_REWIND,
                    KeyEvent.KEYCODE_MEDIA_SKIP_BACKWARD,
                    KeyEvent.KEYCODE_MEDIA_STEP_BACKWARD -> {
                        playPrevStation()
                        return true
                    }
                }
            }
            return super.onMediaButtonEvent(session, controllerInfo, intent)
        }

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
        const val NOTIFICATION_ID = 1001
        const val CHANNEL_ID = "radio_car_playback_channel"
        const val ACTION_PLAY_STATION = "com.seweryn.radiocar.ACTION_PLAY_STATION"
        const val ACTION_RELOAD_STREAM = "com.seweryn.radiocar.ACTION_RELOAD_STREAM"
        const val EXTRA_STATION_ID = "extra_station_id"
        const val EXTRA_STATION_NAME = "extra_station_name"
        const val EXTRA_STATION_URL = "extra_station_url"
        const val EXTRA_STATION_LOGO = "extra_station_logo"

        const val ACTION_NEXT = "com.seweryn.radiocar.ACTION_NEXT"
        const val ACTION_PREV = "com.seweryn.radiocar.ACTION_PREV"
        const val ACTION_RELOAD = "com.seweryn.radiocar.ACTION_RELOAD"
    }
}
