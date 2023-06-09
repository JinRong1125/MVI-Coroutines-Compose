package com.jinrong.mvi.mvicoroutinescompose.mvi

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.DEFAULT_CONCURRENCY
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.flatMapConcat
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flatMapMerge
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.flow.stateIn
import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.reflect.KClass

abstract class FlowViewModel<Intent, State, ViewAction : FlowAction>
constructor(
    coroutineScope: CoroutineScope,
    initializeState: State,
    initializeIntents: List<Intent> = emptyList(),
    extraIntentFlows: List<Flow<Intent>> = emptyList(),
    coroutineContext: CoroutineContext = Dispatchers.Default,
    enableEvent: Boolean = true,
    enableView: Boolean = true
) {
    protected data class StateAction<T>(val state: T) : FlowAction
    protected data class EventAction(val function: suspend () -> Unit) : FlowAction

    private val stateClass = getClass<State>(1)
    private val viewClass = getClass<ViewAction>(2)

    private val intentFlow by lazy {
        MutableSharedFlow<Intent>(extraBufferCapacity = Int.MAX_VALUE, replay = Int.MAX_VALUE)
    }
    private val actionFlow by lazy {
        channelFlow {
            withContext(Dispatchers.Default) {
                initializeIntents.forEach {
                    send(it)
                }
            }
            (extraIntentFlows + listOf(intentFlow)).forEach {
                launch(start = CoroutineStart.UNDISPATCHED) {
                    it.collect {
                        send(it)
                    }
                }
            }
        }
            .increaseAction {
                states.value
            }
            .flowOn(coroutineContext)
            .shareIn(coroutineScope, SharingStarted.WhileSubscribed())
    }
    private val events by lazy {
        actionFlow
            .onEach {
                (it as? EventAction)?.function?.invoke()
            }
            .flowOn(coroutineContext)
            .launchIn(coroutineScope)
    }
    val views by lazy {
        actionFlow
            .mapCast(viewClass)
            .flowOn(coroutineContext)
            .shareIn(coroutineScope, SharingStarted.Eagerly)
    }
    protected val states: StateFlow<State> by lazy {
        actionFlow
            .mapNotNull {
                (it as? StateAction<*>)?.state
            }
            .mapCast(stateClass)
            .flowOn(coroutineContext)
            .stateIn(coroutineScope, SharingStarted.Eagerly, initializeState)
    }

    fun send(intent: Intent) {
        intentFlow.tryEmit(intent)
    }

    abstract fun Flow<Intent>.increaseAction(state: () -> State): Flow<FlowAction>

    protected inline fun <reified T : Intent> Flow<Intent>.mapConcatFlow(noinline block: suspend FlowCollector<FlowAction>.(T) -> Unit) =
        filterIsInstance<T>().flatMapConcat { flow { block(it) } }

    protected inline fun <reified T : Intent> Flow<Intent>.mapLatestFlow(noinline block: suspend FlowCollector<FlowAction>.(T) -> Unit) =
        filterIsInstance<T>().flatMapLatest { flow { block(it) } }

    protected inline fun <reified T : Intent> Flow<Intent>.mapMergeFlow(
        concurrency: Int = DEFAULT_CONCURRENCY,
        noinline block: suspend FlowCollector<FlowAction>.(T) -> Unit
    ) = filterIsInstance<T>().flatMapMerge(concurrency) { flow { block(it) } }

    protected suspend inline fun FlowCollector<FlowAction>.invokeEvent(noinline function: suspend () -> Unit) {
        emit(EventAction(function))
    }

    protected suspend inline fun FlowCollector<FlowAction>.setState(state: State) {
        emit(StateAction(state))
    }

    @Suppress("UNCHECKED_CAST")
    private fun <T> getClass(index: Int) =
        (this::class.supertypes.first().arguments[index].type?.classifier as? KClass<*>)?.javaObjectType as? Class<T>

    private fun <T, R> Flow<T>.mapCast(targetClass: Class<R>?) =
        mapNotNull { runCatching { targetClass?.cast(it) }.getOrNull() }

    init {
        states
        if (enableEvent) events
        if (enableView) views
    }
}