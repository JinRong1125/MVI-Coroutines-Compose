package com.jinrong.mvi.mvicoroutinescompose.main

import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.text.input.TextFieldValue
import androidx.navigation.NavHostController
import com.jinrong.mvi.mvicoroutinescompose.main.MainContract.Intent
import com.jinrong.mvi.mvicoroutinescompose.main.MainContract.State
import com.jinrong.mvi.mvicoroutinescompose.mvi.FlowViewModel
import com.jinrong.mvi.mvicoroutinescompose.service.VGMdbService
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.withContext
import org.koin.core.component.inject

class MainViewModel(
    coroutineScope: CoroutineScope,
    searchTextState: androidx.compose.runtime.State<TextFieldValue>,
) : FlowViewModel<Intent, State>(
    coroutineScope = coroutineScope,
    initializeState = State.initialize(),
    extraIntentFlows = listOf(
        snapshotFlow {
            searchTextState.value
        }
            .distinctUntilChangedBy { it.text }
            .map { Intent.SearchAlbum(it.text) }
    )
) {
    private val vgmdbService by inject<VGMdbService>()
    private val navHostController by inject<NavHostController>()

    val searchAlbums by lazy(LazyThreadSafetyMode.NONE) {
        states
            .distinctUntilChangedBy { it.searchAlbums }
            .mapNotNull { it.searchAlbums?.results?.albums }
            .flowOn(Dispatchers.IO)
    }
    val album by lazy(LazyThreadSafetyMode.NONE) {
        states
            .distinctUntilChangedBy { it.album }
            .map { it.album }
            .flowOn(Dispatchers.IO)
    }
    val searching by lazy(LazyThreadSafetyMode.NONE) {
        states
            .distinctUntilChangedBy { it.searching }
            .map { it.searching }
            .flowOn(Dispatchers.IO)
    }
    val toastAction by lazy(LazyThreadSafetyMode.NONE) {
        effects
            .filterIsInstance<MainContract.ToastAction>()
            .flowOn(Dispatchers.IO)
    }

    init {
        events
    }

    override fun Flow<Intent>.increaseAction(state: () -> State) = merge(
        mapLatestFlow<Intent.SearchAlbum> {
            emit(StateAction(state().copy(searchAlbums = null, searching = true)))
            val query = it.query
            val searchAlbums = runCatching {
                vgmdbService.searchAlbums(query)
            }.onFailure { throwable ->
                emit(StateAction(state().copy(searching = false)))
                if (throwable !is CancellationException) {
                    emit(MainContract.ToastAction("get searchAlbums failed by q: $query"))
                }
                return@mapLatestFlow
            }.getOrThrow()
            if (searchAlbums.results.albums.isEmpty()) {
                emit(MainContract.ToastAction("no albums found by q: $query"))
            }
            emit(StateAction(state().copy(searchAlbums = searchAlbums, searching = false)))
        },
        mapConcatFlow<Intent.ClickAlbum> {
            val albumScreen = MainContract.Screen.Album(it.album.link)
            emit(EventAction { withContext(Dispatchers.Main) {
                navHostController.navigate(albumScreen.route)
            }})
        },
        mapConcatFlow<Intent.ShowAlbum> {
            val link = it.link
            val album = runCatching {
                vgmdbService.album(link)
            }.onFailure {
                emit(MainContract.ToastAction("get album failed by link: $link"))
                return@mapConcatFlow
            }.getOrThrow()
            emit(StateAction(state().copy(album = album)))
        }
    )
}