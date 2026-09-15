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
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
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
import androidx.media3.extractor.metadata.icy.IcyHeaders
import androidx.media3.extractor.metadata.icy.IcyInfo
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import androidx.media3.session.MediaStyleNotificationHelper
import coil.ImageLoader
import coil.request.ImageRequest
import com.seweryn.radiocar.MainActivity
import com.seweryn.radiocar.data.model.Station
import com.seweryn.radiocar.data.repository.StationRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArraySet

@UnstableApi
class RadioPlayerService : MediaSessionService() {

    private lateinit var player: ExoPlayer
    private lateinit var forwardingPlayer: CustomForwardingPlayer
    private var mediaSession: MediaSession? = null
    private lateinit var repository: StationRepository
    private var currentStation: Station? = null

    private var currentSongTitle: String = ""
    private var currentArtist: String = ""
    private var currentArtworkUrl: String? = null
    private var isConnecting: Boolean = false
    private var streamStationName: String? = null
    private var lastLoadedArtUrl: String? = null
    private var cachedArtBitmap: Bitmap? = null

    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var rdsJob: Job? = null

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

        forwardingPlayer = CustomForwardingPlayer(player)

        player.addListener(object : Player.Listener {
            override fun onMetadata(metadata: androidx.media3.common.Metadata) {
                for (i in 0 until metadata.length()) {
                    val entry = metadata.get(i)
                    if (entry is IcyInfo) {
                        handleIcyRawTitle(entry.title)
                    } else if (entry is IcyHeaders) {
                        val icyName = entry.name?.trim()
                        if (!icyName.isNullOrBlank() && icyName != streamStationName) {
                            streamStationName = icyName
                            forwardingPlayer.dispatchMetadataChanged()
                            updateNotification()
                        }
                    }
                }
            }

            override fun onMediaMetadataChanged(mediaMetadata: MediaMetadata) {
                val icyTitle = mediaMetadata.title?.toString()
                if (!icyTitle.isNullOrBlank()) {
                    handleIcyRawTitle(icyTitle)
                }
                val stationFromMeta = mediaMetadata.station?.toString()?.trim()
                if (!stationFromMeta.isNullOrBlank() && stationFromMeta != streamStationName) {
                    streamStationName = stationFromMeta
                    forwardingPlayer.dispatchMetadataChanged()
                    updateNotification()
                }
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                when (playbackState) {
                    Player.STATE_BUFFERING -> isConnecting = true
                    Player.STATE_READY -> isConnecting = false
                    Player.STATE_ENDED, Player.STATE_IDLE -> isConnecting = false
                }
                forwardingPlayer.dispatchMetadataChanged()
                updateNotification()
            }

            override fun onIsPlayingChanged(isPlaying: Boolean) {
                if (isPlaying) {
                    isConnecting = false
                }
                forwardingPlayer.dispatchMetadataChanged()
                updateNotification()
            }

            override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
                forwardingPlayer.dispatchMetadataChanged()
                updateNotification()
            }

            override fun onPlayerError(error: PlaybackException) {
                Log.e("RadioPlayerService", "Player error for station ${currentStation?.name}: ${error.message} (code: ${error.errorCode})", error)
                isConnecting = false
                forwardingPlayer.dispatchMetadataChanged()
                updateNotification()
            }
        })

        val openAppIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
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
        val action = intent?.action
        when (action) {
            ACTION_PLAY_STATION -> {
                val stationId = intent.getIntExtra(EXTRA_STATION_ID, -1)
                val stationName = intent.getStringExtra(EXTRA_STATION_NAME) ?: "Sewer's Mobile Radio"
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
            }
            ACTION_RELOAD_STREAM -> {
                reloadCurrentStream()
            }
            ACTION_TOGGLE_PLAY -> {
                if (player.isPlaying) {
                    player.pause()
                } else {
                    player.play()
                }
                updateNotification()
            }
            ACTION_PREV -> {
                playPrevStation()
            }
            ACTION_NEXT -> {
                playNextStation()
            }
            else -> {
                ensureForeground(currentStation?.name ?: "Sewer's Mobile Radio")
            }
        }
        return super.onStartCommand(intent, flags, startId)
    }

    private fun ensureForeground(stationName: String) {
        updateNotification()
    }

    private fun updateNotification() {
        val station = currentStation ?: repository.getStationById(repository.getLastStationId()) ?: repository.stations.value.firstOrNull { !it.isEmpty }
        val stationName = station?.name ?: "Sewer's Mobile Radio"
        val isPlaying = player.isPlaying
        val isBuffering = player.playbackState == Player.STATE_BUFFERING || isConnecting

        val title = if (currentSongTitle.isNotBlank()) {
            currentSongTitle
        } else {
            streamStationName ?: stationName
        }

        val text = when {
            currentArtist.isNotBlank() -> "${currentArtist} • $stationName"
            isBuffering -> "Łączenie... • $stationName"
            isPlaying -> "Odtwarzanie na żywo • $stationName"
            else -> "Wstrzymano • $stationName"
        }

        val openAppIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val contentPendingIntent = PendingIntent.getActivity(
            this, 0, openAppIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val prevIntent = Intent(this, RadioPlayerService::class.java).apply { action = ACTION_PREV }
        val prevPendingIntent = PendingIntent.getService(
            this, 1, prevIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val playPauseIntent = Intent(this, RadioPlayerService::class.java).apply { action = ACTION_TOGGLE_PLAY }
        val playPausePendingIntent = PendingIntent.getService(
            this, 2, playPauseIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val nextIntent = Intent(this, RadioPlayerService::class.java).apply { action = ACTION_NEXT }
        val nextPendingIntent = PendingIntent.getService(
            this, 3, nextIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentTitle(title)
            .setContentText(text)
            .setSubText(stationName)
            .setContentIntent(contentPendingIntent)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setSilent(true)
            .setOngoing(isPlaying)
            .addAction(android.R.drawable.ic_media_previous, "Poprzednia", prevPendingIntent)
            .addAction(
                if (isPlaying) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play,
                if (isPlaying) "Pauza" else "Odtwarzaj",
                playPausePendingIntent
            )
            .addAction(android.R.drawable.ic_media_next, "Następna", nextPendingIntent)

        mediaSession?.let { session ->
            builder.setStyle(
                MediaStyleNotificationHelper.MediaStyle(session)
                    .setShowActionsInCompactView(0, 1, 2)
            )
        }

        cachedArtBitmap?.let {
            builder.setLargeIcon(it)
        }

        val notification = builder.build()

        try {
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

        val artUrl = currentArtworkUrl ?: station?.logoUrl
        if (!artUrl.isNullOrBlank() && artUrl != lastLoadedArtUrl) {
            loadNotificationBitmap(artUrl, builder)
        }
    }

    private fun loadNotificationBitmap(url: String, builder: NotificationCompat.Builder) {
        val imageLoader = ImageLoader(this)
        val request = ImageRequest.Builder(this)
            .data(url)
            .allowHardware(false)
            .target { drawable ->
                val bitmap = (drawable as? BitmapDrawable)?.bitmap
                if (bitmap != null) {
                    lastLoadedArtUrl = url
                    cachedArtBitmap = bitmap
                    builder.setLargeIcon(bitmap)
                    val manager = getSystemService(NotificationManager::class.java)
                    manager?.notify(NOTIFICATION_ID, builder.build())
                }
            }
            .build()
        imageLoader.enqueue(request)
    }

    fun playStation(station: Station, autoPlay: Boolean = true) {
        if (station.streamUrl.isBlank()) return
        currentStation = station
        currentSongTitle = ""
        currentArtist = ""
        currentArtworkUrl = null
        streamStationName = null
        isConnecting = true
        repository.saveLastStationId(station.id)

        // Cancel previous RDS polling job if any
        rdsJob?.cancel()

        try {
            val initialMetadata = buildSessionMetadata()
            val effectiveUrl = resolveStreamUrl(station.streamUrl)

            val mediaItem = MediaItem.Builder()
                .setMediaId(station.id.toString())
                .setUri(effectiveUrl)
                .setMediaMetadata(initialMetadata)
                .build()

            player.setMediaItem(mediaItem)
            player.playlistMetadata = initialMetadata
            forwardingPlayer.dispatchMetadataChanged()
            updateNotification()
            player.prepare()
            if (autoPlay) {
                player.play()
            }

            // Start RDS fetching if station has an RDS endpoint (e.g. Antyradio, Radio ZET)
            val rdsUrl = getRdsUrlForStation(station)
            if (rdsUrl != null) {
                startRdsPolling(station, rdsUrl)
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
     * - Wykonawca (👤 Ikona człowieka w BMW):
     *     * Gdy nie gra (zapauzowane / stopped): "Paused" / "Stopped"
     *     * Gdy łączy się ze strumieniem: "Connecting"
     *     * Gdy połączone i gra: "Playing" dopóki nie ma wykonawcy, a potem nazwa wykonawcy
     * - Album (💿 Ikona płyty w BMW):
     *     * Podczas łączenia: nazwa stacji zapisana na slocie
     *     * Po połączeniu: nazwa stacji pobrana ze strumienia (ICY/RDS) lub ze slotu
     * - Utwór (🎵 Ikona nutki w BMW):
     *     * Podczas łączenia / brak utworu: Nazwa stacji
     *     * Po pobraniu utworu: Tytuł utworu
     */
    private fun buildSessionMetadata(): MediaMetadata {
        val station = currentStation
        val slotStationName = station?.name ?: "Sewer's Mobile Radio"
        val song = currentSongTitle.trim()
        val artist = currentArtist.trim()

        val artistForCar = when {
            isConnecting -> "Connecting"
            player.isPlaying -> {
                if (artist.isNotBlank()) artist else "Playing"
            }
            player.playbackState == Player.STATE_READY && !player.playWhenReady -> "Paused"
            player.playbackState == Player.STATE_IDLE || player.playbackState == Player.STATE_ENDED -> "Stopped"
            else -> if (artist.isNotBlank()) artist else "Paused"
        }

        val albumForCar = if (isConnecting) {
            slotStationName
        } else {
            streamStationName?.ifBlank { slotStationName } ?: slotStationName
        }

        val titleForCar = if (song.isNotBlank()) {
            song
        } else {
            slotStationName
        }

        val artworkUri = when {
            !currentArtworkUrl.isNullOrBlank() -> Uri.parse(currentArtworkUrl)
            station != null && station.logoUrl.isNotBlank() -> Uri.parse(station.logoUrl)
            else -> null
        }

        return MediaMetadata.Builder()
            .setArtist(artistForCar)
            .setAlbumTitle(albumForCar)
            .setTitle(titleForCar)
            .setDisplayTitle(if (song.isNotBlank()) song else albumForCar)
            .setSubtitle(if (artist.isNotBlank()) artist else artistForCar)
            .setArtworkUri(artworkUri)
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
            cleaned.equals("Radio ZET", ignoreCase = true) ||
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

        forwardingPlayer.dispatchMetadataChanged()
        updateNotification()
    }

    private fun resolveStreamUrl(rawUrl: String): String {
        if (rawUrl.contains("cdn.eurozet.pl")) {
            var updated = rawUrl
                .replace("an01.cdn.eurozet.pl", "an.cdn.eurozet.pl")
                .replace("an02.cdn.eurozet.pl", "an.cdn.eurozet.pl")
                .replace("an03.cdn.eurozet.pl", "an.cdn.eurozet.pl")
                .replace("an04.cdn.eurozet.pl", "an.cdn.eurozet.pl")
                .replace("an05.cdn.eurozet.pl", "an.cdn.eurozet.pl")
                .replace("an06.cdn.eurozet.pl", "an.cdn.eurozet.pl")
                .replace("https://an.cdn.eurozet.pl", "http://an.cdn.eurozet.pl")
            if (updated.contains("?redirected=")) {
                updated = updated.substringBefore("?redirected=")
            }
            return updated
        }
        return rawUrl
    }

    private fun getRdsUrlForStation(station: Station): String? {
        val name = station.name.trim()
        val url = station.streamUrl.lowercase()

        // Main Antyradio (FM / Web stream)
        if (url.contains("ant-web.mp3") || url.contains("ant-kat.mp3") ||
            (name.equals("Antyradio", ignoreCase = true) && !url.contains("antcla") && !url.contains("antgre") && !url.contains("antunp"))
        ) {
            return "https://rds.eurozet.pl/reader/var/antyradio.json"
        }

        // Radio ZET
        if (url.contains("zet-net.mp3") || name.equals("Radio ZET", ignoreCase = true)) {
            return "https://rds.eurozet.pl/reader/var/radiozet.json"
        }

        // Chillizet / Meloradio
        if (url.contains("chilli") || name.contains("Chillizet", ignoreCase = true)) {
            return "https://rds.eurozet.pl/reader/var/zetchilli.json"
        }
        if (url.contains("melo") || name.contains("Meloradio", ignoreCase = true)) {
            return "https://rds.eurozet.pl/reader/var/zetgold.json"
        }

        return null
    }

    private fun startRdsPolling(station: Station, rdsUrl: String) {
        rdsJob?.cancel()
        rdsJob = serviceScope.launch {
            // Immediate fetch upon station tune-in
            fetchRdsMetadata(station, rdsUrl)
            while (isActive) {
                delay(15_000)
                fetchRdsMetadata(station, rdsUrl)
            }
        }
    }

    private suspend fun fetchRdsMetadata(station: Station, rdsUrl: String) = withContext(Dispatchers.IO) {
        try {
            val conn = (URL(rdsUrl).openConnection() as HttpURLConnection).apply {
                connectTimeout = 5000
                readTimeout = 5000
                setRequestProperty("User-Agent", "RadioCar/1.0 (Linux; Android)")
            }
            if (conn.responseCode == 200) {
                val raw = conn.inputStream.bufferedReader().use { it.readText() }
                val firstBrace = raw.indexOf('{')
                val lastBrace = raw.lastIndexOf('}')
                if (firstBrace != -1 && lastBrace > firstBrace) {
                    val jsonStr = raw.substring(firstBrace, lastBrace + 1)
                    val root = JSONObject(jsonStr)
                    val now = root.optJSONObject("now")
                    val artist = now?.optString("artist")?.trim()
                        ?.trimStart('?', '\uFEFF', ' ', '-', '–', '\u0000')?.trim() ?: ""
                    val title = now?.optString("title")?.trim()
                        ?.trimStart('?', '\uFEFF', ' ', '-', '–', '\u0000')?.trim() ?: ""
                    val img = now?.optString("img")?.trim() ?: ""

                    withContext(Dispatchers.Main) {
                        if (currentStation?.id == station.id) {
                            applyRdsMetadata(artist, title, img)
                        }
                    }
                }
            }
            conn.disconnect()
        } catch (e: Exception) {
            Log.w("RadioPlayerService", "Error fetching RDS for ${station.name}: ${e.message}")
        }
    }

    private fun applyRdsMetadata(artist: String, title: String, img: String) {
        val station = currentStation ?: return

        // If title equals station name or generic live, reset
        val isLiveOrBlank = title.isBlank() ||
                title.equals(station.name, ignoreCase = true) ||
                title.equals("Antyradio", ignoreCase = true) ||
                title.equals("Radio ZET", ignoreCase = true) ||
                title.equals("Live", ignoreCase = true)

        if (isLiveOrBlank) {
            if (currentSongTitle.isNotBlank() || currentArtist.isNotBlank()) {
                currentSongTitle = ""
                currentArtist = ""
                currentArtworkUrl = null
                forwardingPlayer.dispatchMetadataChanged()
                updateNotification()
            }
            return
        }

        if (artist == currentArtist && title == currentSongTitle) {
            return
        }

        currentArtist = artist
        currentSongTitle = title
        if (img.isNotBlank() && img.startsWith("http")) {
            currentArtworkUrl = img
        }

        forwardingPlayer.dispatchMetadataChanged()
        updateNotification()
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

    /**
     * Custom ForwardingPlayer that intercepts listeners to guarantee that Bluetooth AVRCP (Car Audio)
     * and external MediaControllers receive immediate, cleanly formatted metadata updates whenever
     * track info changes, without getting stuck on "Live".
     */
    private inner class CustomForwardingPlayer(player: Player) : ForwardingPlayer(player) {
        private val sessionListeners = CopyOnWriteArraySet<Player.Listener>()
        private val wrappedListeners = ConcurrentHashMap<Player.Listener, Player.Listener>()

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

        override fun addListener(listener: Player.Listener) {
            sessionListeners.add(listener)
            val wrapped = object : Player.Listener {
                override fun onMediaMetadataChanged(mediaMetadata: MediaMetadata) {
                    listener.onMediaMetadataChanged(buildSessionMetadata())
                }

                override fun onPlaylistMetadataChanged(mediaMetadata: MediaMetadata) {
                    listener.onPlaylistMetadataChanged(buildSessionMetadata())
                }

                override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                    val updated = mediaItem?.buildUpon()?.setMediaMetadata(buildSessionMetadata())?.build()
                    listener.onMediaItemTransition(updated, reason)
                }

                override fun onEvents(p: Player, events: Player.Events) = listener.onEvents(p, events)
                override fun onTimelineChanged(t: androidx.media3.common.Timeline, r: Int) = listener.onTimelineChanged(t, r)
                override fun onTracksChanged(t: androidx.media3.common.Tracks) = listener.onTracksChanged(t)
                override fun onIsLoadingChanged(i: Boolean) = listener.onIsLoadingChanged(i)
                override fun onAvailableCommandsChanged(c: Player.Commands) = listener.onAvailableCommandsChanged(c)
                override fun onPlaybackStateChanged(s: Int) = listener.onPlaybackStateChanged(s)
                override fun onPlayWhenReadyChanged(p: Boolean, r: Int) = listener.onPlayWhenReadyChanged(p, r)
                override fun onPlaybackSuppressionReasonChanged(r: Int) = listener.onPlaybackSuppressionReasonChanged(r)
                override fun onIsPlayingChanged(i: Boolean) = listener.onIsPlayingChanged(i)
                override fun onRepeatModeChanged(r: Int) = listener.onRepeatModeChanged(r)
                override fun onShuffleModeEnabledChanged(s: Boolean) = listener.onShuffleModeEnabledChanged(s)
                override fun onPlayerError(e: PlaybackException) = listener.onPlayerError(e)
                override fun onPositionDiscontinuity(o: Player.PositionInfo, n: Player.PositionInfo, r: Int) = listener.onPositionDiscontinuity(o, n, r)
                override fun onPlaybackParametersChanged(p: androidx.media3.common.PlaybackParameters) = listener.onPlaybackParametersChanged(p)
                override fun onSeekBackIncrementChanged(s: Long) = listener.onSeekBackIncrementChanged(s)
                override fun onSeekForwardIncrementChanged(s: Long) = listener.onSeekForwardIncrementChanged(s)
                override fun onMaxSeekToPreviousPositionChanged(m: Long) = listener.onMaxSeekToPreviousPositionChanged(m)
                override fun onAudioSessionIdChanged(a: Int) = listener.onAudioSessionIdChanged(a)
                override fun onAudioAttributesChanged(a: AudioAttributes) = listener.onAudioAttributesChanged(a)
                override fun onVolumeChanged(v: Float) = listener.onVolumeChanged(v)
                override fun onDeviceInfoChanged(d: androidx.media3.common.DeviceInfo) = listener.onDeviceInfoChanged(d)
                override fun onDeviceVolumeChanged(v: Int, m: Boolean) = listener.onDeviceVolumeChanged(v, m)
                override fun onVideoSizeChanged(v: androidx.media3.common.VideoSize) = listener.onVideoSizeChanged(v)
                override fun onSurfaceSizeChanged(w: Int, h: Int) = listener.onSurfaceSizeChanged(w, h)
                override fun onRenderedFirstFrame() = listener.onRenderedFirstFrame()
                override fun onCues(c: androidx.media3.common.text.CueGroup) = listener.onCues(c)
                override fun onMetadata(m: androidx.media3.common.Metadata) = listener.onMetadata(m)
            }
            wrappedListeners[listener] = wrapped
            super.addListener(wrapped)
        }

        override fun removeListener(listener: Player.Listener) {
            sessionListeners.remove(listener)
            val wrapped = wrappedListeners.remove(listener) ?: listener
            super.removeListener(wrapped)
        }

        override fun getMediaMetadata(): MediaMetadata = buildSessionMetadata()

        override fun getPlaylistMetadata(): MediaMetadata = buildSessionMetadata()

        override fun getCurrentMediaItem(): MediaItem? {
            return super.getCurrentMediaItem()?.buildUpon()
                ?.setMediaMetadata(buildSessionMetadata())
                ?.build()
        }

        fun dispatchMetadataChanged() {
            val meta = buildSessionMetadata()
            player.playlistMetadata = meta
            for (listener in sessionListeners) {
                try {
                    listener.onMediaMetadataChanged(meta)
                    listener.onPlaylistMetadataChanged(meta)
                } catch (e: Exception) {
                    Log.w("RadioPlayerService", "Error dispatching metadata: ${e.message}")
                }
            }
        }
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
        rdsJob?.cancel()
        serviceScope.cancel()
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
        const val ACTION_TOGGLE_PLAY = "com.seweryn.radiocar.ACTION_TOGGLE_PLAY"
    }
}
