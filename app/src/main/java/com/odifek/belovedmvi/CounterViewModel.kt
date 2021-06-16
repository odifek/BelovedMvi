package com.odifek.belovedmvi

import androidx.core.os.bundleOf
import androidx.core.util.Consumer
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.odifek.mvi.Actor
import com.odifek.mvi.Feature
import com.odifek.mvi.MviSavedStateHelper
import com.odifek.mvi.Reducer
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.onStart

class CounterViewModel(savedStateHandle: SavedStateHandle) : ViewModel(),
    Consumer<CounterViewModel.UiEvent> {
    private val mviSavedStatProvider = MviSavedStateHelper(
        stateProvider = { feature.value },
        savedStateHandle = savedStateHandle,
        savedStateKey = "CounterState"
    ) { state -> bundleOf("counter" to state.counter) }

    private val feature: Feature<FWish, FAction, FEffect, FState, FNews> = Feature(
        initialState = mviSavedStatProvider.restoreState { FState(counter = it.getInt("counter")) }
            ?: FState(),
        reducer = ReducerImp(),
        wishToAction = WishToAction,
        actor = ActorImp(),
        coroutineScope = viewModelScope
    )

    val counter: Flow<Int> = feature.mapLatest { it.counter }

    sealed class UiEvent {
        object ButtonInc : UiEvent()
    }


    override fun accept(event: UiEvent) {
        when (event) {
            UiEvent.ButtonInc -> feature.invoke(FWish.IncrementCounter)
        }
    }
}

sealed class FWish {
    object IncrementCounter : FWish()
}

data class FState(
    val isLoading: Boolean = false,
    val counter: Int = 0
)

sealed class FAction {
    object IncrementCounter : FAction()
}

sealed class FEffect {
    object Loading : FEffect()
    data class Success(val inc: Int) : FEffect()
}

sealed class FNews {

}

class ReducerImp : Reducer<FState, FEffect> {
    override fun invoke(state: FState, effect: FEffect): FState = when (effect) {
        FEffect.Loading -> state.copy(isLoading = true)
        is FEffect.Success -> state.copy(isLoading = false, counter = state.counter + effect.inc)
    }
}

object WishToAction : (FWish) -> FAction {
    override fun invoke(wish: FWish): FAction = when (wish) {
        FWish.IncrementCounter -> FAction.IncrementCounter
    }
}

class ActorImp : Actor<FState, FAction, FEffect> {
    override fun invoke(state: FState, action: FAction): Flow<FEffect> = when (action) {
        FAction.IncrementCounter -> flowOf<FEffect>(FEffect.Success(1)).onStart { emit(FEffect.Loading) }
    }
}
