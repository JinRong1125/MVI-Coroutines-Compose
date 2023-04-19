package com.jinrong.mvi.mvicoroutinescompose.entity

import kotlinx.serialization.Serializable

@Serializable
data class SearchAlbums(
    val results: Results
) {
    @Serializable
    data class Results(
        val albums: List<Album>
    ) {
        @Serializable
        data class Album(
            val link: String,
            val titles: Titles
        ) {
            @Serializable
            data class Titles(
                val ja: String
            )
        }
    }
}