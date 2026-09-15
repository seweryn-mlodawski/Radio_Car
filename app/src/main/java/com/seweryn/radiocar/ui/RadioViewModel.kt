package com.seweryn.radiocar.ui

import android.app.Application
import android.content.ComponentName
import android.content.Intent
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.seweryn.radiocar.data.model.Station
import com.seweryn.radiocar.data.repository.StationRepository
import com.seweryn.radiocar.service.RadioPlayerService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class RadioViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = StationRepository.getInstance(application)
    val stations: StateFlow<List<Station>> = repository.stations
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private var mediaController: MediaController? = null

    private val _currentStation = MutableStateFlow<Station?>(null)
    val currentStation: StateFlow<Station?> = _currentStation.asStateFlow()

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private val _isBuffering = MutableStateFlow(false)
    val isBuffering: StateFlow<Boolean> = _isBuffering.asStateFlow()

    private val _songTitle = MutableStateFlow("")
    val songTitle: StateFlow<String> = _songTitle.asStateFlow()

    private val _artistName = MutableStateFlow("")
    val artistName: StateFlow<String> = _artistName.asStateFlow()

    private val _editingStation = MutableStateFlow<Station?>(null)
    val editingStation: StateFlow<Station?> = _editingStation.asStateFlow()

    private val searchRepo = com.seweryn.radiocar.data.repository.RadioSearchRepository()

    private val _searchResults = MutableStateFlow<List<com.seweryn.radiocar.data.repository.SearchedStation>>(emptyList())
    val searchResults: StateFlow<List<com.seweryn.radiocar.data.repository.SearchedStation>> = _searchResults.asStateFlow()

    private val _isSearching = MutableStateFlow(false)
    val isSearching: StateFlow<Boolean> = _isSearching.asStateFlow()

    private val btTracker = com.seweryn.radiocar.util.BluetoothDeviceTracker(application)
    val connectedDeviceName: StateFlow<String?> = btTracker.connectedDeviceName

    init {
        btTracker.start()
        initMediaController()
    }

    fun refreshBluetoothDevice() {
        btTracker.updateConnectedDevice()
    }

    private fun initMediaController() {
        val context = getApplication<Application>()
        val sessionToken = SessionToken(
            context,
            ComponentName(context, RadioPlayerService::class.java)
        )
        val controllerFuture = MediaController.Builder(context, sessionToken).buildAsync()
        controllerFuture.addListener({
            try {
                val controller = controllerFuture.get()
                mediaController = controller
                setupPlayerListener(controller)
                syncInitialState(controller)
            } catch (_: Exception) {}
        }, ContextCompat.getMainExecutor(context))
    }

    private fun setupPlayerListener(controller: MediaController) {
        controller.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                _isPlaying.value = isPlaying
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                _isBuffering.value = playbackState == Player.STATE_BUFFERING
            }

            override fun onMediaMetadataChanged(mediaMetadata: MediaMetadata) {
                updateMetadata(mediaMetadata)
            }

            override fun onPlaylistMetadataChanged(mediaMetadata: MediaMetadata) {
                updateMetadata(mediaMetadata)
            }

            override fun onMediaItemTransition(mediaItem: androidx.media3.common.MediaItem?, reason: Int) {
                val mediaId = mediaItem?.mediaId?.toIntOrNull()
                if (mediaId != null) {
                    val st = repository.getStationById(mediaId)
                    _currentStation.value = st
                }
                mediaItem?.mediaMetadata?.let { updateMetadata(it) }
            }
        })
    }

    private fun syncInitialState(controller: MediaController) {
        _isPlaying.value = controller.isPlaying
        _isBuffering.value = controller.playbackState == Player.STATE_BUFFERING

        val mediaId = controller.currentMediaItem?.mediaId?.toIntOrNull()
            ?: repository.getLastStationId()
        val station = repository.getStationById(mediaId)
            ?: stations.value.firstOrNull { !it.isEmpty }
        _currentStation.value = station

        controller.playlistMetadata.let { updateMetadata(it) }
        controller.mediaMetadata.let { updateMetadata(it) }
    }

    private fun updateMetadata(mediaMetadata: MediaMetadata) {
        val rawTitle = mediaMetadata.title?.toString()?.trim() ?: ""
        val displayTitle = mediaMetadata.displayTitle?.toString()?.trim() ?: ""
        val subtitle = mediaMetadata.subtitle?.toString()?.trim() ?: ""
        val artist = mediaMetadata.artist?.toString()?.trim() ?: ""
        val album = mediaMetadata.albumTitle?.toString()?.trim() ?: ""

        var parsedArtist = if (subtitle.isNotBlank() && !subtitle.equals("Live", ignoreCase = true)) subtitle else artist
        var parsedSongTitle = if (displayTitle.isNotBlank() && !displayTitle.equals(album, ignoreCase = true)) displayTitle else rawTitle

        // If parsedSongTitle still contains " - " or " – ", separate artist and song title
        if (parsedSongTitle.contains(" - ")) {
            val parts = parsedSongTitle.split(" - ", limit = 2)
            if (parsedArtist.isBlank() || isDefaultArtistName(parsedArtist)) {
                parsedArtist = parts[0].trim()
            }
            parsedSongTitle = parts[1].trim()
        } else if (parsedSongTitle.contains(" – ")) {
            val parts = parsedSongTitle.split(" – ", limit = 2)
            if (parsedArtist.isBlank() || isDefaultArtistName(parsedArtist)) {
                parsedArtist = parts[0].trim()
            }
            parsedSongTitle = parts[1].trim()
        }

        // Filter out "Live" or station name from songTitle
        val currentStName = _currentStation.value?.name
        if (parsedSongTitle.isBlank() ||
            parsedSongTitle.equals("Live", ignoreCase = true) ||
            parsedSongTitle.equals(album, ignoreCase = true) ||
            (!currentStName.isNullOrBlank() && parsedSongTitle.equals(currentStName, ignoreCase = true))
        ) {
            _songTitle.value = ""
        } else {
            _songTitle.value = parsedSongTitle
        }

        // Filter out app name or station name from artistName
        if (parsedArtist.isBlank() ||
            isDefaultArtistName(parsedArtist) ||
            parsedArtist.equals(album, ignoreCase = true) ||
            (!currentStName.isNullOrBlank() && parsedArtist.equals(currentStName, ignoreCase = true)) ||
            parsedArtist.equals("Radio Car", ignoreCase = true)
        ) {
            _artistName.value = ""
        } else {
            _artistName.value = parsedArtist
        }
    }

    private fun isDefaultArtistName(name: String): Boolean {
        return name.equals("Sewer's Mobile Radio", ignoreCase = true) ||
                name.equals("Sewer Mobile Radio", ignoreCase = true) ||
                name.equals("Connecting", ignoreCase = true) ||
                name.equals("Playing", ignoreCase = true) ||
                name.equals("Paused", ignoreCase = true) ||
                name.equals("Stopped", ignoreCase = true)
    }

    fun playStation(station: Station) {
        if (station.isEmpty) return
        _currentStation.value = station
        _songTitle.value = ""
        _artistName.value = ""

        val context = getApplication<Application>()
        val intent = Intent(context, RadioPlayerService::class.java).apply {
            action = RadioPlayerService.ACTION_PLAY_STATION
            putExtra(RadioPlayerService.EXTRA_STATION_ID, station.id)
            putExtra(RadioPlayerService.EXTRA_STATION_NAME, station.name)
            putExtra(RadioPlayerService.EXTRA_STATION_URL, station.streamUrl)
            putExtra(RadioPlayerService.EXTRA_STATION_LOGO, station.logoUrl)
        }
        try {
            context.startService(intent)
        } catch (_: Exception) {
            ContextCompat.startForegroundService(context, intent)
        }
    }

    fun togglePlayPause() {
        val controller = mediaController ?: return
        if (controller.isPlaying) {
            controller.stop()
            _isPlaying.value = false
        } else {
            val station = _currentStation.value
            if (station != null && !station.isEmpty) {
                playStation(station)
            } else {
                controller.prepare()
                controller.play()
            }
        }
    }

    fun nextStation() {
        val list = stations.value.filter { !it.isEmpty }
        if (list.isEmpty()) return
        val currentId = _currentStation.value?.id
        val currentIndex = list.indexOfFirst { it.id == currentId }
        val nextIndex = if (currentIndex != -1) (currentIndex + 1) % list.size else 0
        playStation(list[nextIndex])
    }

    fun prevStation() {
        val list = stations.value.filter { !it.isEmpty }
        if (list.isEmpty()) return
        val currentId = _currentStation.value?.id
        val currentIndex = list.indexOfFirst { it.id == currentId }
        val prevIndex = if (currentIndex > 0) currentIndex - 1 else list.size - 1
        playStation(list[prevIndex])
    }

    fun reloadStream() {
        val context = getApplication<Application>()
        val intent = Intent(context, RadioPlayerService::class.java).apply {
            action = RadioPlayerService.ACTION_RELOAD_STREAM
        }
        ContextCompat.startForegroundService(context, intent)
    }

    fun openEditDialog(station: Station) {
        _editingStation.value = station
    }

    fun closeEditDialog() {
        _editingStation.value = null
    }

    fun saveStation(station: Station) {
        repository.updateStation(station)
        if (_currentStation.value?.id == station.id) {
            _currentStation.value = station
            if (_isPlaying.value) {
                playStation(station)
            }
        }
        closeEditDialog()
    }

    fun saveOrSwapStation(
        sourceSlotId: Int,
        targetSlotId: Int,
        name: String,
        streamUrl: String,
        logoUrl: String,
        icon: String
    ) {
        val updated = repository.saveOrSwapStation(
            sourceSlotId = sourceSlotId,
            targetSlotId = targetSlotId,
            name = name,
            streamUrl = streamUrl,
            logoUrl = logoUrl,
            icon = icon
        )
        val currentlyPlayingId = _currentStation.value?.id
        if (currentlyPlayingId == sourceSlotId || currentlyPlayingId == targetSlotId) {
            val newCurrent = repository.getStationById(targetSlotId)
            _currentStation.value = newCurrent
            if (_isPlaying.value && newCurrent != null && !newCurrent.isEmpty) {
                playStation(newCurrent)
            }
        }
        closeEditDialog()
    }

    fun searchStations(query: String) {
        if (query.isBlank()) {
            _searchResults.value = emptyList()
            return
        }
        viewModelScope.launch {
            _isSearching.value = true
            try {
                _searchResults.value = searchRepo.searchStations(query)
            } catch (_: Exception) {
                _searchResults.value = emptyList()
            } finally {
                _isSearching.value = false
            }
        }
    }

    fun clearSearchResults() {
        _searchResults.value = emptyList()
        _isSearching.value = false
    }

    fun clearStation(stationId: Int) {
        val emptyStation = Station(
            id = stationId,
            name = "Puste gniazdo $stationId",
            streamUrl = "",
            logoUrl = "",
            icon = "➕"
        )
        repository.updateStation(emptyStation)
        if (_currentStation.value?.id == stationId) {
            _currentStation.value = emptyStation
            mediaController?.stop()
            _isPlaying.value = false
        }
        closeEditDialog()
    }

    fun resetToDefaults() {
        val defaults = repository.resetToDefaults()
        val firstStation = defaults.firstOrNull { !it.isEmpty }
        if (firstStation != null) {
            playStation(firstStation)
        }
    }

    override fun onCleared() {
        super.onCleared()
        btTracker.stop()
        mediaController?.release()
        mediaController = null
    }
}
