package com.jinrong.mvi.mvicoroutinescompose.main.entity

import kotlinx.serialization.Serializable

@Serializable
data class Album(
    val name: String,
    val covers: List<Cover>
) {
    @Serializable
    data class Cover(
        val thumb: String
    )
}