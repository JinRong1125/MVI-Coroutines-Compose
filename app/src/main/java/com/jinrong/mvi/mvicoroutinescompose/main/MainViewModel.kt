package com.jinrong.mvi.mvicoroutinescompose.main

import androidx.navigation.NavHostController
import com.jinrong.mvi.mvicoroutinescompose.main.MainContract.Action
import com.jinrong.mvi.mvicoroutinescompose.main.MainContract.Intent
import com.jinrong.mvi.mvicoroutinescompose.main.MainContract.State
import com.jinrong.mvi.mvicoroutinescompose.main.service.VGMdbService
import com.jinrong.mvi.mvicoroutinescompose.mvi.FlowViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.flatMapConcat
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.withContext

class MainViewModel(
    coroutineScope: CoroutineScope,
    private val navHostController: NavHostController
) : FlowViewModel<Intent, State, Action, Action.Event, Action.View, Action.State>(
    coroutineScope = coroutineScope,
    initializeState = State.initialize(),
    initializeIntents = listOf(Intent.SearchAlbum("sakura no toki"))
) {
    private val vgmdbService = VGMdbService()

    val searchAlbums = states.distinctUntilChangedBy { it.searchAlbums }.mapNotNull { it.searchAlbums?.results?.albums }
    val album = states.distinctUntilChangedBy { it.album }.map { it.album }

    override fun Flow<Intent>.increaseAction(state: () -> State) =
        merge(
            filterIsInstance<Intent.SearchAlbum>()
                .flatMapConcat {
                    flow {
                        val query = it.query
                        val searchAlbums = runCatching {
                            vgmdbService.searchAlbums(query)
                        }.onFailure {
                            emit(Action.View.Toast("get searchAlbums failed with q: $query"))
                            return@flow
                        }.getOrThrow()
                        emit(Action.State.SetSearchAlbums(searchAlbums))
                    }
                },
            filterIsInstance<Intent.ClickAlbum>()
                .flatMapConcat {
                    flow {
                        val albumScreen = MainContract.Screen.Album(it.album.link)
                        emit(Action.Event.Navigate(albumScreen))
                    }
                },
            filterIsInstance<Intent.ShowAlbum>()
                .flatMapConcat {
                    flow {
                        val link = it.link
                        val album = runCatching {
                            vgmdbService.album(link)
                        }.onFailure {
                            emit(Action.View.Toast("get album failed with link: $link"))
                            return@flow
                        }.getOrThrow()
                        emit(Action.State.SetAlbum(album))
                    }
                }
        )

    override suspend fun Action.Event.reduceEvent() {
        when (this) {
            is Action.Event.Navigate -> withContext(Dispatchers.Main) {
                navHostController.navigate(screen.route)
            }
        }
    }

    override fun Action.State.reduceState(state: State) =
        when (this) {
            is Action.State.SetSearchAlbums -> state.copy(searchAlbums = searchAlbums)
            is Action.State.SetAlbum -> state.copy(album = album)
        }
}