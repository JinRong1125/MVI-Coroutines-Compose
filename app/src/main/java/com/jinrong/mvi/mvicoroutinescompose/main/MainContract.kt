package com.jinrong.mvi.mvicoroutinescompose.main

import com.jinrong.mvi.mvicoroutinescompose.entity.Album
import com.jinrong.mvi.mvicoroutinescompose.entity.SearchAlbums
import com.jinrong.mvi.mvicoroutinescompose.mvi.FlowAction

class MainContract {
    sealed class Intent {
        data class SearchAlbum(val query: String) : Intent()
        data class ClickAlbum(val album: SearchAlbums.Results.Album) : Intent()
        data class ShowAlbum(val link: String) : Intent()
    }

    data class State(
        val searchAlbums: SearchAlbums?,
        val album: Album?
    ) {
        companion object {
            fun initialize() = State(
                searchAlbums = null,
                album = null
            )
        }
    }

    sealed class ViewAction : FlowAction {
        data class Toast(val text: String) : ViewAction()
    }

    sealed class Screen(val route: String) {
        object Search : Screen("search")
        data class Album(val link: String) : Screen("album?link=$link") {
            companion object {
                const val LINK = "link"
                const val ROUTE = "album?link={link}"
            }
        }
    }
}