package com.jinrong.mvi.mvicoroutinescompose.mvi

import kotlinx.coroutines.CompletableJob
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.DEFAULT_CONCURRENCY
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.flatMapConcat
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flatMapMerge
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.transform
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent
import kotlin.coroutines.CoroutineContext
import kotlin.reflect.KClass

abstract class FlowViewModel<Intent: Any, State>(
    intentClass: KClass<Intent>,
    val coroutineScope: CoroutineScope,
    initializeState: State,
    initializeIntents: List<Intent> = emptyList(),
    coroutineContext: CoroutineContext = Dispatchers.Default
): KoinComponent {

    private val intentMap = mapOf(*intentClass.nestedClasses.map {
        it to MutableSharedFlow<Intent>(Int.MAX_VALUE, Int.MAX_VALUE)
    }.toTypedArray())

    protected val states = intentMap.keys.map {
        intentMap[it]?.mapState(it) ?: flow {}
    }
        .merge()
        .transform {
            emit(it.state).apply {
                it.job?.complete()
            }
        }
        .flowOn(coroutineContext)
        .stateIn(coroutineScope, SharingStarted.Eagerly, initializeState)

    init {
        coroutineScope.launch(Dispatchers.Unconfined, CoroutineStart.UNDISPATCHED) {
            initializeIntents.forEach {
                intentMap[it::class]?.emit(it)
            }
        }
    }

    abstract fun MutableSharedFlow<Intent>.mapState(
        intentClass: KClass<*>,
        state: () -> State = { states.value }
    ): Flow<StateAction<State>>

    suspend fun FlowCollector<StateAction<State>>.set(state: State) {
        emit(StateAction(state))
    }

    suspend fun FlowCollector<StateAction<State>>.sync(state: State) {
        with(Job()) {
            emit(StateAction(state, this))
            join()
        }
    }

    fun <I: Intent> intent(intent: I) {
        intentMap[intent::class]?.tryEmit(intent)
    }

    @Suppress("UNCHECKED_CAST")
    protected inline fun <reified I: Intent> Flow<*>.mapConcat(
        noinline block: suspend FlowCollector<StateAction<State>>.(I) -> Unit
    ) = (this as Flow<I>).flatMapConcat { flow { block(it) } }

    @Suppress("UNCHECKED_CAST")
    protected inline fun <reified I: Intent> Flow<*>.mapMerge(
        concurrency: Int = DEFAULT_CONCURRENCY,
        noinline block: suspend FlowCollector<StateAction<State>>.(I) -> Unit
    ) = (this as Flow<I>).flatMapMerge(concurrency) { flow { block(it) } }

    @Suppress("UNCHECKED_CAST")
    protected inline fun <reified I: Intent> Flow<*>.mapLatest(
        noinline block: suspend FlowCollector<StateAction<State>>.(I) -> Unit
    ) = (this as Flow<I>).flatMapLatest { flow { block(it) } }

    data class StateAction<State>(
        val state: State,
        val job: CompletableJob? = null
    )
}