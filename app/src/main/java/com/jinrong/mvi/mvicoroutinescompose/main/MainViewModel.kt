package com.jinrong.mvi.mvicoroutinescompose.main

import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.text.input.TextFieldValue
import androidx.navigation.NavHostController
import com.jinrong.mvi.mvicoroutinescompose.main.MainContract.Intent
import com.jinrong.mvi.mvicoroutinescompose.main.MainContract.State
import com.jinrong.mvi.mvicoroutinescompose.main.MainContract.ViewAction
import com.jinrong.mvi.mvicoroutinescompose.service.VGMdbService
import com.jinrong.mvi.mvicoroutinescompose.mvi.FlowViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.withContext

class MainViewModel(
    coroutineScope: CoroutineScope,
    searchTextState: androidx.compose.runtime.State<TextFieldValue>,
    private val navHostController: NavHostController
) : FlowViewModel<Intent, State, ViewAction>(
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
    private val vgmdbService = VGMdbService()

    val searchAlbums = states.distinctUntilChangedBy { it.searchAlbums }.mapNotNull { it.searchAlbums?.results?.albums }
    val album = states.distinctUntilChangedBy { it.album }.map { it.album }

    override fun Flow<Intent>.increaseAction(state: () -> State) = merge(
        mapLatestFlow<Intent.SearchAlbum> {
            val query = it.query
            if (query.isBlank()) {
                return@mapLatestFlow
            }
            val searchAlbums = runCatching {
                vgmdbService.searchAlbums(query)
            }.onFailure {
                emit(ViewAction.Toast("get searchAlbums failed with q: $query"))
                return@mapLatestFlow
            }.getOrThrow()
            setState(state().copy(searchAlbums = searchAlbums))
        },
        mapConcatFlow<Intent.ClickAlbum> {
            val albumScreen = MainContract.Screen.Album(it.album.link)
            invokeEvent { withContext(Dispatchers.Main) {
                navHostController.navigate(albumScreen.route)
            } }
        },
        mapConcatFlow<Intent.ShowAlbum> {
            val link = it.link
            val album = runCatching {
                vgmdbService.album(link)
            }.onFailure {
                emit(ViewAction.Toast("get album failed with link: $link"))
                return@mapConcatFlow
            }.getOrThrow()
            setState(state().copy(album = album))
        }
    )
}