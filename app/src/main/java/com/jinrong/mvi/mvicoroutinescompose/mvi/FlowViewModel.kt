package com.jinrong.mvi.mvicoroutinescompose.mvi

import kotlinx.coroutines.CompletableJob
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.DEFAULT_CONCURRENCY
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.flatMapConcat
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flatMapMerge
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.shareIn
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

    private val intentMap: Map<KClass<*>, MutableSharedFlow<Intent>> =
        mapOf(*intentClass.nestedClasses.map {
            it to MutableSharedFlow<Intent>(extraBufferCapacity = Int.MAX_VALUE)
        }.toTypedArray())
    private val intentFlows = intentMap.values

    private val actions by lazy(LazyThreadSafetyMode.NONE) {
        intentMap.keys.map {
            intentMap[it]?.mapAction(it) ?: flow {  }
        }
            .merge()
            .flowOn(coroutineContext)
            .shareIn(coroutineScope, SharingStarted.Eagerly)
    }
    protected val states = actions.filterIsInstance<StateAction<State>>()
        .transform {
            emit(it.state).apply {
                it.syncJob?.complete()
            }
        }
        .flowOn(coroutineContext)
        .stateIn(coroutineScope, SharingStarted.Eagerly, initializeState)
    private val events = actions.filterIsInstance<EventAction>()
        .onEach {
            coroutineScope.launch(it.coroutineContext) {
                it.execute()
            }
        }
        .flowOn(coroutineContext)
        .launchIn(coroutineScope)

    init {
        coroutineScope.launch(Dispatchers.Unconfined, CoroutineStart.UNDISPATCHED) {
            initializeIntents.forEach {
                intentMap[it::class]?.emit(it)
            }
        }
    }
    abstract fun MutableSharedFlow<Intent>.mapAction(
        intentClass: KClass<*>,
        state: () -> State = { states.value }
    ): Flow<FlowAction>

    fun <I: Intent> intent(intent: I) {
        intentMap[intent::class]?.tryEmit(intent)
    }

    @Suppress("UNCHECKED_CAST")
    protected inline fun <reified I: Intent> Flow<*>.mapConcat(
        noinline block: suspend FlowCollector<FlowAction>.(I) -> Unit
    ) = (this as Flow<I>).flatMapConcat { flow { block(it) } }

    @Suppress("UNCHECKED_CAST")
    protected inline fun <reified I: Intent> Flow<*>.mapMerge(
        concurrency: Int = DEFAULT_CONCURRENCY,
        noinline block: suspend FlowCollector<FlowAction>.(I) -> Unit
    ) = (this as Flow<I>).flatMapMerge(concurrency) { flow { block(it) } }

    @Suppress("UNCHECKED_CAST")
    protected inline fun <reified I: Intent> Flow<*>.mapLatest(
        noinline block: suspend FlowCollector<FlowAction>.(I) -> Unit
    ) = (this as Flow<I>).flatMapLatest { flow { block(it) } }

    protected data class StateAction<State>(
        val state: State,
        val syncJob: CompletableJob? = null
    ) : FlowAction

    protected interface EventAction: FlowAction {
        companion object {
            fun execute(
                coroutineContext: CoroutineContext = Dispatchers.Main,
                execution: suspend () -> Unit
            ) = object : EventAction {
                override val coroutineContext = coroutineContext
                override suspend fun execute() = execution()
            }
        }
        val coroutineContext: CoroutineContext
        suspend fun execute()
    }
}