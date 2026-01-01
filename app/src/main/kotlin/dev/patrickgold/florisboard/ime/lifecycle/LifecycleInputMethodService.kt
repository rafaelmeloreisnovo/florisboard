/*
 * Copyright (C) 2022-2025 The FlorisBoard Contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package dev.patrickgold.florisboard.ime.lifecycle

import android.inputmethodservice.InputMethodService
import androidx.annotation.CallSuper
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.coroutineScope
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import kotlinx.coroutines.CoroutineScope
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

/**
 * Lifecycle-aware InputMethodService optimized for Android 15 ARM64 stability.
 * 
 * Key optimizations:
 * - Thread-safe lifecycle state management using atomic operations
 * - Defensive null checks for Android 15 window handling
 * - Proper lifecycle event sequencing to prevent zombie states
 * - Resource cleanup on destruction to prevent memory leaks
 */
open class LifecycleInputMethodService : InputMethodService(),
    LifecycleOwner, ViewModelStoreOwner, SavedStateRegistryOwner
{
    // Atomic state tracking for thread-safe lifecycle management
    private val lifecycleState = AtomicInteger(STATE_INITIAL)
    private val isDestroyed = AtomicBoolean(false)
    
    companion object {
        // Deterministic state constants using bitwise values
        private const val STATE_INITIAL = 0
        private const val STATE_CREATED = 1
        private const val STATE_STARTED = 2
        private const val STATE_RESUMED = 3
        private const val STATE_PAUSED = 4
        private const val STATE_STOPPED = 5
        private const val STATE_DESTROYED = 6
    }
    
    private val lifecycleRegistry by lazy { LifecycleRegistry(this) }
    private val store by lazy { ViewModelStore() }
    private val savedStateRegistryController by lazy { SavedStateRegistryController.create(this) }

    val uiScope: CoroutineScope
        get() = lifecycle.coroutineScope

    final override val savedStateRegistry: SavedStateRegistry
        get() = savedStateRegistryController.savedStateRegistry

    override val lifecycle: Lifecycle
        get() = lifecycleRegistry

    override val viewModelStore: ViewModelStore
        get() = store

    @CallSuper
    override fun onCreate() {
        super.onCreate()
        if (isDestroyed.get()) return
        
        savedStateRegistryController.performRestore(null)
        
        // Deterministic lifecycle event sequencing
        if (lifecycleState.compareAndSet(STATE_INITIAL, STATE_CREATED)) {
            lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
        }
        if (lifecycleState.compareAndSet(STATE_CREATED, STATE_STARTED)) {
            lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_START)
        }
    }

    /**
     * Installs view tree owners with defensive null handling for Android 15.
     * Uses safe navigation to prevent NPE on window access.
     */
    fun installViewTreeOwners() {
        if (isDestroyed.get()) return
        
        val decorView = window?.window?.decorView ?: return
        decorView.setViewTreeLifecycleOwner(this)
        decorView.setViewTreeViewModelStoreOwner(this)
        decorView.setViewTreeSavedStateRegistryOwner(this)
    }

    @CallSuper
    override fun onWindowShown() {
        super.onWindowShown()
        if (isDestroyed.get()) return
        
        // Use loop with CAS for atomic state transition
        while (true) {
            val currentState = lifecycleState.get()
            if (currentState >= STATE_STARTED && currentState < STATE_RESUMED) {
                if (lifecycleState.compareAndSet(currentState, STATE_RESUMED)) {
                    lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)
                    break
                }
                // CAS failed, retry with fresh state
            } else if (currentState == STATE_PAUSED) {
                if (lifecycleState.compareAndSet(STATE_PAUSED, STATE_RESUMED)) {
                    lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)
                    break
                }
                // CAS failed, retry with fresh state
            } else {
                // State already resumed or invalid, no action needed
                break
            }
        }
    }

    @CallSuper
    override fun onWindowHidden() {
        super.onWindowHidden()
        if (isDestroyed.get()) return
        
        // Use CAS for atomic state transition
        if (lifecycleState.compareAndSet(STATE_RESUMED, STATE_PAUSED)) {
            lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_PAUSE)
        }
    }

    @CallSuper
    override fun onDestroy() {
        // Mark as destroyed first to prevent concurrent operations
        if (!isDestroyed.compareAndSet(false, true)) {
            super.onDestroy()
            return
        }
        
        // Use loop with CAS for atomic state transitions
        while (true) {
            val currentState = lifecycleState.get()
            
            // Handle proper state transitions based on current state
            if (currentState == STATE_RESUMED) {
                if (lifecycleState.compareAndSet(STATE_RESUMED, STATE_PAUSED)) {
                    lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_PAUSE)
                    // Continue to stop state
                }
            } else if (currentState >= STATE_STARTED && currentState < STATE_STOPPED) {
                if (lifecycleState.compareAndSet(currentState, STATE_STOPPED)) {
                    lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_STOP)
                    break
                }
            } else {
                break
            }
        }
        
        lifecycleState.set(STATE_DESTROYED)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
        
        // Clear ViewModelStore to release resources
        store.clear()
        
        super.onDestroy()
    }
}
