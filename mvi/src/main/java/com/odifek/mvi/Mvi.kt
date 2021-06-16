package com.odifek.mvi

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.InternalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*

/**
 * A reducer takes the current State and the Effect of an Action, combines them to produce a new State
 */
typealias Reducer<State, Effect> = (state: State, effect: Effect) -> State

/**Take the action, effect and current state and optionally publish a news with it.*/
typealias NewsPublisher<Action, Effect, State, News> = (action: Action, effect: Effect, state: State) -> News?

/**
 * When an action has been processed, further actions could be triggered depending on the state
 */
typealias PostProcessor<Action, Effect, State> = (action: Action, effect: Effect, state: State) -> Action?

/**
 * Takes a State, Action and produces an observable [Flow] of Effects. This is used to do actual asynchronous work.
 * Your actor can take an Action and perform an async job. It can send a Loading or InProgress effect first if the job could take a long time
 */
typealias Actor<State, Action, Effect> = (state: State, action: Action) -> Flow<Effect>

/**
 * A feature encapsulates the functionality of the redux store. It exposes a [StateFlow] with which the current [value] of the state can be accessed
 * @param Wish represents a signal/input from the UI
 * @param Action is an internal representation of an action to be performed. [Wish] would be converted to [Action].
 * Other events that are performed outside the UI would have to be mapped to [Action] as well
 * @param Effect represents the result of the reducer that operates on the [Wish] or [Action]
 * @param State represents the state of the [Feature] at any point in time
 * @param News is more like a one time event that we do not want to persist in the [State]
 * @param reducer takes the current [State] and an [Effect] and combines them to produce a new [State]
 * @param wishToAction maps a [Wish] to corresponding [Action]. Which is an internal representation
 * @param actor is used to process asynchronous tasks. Takes a State, Action and produces an observable [Flow] of Effects. This is used to do actual asynchronous work
 * @param newsPublisher an optional one time event publisher. Takes the [Action], [Effect] that's produced and the current [State] to determine if a one time event is required
 * @param postProcessor  When an action has been processed, further actions could be triggered depending on the state
 * @param coroutineScope is used to subscribe to the events that's emitted by this Feature. The coroutines would be cancelled according to this scope
 *
 */
class Feature<Wish, Action, Effect, State, News>(
    initialState: State,
    private val reducer: Reducer<State, Effect>,
    private val wishToAction: (Wish) -> Action,
    private val actor: Actor<State, Action, Effect>,
    private val newsPublisher: NewsPublisher<Action, Effect, State, News>? = null,
    private val postProcessor: PostProcessor<Action, Effect, State>? = null,
    private val coroutineScope: CoroutineScope
) : StateFlow<State>, (Wish) -> Unit {
    private val mutableState: MutableStateFlow<State> = MutableStateFlow(initialState)

    private val actions: Channel<Action> = Channel(Channel.BUFFERED)

    private val newsChannel: Channel<News> = Channel(Channel.BUFFERED)

    /**Exposes flow for publishing news, aka one-time events*/
    val news: Flow<News> = newsChannel.receiveAsFlow()

    init {

        actions.consumeAsFlow()
            .flatMapConcat { action ->
                actor(value, action).map { Pair(action, it) }
            }
            .map {
                val (action, effect) = it
                val state = reducer.invoke(value, effect)
                Triple(action, effect, state)
            }
            .onEach {
                val (action, effect, state) = it
                mutableState.emit(state) // Emit the current state
                val otherAction = postProcessor?.invoke(action, effect, state) //
                val news = newsPublisher?.invoke(action, effect, state)
                if (news != null) {
                    newsChannel.send(news)
                }
                if (otherAction != null) {
                    actions.trySend(otherAction)
                }
            }
            .launchIn(coroutineScope)

    }

    override fun invoke(wish: Wish) {
        val action = wishToAction(wish)
        actions.trySend(action)
    }

    @InternalCoroutinesApi
    override suspend fun collect(collector: FlowCollector<State>) = mutableState.collect(collector)

    override val replayCache: List<State>
        get() = mutableState.replayCache
    override val value: State
        get() = mutableState.value

}