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

    private val prefs: SharedPreferences = context.getSharedPreferences("radio_car_prefs", Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true; prettyPrint = false }

    private val _stations = MutableStateFlow<List<Station>>(emptyList())
    val stations: StateFlow<List<Station>> = _stations.asStateFlow()

    init {
        loadStations()
    }

    private fun loadStations() {
        val savedJson = prefs.getString(KEY_STATIONS, null)
        if (savedJson != null) {
            try {
                val list = json.decodeFromString<List<Station>>(savedJson)
                if (list.isNotEmpty()) {
                    _stations.value = list
                    return
                }
            } catch (_: Exception) {
                // Fallback to defaults
            }
        }
        _stations.value = defaultStations()
        saveStations(_stations.value)
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
        return _stations.value.find { it.id == id }
    }

    fun getLastStationId(): Int {
        return prefs.getInt(KEY_LAST_STATION_ID, 1)
    }

    fun saveLastStationId(id: Int) {
        prefs.edit().putInt(KEY_LAST_STATION_ID, id).apply()
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

        fun defaultStations(): List<Station> = listOf(
            Station(
                id = 1,
                name = "Antyradio",
                streamUrl = "https://n-4-2.dcs.redcdn.pl/sc/o2/Eurozet/live/antyradio.livx?audio=5",
                logoUrl = "https://gfx.antyradio.pl/design/antyradio/src/images/favicon/favicon_180x180.png",
                icon = "📻"
            ),
            Station(
                id = 2,
                name = "Antyradio Classic Rock",
                streamUrl = "https://an01.cdn.eurozet.pl/ANTCLA.mp3?redirected=01",
                logoUrl = "https://gfx-player.antyradio.pl/design/player_antyradio/images/favicon/apple-touch-icon.png",
                icon = "📻"
            ),
            Station(
                id = 3,
                name = "Antyradio Greatest",
                streamUrl = "https://an02.cdn.eurozet.pl/ANTGRE.mp3",
                logoUrl = "https://gfx-player.antyradio.pl/design/player_antyradio/images/favicon/favicon-32x32.png",
                icon = "📻"
            ),
            Station(
                id = 4,
                name = "Radio 357",
                streamUrl = "https://n-11-21.dcs.redcdn.pl/sc/o2/radio357/live/radio357_pr.livx?preroll=0",
                logoUrl = "https://radio357.pl/wp-content/uploads/2022/04/c5399c0d-1b8e-43d5-b23b-e5cef1857856.png",
                icon = "📻"
            ),
            Station(
                id = 5,
                name = "RMF Rock",
                streamUrl = "http://217.74.72.11/rmf_rock",
                logoUrl = "http://www.rmfon.pl/assets/images/favicon/apple-touch-icon.png",
                icon = "🎸"
            ),
            Station(
                id = 6,
                name = "RMF Rock + FAKTY",
                streamUrl = "http://195.150.20.7/ROCKF",
                logoUrl = "http://www.rmfon.pl/assets/images/favicon/apple-touch-icon.png",
                icon = "📻"
            ),
            Station(
                id = 7,
                name = "Polskie Radio Czwórka",
                streamUrl = "https://stream14.polskieradio.pl/pr4/pr4.sdp/playlist.m3u8",
                logoUrl = "https://upload.wikimedia.org/wikipedia/commons/7/79/Czwórka_Polskie_Radio.jpg",
                icon = "📻"
            ),
            Station(
                id = 8,
                name = "Puste gniazdo 8",
                streamUrl = "",
                logoUrl = "",
                icon = "➕"
            ),
            Station(
                id = 9,
                name = "Puste gniazdo 9",
                streamUrl = "",
                logoUrl = "",
                icon = "➕"
            ),
            Station(
                id = 10,
                name = "Puste gniazdo 10",
                streamUrl = "",
                logoUrl = "",
                icon = "➕"
            )
        )
    }
}
