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
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.flow.stateIn
import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

abstract class FlowViewModel<Intent, State, View>(
    coroutineScope: CoroutineScope,
    initializeState: State,
    protected val view: View,
    initializeIntents: List<Intent> = emptyList(),
    extraIntentFlows: List<Flow<Intent>> = emptyList(),
    coroutineContext: CoroutineContext = Dispatchers.Default,
    enableView: Boolean = true,
    enableEvent: Boolean = true
) {
    protected data class StateAction<State>(val state: State) : FlowAction
    protected data class EventAction(val function: suspend () -> Unit) : FlowAction
    data class ViewAction(val function: () -> Unit) : FlowAction

    private val intentFlow = MutableSharedFlow<Intent>(extraBufferCapacity = Int.MAX_VALUE, replay = Int.MAX_VALUE)
    private val actionFlow = channelFlow {
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
        .increaseAction()
        .flowOn(coroutineContext)
        .shareIn(coroutineScope, SharingStarted.WhileSubscribed())
    protected val states: StateFlow<State> =
        actionFlow
            .cast<StateAction<State>>()
            .flowOn(coroutineContext)
            .stateIn(coroutineScope, SharingStarted.Eagerly, initializeState)
    val views by lazy(LazyThreadSafetyMode.NONE) {
        actionFlow
            .mapNotNull { it as? ViewAction }
            .flowOn(coroutineContext)
            .shareIn(coroutineScope, SharingStarted.Eagerly)
    }
    private val events by lazy(LazyThreadSafetyMode.NONE) {
        actionFlow
            .onEach { (it as? EventAction)?.function?.invoke() }
            .flowOn(coroutineContext)
            .launchIn(coroutineScope)
    }

    fun send(intent: Intent) {
        intentFlow.tryEmit(intent)
    }

    abstract fun Flow<Intent>.increaseAction(state: () -> State = { states.value }): Flow<FlowAction>

    protected inline fun <reified T : Intent> Flow<Intent>.mapConcatFlow(noinline block: suspend FlowCollector<FlowAction>.(T) -> Unit) =
        filterIsInstance<T>().flatMapConcat { flow { block(it) } }

    protected inline fun <reified T : Intent> Flow<Intent>.mapLatestFlow(noinline block: suspend FlowCollector<FlowAction>.(T) -> Unit) =
        filterIsInstance<T>().flatMapLatest { flow { block(it) } }

    protected inline fun <reified T : Intent> Flow<Intent>.mapMergeFlow(
        concurrency: Int = DEFAULT_CONCURRENCY,
        noinline block: suspend FlowCollector<FlowAction>.(T) -> Unit
    ) = filterIsInstance<T>().flatMapMerge(concurrency) { flow { block(it) } }

    protected suspend inline fun FlowCollector<FlowAction>.setState(state: State) {
        emit(StateAction(state))
    }

    protected suspend inline fun FlowCollector<FlowAction>.invokeView(noinline function: View.() -> Unit) {
        emit(ViewAction { view.function() })
    }

    protected suspend inline fun FlowCollector<FlowAction>.invokeEvent(noinline function: suspend () -> Unit) {
        emit(EventAction(function))
    }

    private inline fun <reified T : StateAction<State>> Flow<FlowAction>.cast() =
        mapNotNull { (it as? T)?.state }

    init {
        states
        if (enableView) views
        if (enableEvent) events
    }
}