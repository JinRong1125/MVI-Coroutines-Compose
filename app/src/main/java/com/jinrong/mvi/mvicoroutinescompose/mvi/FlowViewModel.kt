package com.jinrong.mvi.mvicoroutinescompose.mvi

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.scan
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.flow.stateIn
import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.onStart
import kotlin.reflect.KClass

abstract class FlowViewModel<Intent, State, EventAction : FlowAction, ViewAction : FlowAction, StateAction : FlowAction>
constructor(
    coroutineScope: CoroutineScope,
    initializeState: State,
    initializeIntents: List<Intent> = emptyList(),
    extraIntentFlows: List<Flow<Intent>> = emptyList(),
    coroutineContext: CoroutineContext = Dispatchers.Default
) {
    private val eventClass = getClass<EventAction>(2)
    private val viewClass = getClass<ViewAction>(3)
    private val stateClass = getClass<StateAction>(4)

    private val intentFlow by lazy {
        MutableSharedFlow<Intent>(extraBufferCapacity = 64, replay = 64)
    }
    private val actionFlow by lazy {
        (listOf(intentFlow) + extraIntentFlows)
            .merge()
            .onStart {
                initializeIntents.forEach {
                    emit(it)
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
            .mapCast(eventClass)
            .onEach {
                it.reduceEvent()
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
            .mapCast(stateClass)
            .scan(initializeState) { state, action ->
                action.reduceState(state)
            }
            .flowOn(coroutineContext)
            .stateIn(coroutineScope, SharingStarted.Eagerly, initializeState)
    }

    fun sendIntent(intent: Intent) {
        intentFlow.tryEmit(intent)
    }

    abstract fun Flow<Intent>.increaseAction(state: () -> State): Flow<Any>

    open suspend fun EventAction.reduceEvent() {}

    abstract fun StateAction.reduceState(state: State): State

    @Suppress("UNCHECKED_CAST")
    private fun <T: FlowAction> getClass(index: Int) =
        (this::class.supertypes.first().arguments[index].type?.classifier as? KClass<*>)?.javaObjectType as? Class<T>

    private fun <T: Any, R: FlowAction> Flow<T>.mapCast(actionClass: Class<R>?) =
        mapNotNull {
            runCatching {
                actionClass?.cast(it)
            }.getOrNull()
        }

    init {
        states
        eventClass?.let {
            if (FlowAction::class.java.isAssignableFrom(it)) {
                events
            }
        }
        viewClass?.let {
            if (FlowAction::class.java.isAssignableFrom(it)) {
                events
            }
        }
    }
}