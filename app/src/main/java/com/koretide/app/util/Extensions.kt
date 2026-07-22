package com.koretide.app.util

import android.view.View
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch

fun View.visible() { visibility = View.VISIBLE }
fun View.gone() { visibility = View.GONE }

fun <T> LifecycleOwner.collectFlow(
    flow: Flow<T>,
    action: suspend (T) -> Unit
) {
    lifecycleScope.launch {
        repeatOnLifecycle(Lifecycle.State.STARTED) {
            flow.collect(action)
        }
    }
}
