package com.jinrong.mvi.mvicoroutinescompose.main

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import coil.compose.rememberAsyncImagePainter
import com.jinrong.mvi.mvicoroutinescompose.main.MainContract.Intent
import com.jinrong.mvi.mvicoroutinescompose.main.MainContract.Screen
import com.jinrong.mvi.mvicoroutinescompose.entity.Album
import com.jinrong.mvi.mvicoroutinescompose.entity.SearchAlbums
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.FlowCollector
import org.koin.core.context.loadKoinModules
import org.koin.dsl.module

class MainActivity : ComponentActivity() {

    private val searchText by lazy(LazyThreadSafetyMode.NONE) {
        mutableStateOf(TextFieldValue("kuuki"))
    }
    private val mainViewModel by lazy(LazyThreadSafetyMode.NONE) {
        MainViewModel(lifecycleScope, searchText)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val navController = rememberNavController()
            val navModule = module { single<NavHostController> { navController } }
            loadKoinModules(navModule)
            LaunchedEffect(mainViewModel.effects) {
                repeatOnCreated(mainViewModel.toastAction) {
                    Toast.makeText(this@MainActivity, it.message, Toast.LENGTH_SHORT).show()
                }
            }
            NavHost(
                navController = navController,
                startDestination = Screen.Search.route,
            ) {
                composable(
                    route = Screen.Search.route
                ) {
                    val searchAlbums = mainViewModel.searchAlbums
                        .collectAsStateWithLifecycle(
                            initialValue = emptyList(),
                            minActiveState = Lifecycle.State.CREATED
                        )
                    val searching = mainViewModel.searching
                        .collectAsStateWithLifecycle(
                            initialValue = false,
                            minActiveState = Lifecycle.State.CREATED
                        )
                    val clickAlbum: (SearchAlbums.Results.Album) -> Unit = {
                        mainViewModel.send(Intent.ClickAlbum(it))
                    }
                    SearchScreen(searchText, searchAlbums, searching, clickAlbum)
                }
                composable(
                    route = Screen.Album.ROUTE,
                    arguments = listOf(
                        navArgument(Screen.Album.LINK) {
                            type = NavType.StringType
                        }
                    )
                ) {
                    val album = mainViewModel.album
                        .collectAsStateWithLifecycle(
                            initialValue = null,
                            minActiveState = Lifecycle.State.CREATED
                        )
                    val showAlbum: () -> Unit = {
                        val link = it.arguments?.getString(Screen.Album.LINK) ?: ""
                        mainViewModel.send(Intent.ShowAlbum(link))
                    }
                    AlbumScreen(album, showAlbum)
                }
            }
        }
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    fun SearchScreen(
        searchText: MutableState<TextFieldValue>,
        searchAlbums: State<List<SearchAlbums.Results.Album>>,
        searching: State<Boolean>,
        onClickAlbum: (SearchAlbums.Results.Album) -> Unit
    ) {
        remember { searchText }
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            OutlinedTextField(
                value = searchText.value,
                onValueChange = { searchText.value = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(10.dp)
            )
            if (searching.value) {
                CircularProgressIndicator(
                    modifier = Modifier.padding(top = 5.dp)
                )
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxWidth(),
                    contentPadding = PaddingValues(5.dp)
                ) {
                    items(items = searchAlbums.value) {
                        Card(
                            shape = RoundedCornerShape(2.dp),
                            modifier = Modifier
                                .padding(10.dp)
                                .clickable(
                                    onClick = { onClickAlbum(it) }
                                ),
                            elevation = CardDefaults.cardElevation(
                                defaultElevation = 4.dp
                            )
                        ) {
                            Text(
                                text = it.titles.ja,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(10.dp)
                            )
                        }
                    }
                }
            }
        }
    }

    @Composable
    fun AlbumScreen(
        album: State<Album?>,
        onCreate: () -> Unit
    ) {
        val currentOnCreate by rememberUpdatedState(onCreate)
        val lifecycleOwner = LocalLifecycleOwner.current
        DisposableEffect(lifecycleOwner) {
            val lifecycleObserver = LifecycleEventObserver { _, event ->
                if (event == Lifecycle.Event.ON_CREATE) {
                    currentOnCreate()
                }
            }
            lifecycleOwner.lifecycle.addObserver(lifecycleObserver)
            onDispose {
                lifecycleOwner.lifecycle.removeObserver(lifecycleObserver)
            }
        }

        Card(
            shape = RoundedCornerShape(2.dp),
            modifier = Modifier.padding(10.dp),
            elevation = CardDefaults.cardElevation(
                defaultElevation = 4.dp
            ),
        ) {
            Row(
                Modifier.padding(10.dp),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                val model = album.value?.covers?.firstOrNull()?.thumb
                Image(
                    painter = rememberAsyncImagePainter(model),
                    contentDescription = null,
                    modifier = Modifier.weight(1F),
                    contentScale = ContentScale.FillWidth
                )

                val text = album.value?.name ?: ""
                Text(
                    text = text,
                    modifier = Modifier
                        .padding(start = 10.dp)
                        .weight(2F)
                )
            }
        }
    }

    private suspend inline fun <T> LifecycleOwner.repeatOnCreated(flow: Flow<T>, flowCollector: FlowCollector<T>) {
        repeatOnLifecycle(Lifecycle.State.CREATED) {
            flow.collect(flowCollector)
        }
    }
}

