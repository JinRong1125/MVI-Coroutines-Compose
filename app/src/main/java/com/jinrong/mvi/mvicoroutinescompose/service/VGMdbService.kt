package com.jinrong.mvi.mvicoroutinescompose.service

import com.jinrong.mvi.mvicoroutinescompose.entity.Album
import com.jinrong.mvi.mvicoroutinescompose.entity.SearchAlbums
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.parameter
import io.ktor.client.request.get
import io.ktor.http.URLProtocol
import io.ktor.http.path
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json

class VGMdbService {

    companion object {
        private const val HOST_VGMDB = "vgmdb.info"
        private const val PATH_SEARCH_ALBUMS = "search/albums"
    }

    private val httpClient by lazy {
        HttpClient(OkHttp) {
            install(ContentNegotiation) {
                json(
                    Json {
                        ignoreUnknownKeys = true
                    }
                )
            }
        }
    }

    suspend fun searchAlbums(query: String): SearchAlbums = withContext(Dispatchers.IO) {
        httpClient.get {
            url {
                protocol = URLProtocol.HTTPS
                host = HOST_VGMDB
                path(PATH_SEARCH_ALBUMS)
                parameter("q", query)
            }
        }.body()
    }

    suspend fun album(path: String): Album = withContext(Dispatchers.IO) {
        httpClient.get {
            url {
                protocol = URLProtocol.HTTPS
                host = HOST_VGMDB
                path(path)
            }
        }.body()
    }
}