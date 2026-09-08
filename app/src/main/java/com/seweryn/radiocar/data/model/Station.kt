package com.seweryn.radiocar.data.model

import kotlinx.serialization.Serializable

@Serializable
data class Station(
    val id: Int,
    val name: String,
    val streamUrl: String,
    val logoUrl: String = "",
    val icon: String = "📻"
) {
    val isEmpty: Boolean
        get() = streamUrl.isBlank()
}
