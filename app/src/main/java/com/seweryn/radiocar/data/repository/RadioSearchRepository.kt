package com.seweryn.radiocar.data.repository

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

@Serializable
data class SearchedStation(
    val name: String = "",
    val url: String = "",
    val url_resolved: String = "",
    val favicon: String = "",
    val tags: String = "",
    val country: String = "",
    val codec: String = "",
    val bitrate: Int = 0
) {
    val streamUrl: String
        get() = url_resolved.ifBlank { url }
}

class RadioSearchRepository {

    private val json = Json { ignoreUnknownKeys = true; coerceInputValues = true }

    suspend fun searchStations(query: String): List<SearchedStation> = withContext(Dispatchers.IO) {
        val trimmed = query.trim()
        if (trimmed.length < 2) return@withContext emptyList()

        val encoded = URLEncoder.encode(trimmed, "UTF-8")
        val urlsToTry = listOf(
            "https://de1.api.radio-browser.info/json/stations/search?name=$encoded&limit=25",
            "https://de1.api.radio-browser.info/json/stations/bytag/$encoded"
        )

        val accumulated = mutableListOf<SearchedStation>()

        for (endpoint in urlsToTry) {
            try {
                val url = URL(endpoint)
                val conn = (url.openConnection() as HttpURLConnection).apply {
                    connectTimeout = 7000
                    readTimeout = 7000
                    setRequestProperty("User-Agent", "RadioCarApp/1.0")
                }
                if (conn.responseCode == 200) {
                    val body = conn.inputStream.bufferedReader().use { it.readText() }
                    val list = json.decodeFromString<List<SearchedStation>>(body)
                    accumulated.addAll(list)
                }
                conn.disconnect()
            } catch (_: Exception) {}

            if (accumulated.size >= 20) break
        }

        accumulated
            .filter { it.streamUrl.isNotBlank() && it.name.isNotBlank() }
            .distinctBy { it.streamUrl }
            .take(25)
    }
}
