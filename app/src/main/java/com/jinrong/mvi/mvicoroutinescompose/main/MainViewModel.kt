package com.jinrong.mvi.mvicoroutinescompose.main

import androidx.navigation.NavHostController
import com.jinrong.mvi.mvicoroutinescompose.main.MainContract.Intent
import com.jinrong.mvi.mvicoroutinescompose.main.MainContract.State
import com.jinrong.mvi.mvicoroutinescompose.mvi.FlowViewModel
import com.jinrong.mvi.mvicoroutinescompose.service.VGMdbService
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.launch
import org.koin.core.component.inject
import kotlin.reflect.KClass

class MainViewModel(
    coroutineScope: CoroutineScope
) : FlowViewModel<Intent, State>(
    intentClass = Intent::class,
    coroutineScope = coroutineScope,
    initializeState = State.initialize()
) {
    private val vgmdbService by inject<VGMdbService>()
    private val view by inject<MainContract.View>()
    private val navHostController by inject<NavHostController>()

    val searchAlbums = states
        .distinctUntilChangedBy { it.searchAlbums }
        .mapNotNull { it.searchAlbums?.results?.albums }
        .flowOn(Dispatchers.IO)
    val album = states
        .distinctUntilChangedBy { it.album }
        .map { it.album }
        .flowOn(Dispatchers.IO)
    val searching = states
        .distinctUntilChangedBy { it.searching }
        .map { it.searching }
        .flowOn(Dispatchers.IO)

    override fun MutableSharedFlow<Intent>.mapState(
        intentClass: KClass<*>,
        state: () -> State
    ) = when (intentClass) {
        Intent.SearchAlbum::class -> mapLatest<Intent.SearchAlbum> {
            val query = it.query
            if (query.isEmpty()) {
                return@mapLatest
            }
            set(state().copy(searchAlbums = null, searching = true))
            val searchAlbums = runCatching {
                vgmdbService.searchAlbums(query)
            }.onFailure { throwable ->
                set(state().copy(searching = false))

                if (throwable !is CancellationException) {
                    coroutineScope.launch {
                        view.showToast("get searchAlbums failed by q: $query")
                    }
                    return@mapLatest
                }
            }.getOrThrow()
            if (searchAlbums.results.albums.isEmpty()) {
                coroutineScope.launch {
                    view.showToast("no albums found by q: $query")
                }
            }
            set(state().copy(searchAlbums = searchAlbums, searching = false))
        }
        Intent.ClickAlbum::class -> mapConcat<Intent.ClickAlbum> {
            val albumScreen = MainContract.Screen.Album(it.album.link)
            coroutineScope.launch {
                navHostController.navigate(albumScreen.route)
            }
        }
        Intent.ShowAlbum::class -> mapConcat<Intent.ShowAlbum> {
            val link = it.link
            val album = runCatching {
                vgmdbService.album(link)
            }.onFailure {
                coroutineScope.launch {
                    view.showToast("get album failed by link: $link")
                }
                return@mapConcat
            }.getOrThrow()
            set((state().copy(album = album)))
        }
        else -> flow {}
    }
}