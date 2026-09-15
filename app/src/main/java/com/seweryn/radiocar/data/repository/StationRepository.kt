package com.seweryn.radiocar.data.repository

import android.content.Context
import android.content.SharedPreferences
import com.seweryn.radiocar.data.model.Station
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class StationRepository(context: Context) {

    private val appContext = context.applicationContext
    private val prefs: SharedPreferences = appContext.getSharedPreferences("radio_car_prefs", Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true; prettyPrint = false }

    private val _stations = MutableStateFlow<List<Station>>(emptyList())
    val stations: StateFlow<List<Station>> = _stations.asStateFlow()

    private val prefsListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key == KEY_STATIONS) {
            loadStations()
        }
    }

    init {
        loadStations()
        prefs.registerOnSharedPreferenceChangeListener(prefsListener)
    }

    fun loadStations() {
        val savedJson = prefs.getString(KEY_STATIONS, null)
        if (savedJson != null) {
            try {
                val list = json.decodeFromString<List<Station>>(savedJson)
                if (list.isNotEmpty()) {
                    val migratedList = list.map { station ->
                        migrateStationUrl(station)
                    }
                    _stations.value = migratedList
                    if (migratedList != list) {
                        saveStations(migratedList)
                    }
                    return
                }
            } catch (_: Exception) {
                // Fallback to defaults
            }
        }
        _stations.value = defaultStations()
        saveStations(_stations.value)
    }

    private fun migrateStationUrl(station: Station): Station {
        val url = station.streamUrl
        if (url.contains("cdn.eurozet.pl")) {
            var updatedUrl = url
                .replace("an01.cdn.eurozet.pl", "an.cdn.eurozet.pl")
                .replace("an02.cdn.eurozet.pl", "an.cdn.eurozet.pl")
                .replace("an03.cdn.eurozet.pl", "an.cdn.eurozet.pl")
                .replace("an04.cdn.eurozet.pl", "an.cdn.eurozet.pl")
                .replace("an05.cdn.eurozet.pl", "an.cdn.eurozet.pl")
                .replace("an06.cdn.eurozet.pl", "an.cdn.eurozet.pl")
                .replace("https://an.cdn.eurozet.pl", "http://an.cdn.eurozet.pl")
            if (updatedUrl.contains("?redirected=")) {
                updatedUrl = updatedUrl.substringBefore("?redirected=")
            }
            if (updatedUrl != url) {
                return station.copy(streamUrl = updatedUrl)
            }
        }
        return station
    }

    fun updateStation(updatedStation: Station) {
        val currentList = _stations.value.toMutableList()
        val index = currentList.indexOfFirst { it.id == updatedStation.id }
        if (index != -1) {
            currentList[index] = updatedStation
        } else {
            currentList.add(updatedStation)
        }
        val sortedList = currentList.sortedBy { it.id }
        _stations.value = sortedList
        saveStations(sortedList)
    }

    fun saveOrSwapStation(
        sourceSlotId: Int,
        targetSlotId: Int,
        name: String,
        streamUrl: String,
        logoUrl: String,
        icon: String
    ): Station {
        val currentList = _stations.value.toMutableList()
        val sourceIndex = currentList.indexOfFirst { it.id == sourceSlotId }
        val targetIndex = currentList.indexOfFirst { it.id == targetSlotId }

        val newStationData = Station(
            id = targetSlotId,
            name = name.ifBlank { if (streamUrl.isBlank()) "Puste gniazdo $targetSlotId" else "Stacja $targetSlotId" },
            streamUrl = streamUrl,
            logoUrl = logoUrl,
            icon = icon.ifBlank { if (streamUrl.isBlank()) "➕" else "📻" }
        )

        if (sourceSlotId != targetSlotId && sourceIndex != -1 && targetIndex != -1) {
            val existingTarget = currentList[targetIndex]
            val swappedSource = existingTarget.copy(
                id = sourceSlotId,
                name = if (existingTarget.isEmpty) "Puste gniazdo $sourceSlotId" else existingTarget.name
            )
            currentList[targetIndex] = newStationData
            currentList[sourceIndex] = swappedSource
        } else if (targetIndex != -1) {
            currentList[targetIndex] = newStationData
        }

        val sortedList = currentList.sortedBy { it.id }
        _stations.value = sortedList
        saveStations(sortedList)
        return newStationData
    }

    fun getStationById(id: Int): Station? {
        val found = _stations.value.find { it.id == id }
        if (found == null || found.streamUrl.isBlank()) {
            // Fresh reload from SharedPreferences if not found or empty
            loadStations()
            return _stations.value.find { it.id == id }
        }
        return found
    }

    fun getLastStationId(): Int {
        return prefs.getInt(KEY_LAST_STATION_ID, 1)
    }

    fun saveLastStationId(id: Int) {
        prefs.edit().putInt(KEY_LAST_STATION_ID, id).apply()
    }

    fun resetToDefaults(): List<Station> {
        val defaults = defaultStations()
        _stations.value = defaults
        saveStations(defaults)
        saveLastStationId(1)
        return defaults
    }

    private fun saveStations(list: List<Station>) {
        try {
            val encoded = json.encodeToString(list)
            prefs.edit().putString(KEY_STATIONS, encoded).apply()
        } catch (_: Exception) {}
    }

    companion object {
        private const val KEY_STATIONS = "saved_stations"
        private const val KEY_LAST_STATION_ID = "last_station_id"

        @Volatile
        private var INSTANCE: StationRepository? = null

        fun getInstance(context: Context): StationRepository {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: StationRepository(context.applicationContext).also { INSTANCE = it }
            }
        }

        fun defaultStations(): List<Station> = listOf(
            Station(
                id = 1,
                name = "Antyradio",
                streamUrl = "http://an.cdn.eurozet.pl/ant-web.mp3",
                logoUrl = "https://gfx.antyradio.pl/design/antyradio/src/images/favicon/favicon_180x180.png",
                icon = "📻"
            ),
            Station(
                id = 2,
                name = "Antyradio Classic Rock",
                streamUrl = "http://an.cdn.eurozet.pl/ANTCLA.mp3",
                logoUrl = "https://gfx-player.antyradio.pl/design/player_antyradio/images/favicon/apple-touch-icon.png",
                icon = "🎸"
            ),
            Station(
                id = 3,
                name = "Antyradio Greatest",
                streamUrl = "http://an.cdn.eurozet.pl/ANTGRE.mp3",
                logoUrl = "https://gfx-player.antyradio.pl/design/player_antyradio/images/favicon/favicon-32x32.png",
                icon = "⚡"
            ),
            Station(
                id = 4,
                name = "Antyradio Unplugged",
                streamUrl = "http://an.cdn.eurozet.pl/ANTUNP.mp3",
                logoUrl = "https://gfx-player.antyradio.pl/design/player_antyradio/images/favicon/apple-touch-icon.png",
                icon = "📻"
            ),
            Station(
                id = 5,
                name = "RMF Rock",
                streamUrl = "http://217.74.72.11/rmf_rock",
                logoUrl = "https://www.rmfon.pl/favicon.ico",
                icon = "🎸"
            ),
            Station(
                id = 6,
                name = "RMF Rock + FAKTY",
                streamUrl = "http://195.150.20.7/ROCKF",
                logoUrl = "https://www.rmfon.pl/favicon.ico",
                icon = "📰"
            ),
            Station(
                id = 7,
                name = "Radio 357",
                streamUrl = "https://stream.rcs.revma.com/ye5kghkgcm0uv",
                logoUrl = "https://radio357.pl/wp-content/uploads/2022/04/c5399c0d-1b8e-43d5-b23b-e5cef1857856.png",
                icon = "📻"
            ),
            Station(
                id = 8,
                name = "Hard Rock Radio FM",
                streamUrl = "http://67.249.184.45:8015/",
                logoUrl = "",
                icon = "🎸"
            ),
            Station(
                id = 9,
                name = "Radio ZET",
                streamUrl = "http://an.cdn.eurozet.pl/zet-net.mp3",
                logoUrl = "https://www.radiozet.pl/favicon.ico",
                icon = "🔴"
            ),
            Station(
                id = 10,
                name = "RMF Polska Alternatywa",
                streamUrl = "http://195.150.20.7/POLSKAALTERNATYWA",
                logoUrl = "https://www.rmf.fm/assets/images/favicon/apple-icon-120x120.png?3",
                icon = "📻"
            )
        )
    }
}
