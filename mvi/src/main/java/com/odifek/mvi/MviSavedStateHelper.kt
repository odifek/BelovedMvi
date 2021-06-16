package com.odifek.mvi

import android.os.Bundle
import androidx.lifecycle.SavedStateHandle
import androidx.savedstate.SavedStateRegistry

/**
 * A helper that you could use to handle saved state across process death by the mechanism of the new android [SavedStateHandle] api.
 * It doesn't make any assumptions. You give it a [stateProvider] (a function that would return the current state). It calls this function,
 * when it's time to save state, and hands you the same state to save and return in a [Bundle]. You can save just the items you want to.
 *
 * You can use the [restoreState] function to retrieve the items saved in it and use it to create a new State
 * @param stateProvider get the current state. Called when the time for saving state has come
 * @param saveStateInBundle gives you back the state it retrieved from [stateProvider] and you save the items you want in a bundle. These items are just the items required to restore the state of the app.
 * Data from asynchronous sources can be refreshed as usual
 * @param savedStateHandle the [SavedStateHandle] api handles the state saving and restoration in android. You can normally get this passed in the constructor of your ViewModel. see [Saved State Module](https://developer.android.com/topic/libraries/architecture/viewmodel-savedstate)
 * @param savedStateKey a unique key for managing the bundle where your items are saved. You want to use a unique key if you could have more than one bundle with similar item keys in the same [savedStateHandle]
 */
class MviSavedStateHelper<State>(
    private val stateProvider: () -> State,
    private val savedStateHandle: SavedStateHandle,
    private val savedStateKey: String = "MviSavedStateProvider",
    private val saveStateInBundle: (state: State) -> Bundle
) : SavedStateRegistry.SavedStateProvider {
    init {
        savedStateHandle.setSavedStateProvider(savedStateKey, this)
    }

    override fun saveState(): Bundle = saveStateInBundle(stateProvider())

    fun restoreState(fromBundle: (Bundle) -> State): State? =
        savedStateHandle.get<Bundle>(savedStateKey)?.let(fromBundle)
}