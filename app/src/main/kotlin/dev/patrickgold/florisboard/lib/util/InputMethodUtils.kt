/*
 * Copyright (C) 2021-2025 The FlorisBoard Contributors
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

package dev.patrickgold.florisboard.lib.util

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.view.inputmethod.InputMethodManager
import androidx.annotation.RequiresApi
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle
import dev.patrickgold.florisboard.BuildConfig
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import org.florisboard.lib.android.AndroidSettings
import org.florisboard.lib.android.AndroidVersion
import org.florisboard.lib.android.systemServiceOrNull
import org.florisboard.lib.compose.observeAsState

private const val DELIMITER = ':'
private const val IME_SERVICE_CLASS_NAME = "dev.patrickgold.florisboard.FlorisImeService"

/**
 * Query delay interval in milliseconds.
 * Optimized value to balance responsiveness with battery/CPU usage.
 */
private const val TIMED_QUERY_DELAY = 750L

/**
 * Maximum query iterations to prevent infinite loops in edge cases.
 * Acts as a circuit breaker for lifecycle-unaware scenarios.
 */
private const val MAX_QUERY_ITERATIONS = 10000

/**
 * Input method utilities optimized for Android 15 ARM64.
 * 
 * Key optimizations:
 * - Lifecycle-aware polling to prevent zombie loops
 * - Deterministic iteration limits as circuit breakers
 * - Reduced query frequency to minimize IOPS
 * - Thread-safe state management
 */
object InputMethodUtils {
    
    /**
     * Checks if FlorisBoard IME is enabled in system settings.
     * Uses deterministic API-level detection for optimal performance.
     */
    fun isFlorisboardEnabled(context: Context): Boolean {
        return if (AndroidVersion.ATLEAST_API34_U) {
            context.systemServiceOrNull(InputMethodManager::class)
                ?.enabledInputMethodList
                ?.any { it.packageName == BuildConfig.APPLICATION_ID } ?: false
        } else {
            val enabledImeList = AndroidSettings.Secure.getString(
                context, Settings.Secure.ENABLED_INPUT_METHODS
            )
            enabledImeList != null && parseIsFlorisboardEnabled(context, enabledImeList)
        }
    }

    /**
     * Checks if FlorisBoard IME is the currently selected input method.
     * Uses deterministic API-level detection for optimal performance.
     */
    fun isFlorisboardSelected(context: Context): Boolean {
        return if (AndroidVersion.ATLEAST_API34_U) {
            context.systemServiceOrNull(InputMethodManager::class)
                ?.currentInputMethodInfo
                ?.packageName == BuildConfig.APPLICATION_ID
        } else {
            val selectedIme = AndroidSettings.Secure.getString(
                context, Settings.Secure.DEFAULT_INPUT_METHOD
            )
            selectedIme != null && parseIsFlorisboardSelected(context, selectedIme)
        }
    }

    @Composable
    fun observeIsFlorisboardEnabled(
        context: Context = LocalContext.current.applicationContext,
        foregroundOnly: Boolean = false,
    ): State<Boolean> {
        return if (AndroidVersion.ATLEAST_API34_U) {
            timedObserveIsFlorisBoardEnabled()
        } else {
            AndroidSettings.Secure.observeAsState(
                key = Settings.Secure.ENABLED_INPUT_METHODS,
                foregroundOnly = foregroundOnly,
                transform = { parseIsFlorisboardEnabled(context, it.toString()) },
            )
        }
    }

    @Composable
    fun observeIsFlorisboardSelected(
        context: Context = LocalContext.current.applicationContext,
        foregroundOnly: Boolean = false,
    ): State<Boolean> {
        return if (AndroidVersion.ATLEAST_API34_U) {
            timedObserveIsFlorisBoardSelected()
        } else {
            AndroidSettings.Secure.observeAsState(
                key = Settings.Secure.DEFAULT_INPUT_METHOD,
                foregroundOnly = foregroundOnly,
                transform = { parseIsFlorisboardSelected(context, it.toString()) },
            )
        }
    }

    /**
     * Parses enabled IME list string to check if FlorisBoard is enabled.
     * Iterates through component strings with early exit on match.
     */
    fun parseIsFlorisboardEnabled(context: Context, activeImeIds: String): Boolean {
        val components = activeImeIds.split(DELIMITER)
        val targetPkg = context.packageName
        
        // Iterate through components with early exit on match
        for (i in components.indices) {
            val component = ComponentName.unflattenFromString(components[i])
            if (component?.packageName == targetPkg && component.className == IME_SERVICE_CLASS_NAME) {
                return true
            }
        }
        return false
    }

    /**
     * Parses selected IME string to check if FlorisBoard is selected.
     */
    fun parseIsFlorisboardSelected(context: Context, selectedImeId: String): Boolean {
        val component = ComponentName.unflattenFromString(selectedImeId)
        return component?.packageName == context.packageName && component?.className == IME_SERVICE_CLASS_NAME
    }

    fun showImeEnablerActivity(context: Context) {
        val intent = Intent()
        intent.action = Settings.ACTION_INPUT_METHOD_SETTINGS
        intent.addCategory(Intent.CATEGORY_DEFAULT)
        context.startActivity(intent)
    }

    fun showImePicker(context: Context): Boolean {
        val imm = context.systemServiceOrNull(InputMethodManager::class)
        return if (imm != null) {
            imm.showInputMethodPicker()
            true
        } else {
            false
        }
    }

    /**
     * Lifecycle-aware polling for FlorisBoard enabled state.
     * Prevents infinite loops by:
     * 1. Using lifecycle-aware repeatOnLifecycle
     * 2. Checking coroutine isActive state
     * 3. Limiting maximum iterations as circuit breaker
     */
    @RequiresApi(api = 34)
    @Composable
    private fun timedObserveIsFlorisBoardEnabled(): State<Boolean> {
        val state = remember { mutableStateOf(false) }
        val context = LocalContext.current
        val lifecycleOwner = LocalLifecycleOwner.current
        
        LaunchedEffect(lifecycleOwner) {
            lifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                var iterations = 0
                while (isActive && iterations < MAX_QUERY_ITERATIONS) {
                    state.value = isFlorisboardEnabled(context)
                    delay(TIMED_QUERY_DELAY)
                    iterations++
                }
            }
        }
        return state
    }

    /**
     * Lifecycle-aware polling for FlorisBoard selected state.
     * Prevents infinite loops by:
     * 1. Using lifecycle-aware repeatOnLifecycle
     * 2. Checking coroutine isActive state
     * 3. Limiting maximum iterations as circuit breaker
     */
    @RequiresApi(api = 34)
    @Composable
    private fun timedObserveIsFlorisBoardSelected(): State<Boolean> {
        val state = remember { mutableStateOf(false) }
        val context = LocalContext.current
        val lifecycleOwner = LocalLifecycleOwner.current
        
        LaunchedEffect(lifecycleOwner) {
            lifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                var iterations = 0
                while (isActive && iterations < MAX_QUERY_ITERATIONS) {
                    state.value = isFlorisboardSelected(context)
                    delay(TIMED_QUERY_DELAY)
                    iterations++
                }
            }
        }
        return state
    }
}
