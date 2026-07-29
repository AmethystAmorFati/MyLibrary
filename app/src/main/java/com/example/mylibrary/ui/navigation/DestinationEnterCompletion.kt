package com.example.mylibrary.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner

@Composable
internal fun rememberDestinationEnterCompleted(
    lifecycleOwner: LifecycleOwner
): Boolean {
    val lifecycle = lifecycleOwner.lifecycle
    var completed by rememberSaveable {
        mutableStateOf(
            latchDestinationEnterCompleted(
                wasCompleted = false,
                currentState = lifecycle.currentState
            )
        )
    }

    DisposableEffect(lifecycleOwner) {
        fun updateCompletion() {
            completed = latchDestinationEnterCompleted(
                wasCompleted = completed,
                currentState = lifecycle.currentState
            )
        }

        val observer = LifecycleEventObserver { _, _ ->
            updateCompletion()
        }
        lifecycle.addObserver(observer)
        updateCompletion()

        onDispose {
            lifecycle.removeObserver(observer)
        }
    }

    return completed
}

internal fun latchDestinationEnterCompleted(
    wasCompleted: Boolean,
    currentState: Lifecycle.State
): Boolean =
    wasCompleted || currentState.isAtLeast(Lifecycle.State.RESUMED)
