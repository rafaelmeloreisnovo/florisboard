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

package dev.patrickgold.florisboard

import android.app.Application
import android.content.BroadcastReceiver
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.IntentFilter
import android.os.Handler
import android.util.Log
import androidx.core.os.UserManagerCompat
import dev.patrickgold.florisboard.app.FlorisPreferenceModel
import dev.patrickgold.florisboard.app.FlorisPreferenceStore
import dev.patrickgold.florisboard.ime.clipboard.ClipboardManager
import dev.patrickgold.florisboard.ime.core.SubtypeManager
import dev.patrickgold.florisboard.ime.dictionary.DictionaryManager
import dev.patrickgold.florisboard.ime.editor.EditorInstance
import dev.patrickgold.florisboard.ime.keyboard.KeyboardManager
import dev.patrickgold.florisboard.ime.media.emoji.FlorisEmojiCompat
import dev.patrickgold.florisboard.ime.nlp.NlpManager
import dev.patrickgold.florisboard.ime.text.gestures.GlideTypingManager
import dev.patrickgold.florisboard.ime.theme.ThemeManager
import dev.patrickgold.florisboard.lib.cache.CacheManager
import dev.patrickgold.florisboard.lib.crashutility.CrashUtility
import dev.patrickgold.florisboard.lib.devtools.Flog
import dev.patrickgold.florisboard.lib.devtools.LogTopic
import dev.patrickgold.florisboard.lib.devtools.flogError
import dev.patrickgold.florisboard.lib.ext.ExtensionManager
import dev.patrickgold.jetpref.datastore.runtime.initAndroid
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import org.florisboard.lib.kotlin.io.deleteContentsRecursively
import org.florisboard.lib.kotlin.tryOrNull
import org.florisboard.libnative.dummyAdd
import java.lang.ref.WeakReference

/**
 * Global weak reference for the [FlorisApplication] class. This is needed as in certain scenarios an application
 * reference is needed, but the Android framework hasn't finished setting up
 */
private var FlorisApplicationReference = WeakReference<FlorisApplication?>(null)

@Suppress("unused")
class FlorisApplication : Application() {
    companion object {
        private const val TAG = "FlorisApplication"
        
        init {
            try {
                System.loadLibrary("fl_native")
                Log.i(TAG, "Native library loaded successfully")
            } catch (e: UnsatisfiedLinkError) {
                Log.e(TAG, "Failed to load native library: ${e.message}", e)
            } catch (e: Exception) {
                Log.e(TAG, "Unexpected error loading native library: ${e.message}", e)
            }
        }
    }

    private val mainHandler by lazy { Handler(mainLooper) }
    private val scope = CoroutineScope(Dispatchers.Default)
    val preferenceStoreLoaded = MutableStateFlow(false)

    val cacheManager = lazy { CacheManager(this) }
    val clipboardManager = lazy { ClipboardManager(this) }
    val editorInstance = lazy { EditorInstance(this) }
    val extensionManager = lazy { ExtensionManager(this) }
    val glideTypingManager = lazy { GlideTypingManager(this) }
    val keyboardManager = lazy { KeyboardManager(this) }
    val nlpManager = lazy { NlpManager(this) }
    val subtypeManager = lazy { SubtypeManager(this) }
    val themeManager = lazy { ThemeManager(this) }

    override fun onCreate() {
        super.onCreate()
        FlorisApplicationReference = WeakReference(this)
        try {
            Flog.install(
                context = this,
                isFloggingEnabled = BuildConfig.DEBUG,
                flogTopics = LogTopic.ALL,
                flogLevels = Flog.LEVEL_ALL,
                flogOutputs = Flog.OUTPUT_CONSOLE,
            )
            CrashUtility.install(this)
            
            // Initialize emoji compatibility with error handling
            try {
                FlorisEmojiCompat.init(this)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to initialize emoji compat", e)
            }
            
            flogError { "dummy result: ${dummyAdd(3,4)}" }

            if (!UserManagerCompat.isUserUnlocked(this)) {
                try {
                    cacheDir?.deleteContentsRecursively()
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to clear cache before user unlock", e)
                }
                
                try {
                    extensionManager.value.init()
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to initialize extension manager before user unlock", e)
                }
                
                try {
                    registerReceiver(BootComplete(), IntentFilter(Intent.ACTION_USER_UNLOCKED))
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to register boot completion receiver", e)
                }
                return
            }

            init()
        } catch (e: Exception) {
            Log.e(TAG, "Critical error during onCreate", e)
            CrashUtility.stageException(e)
            // Continue execution to allow partial functionality
            // Core components may still initialize successfully in init()
        }
    }

    fun init() {
        try {
            cacheDir?.deleteContentsRecursively()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to clear cache during initialization", e)
        }
        
        scope.launch {
            try {
                val result = FlorisPreferenceStore.initAndroid(
                    context = this@FlorisApplication,
                    datastoreName = FlorisPreferenceModel.NAME,
                )
                Log.i("PREFS", result.toString())
                preferenceStoreLoaded.value = true
            } catch (e: Exception) {
                Log.e(TAG, "Failed to initialize preference store", e)
                // Mark as loaded anyway to allow app to continue
                preferenceStoreLoaded.value = true
            }
        }
        
        try {
            extensionManager.value.init()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize extension manager", e)
        }
        
        try {
            clipboardManager.value.initializeForContext(this)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize clipboard manager", e)
        }
        
        try {
            DictionaryManager.init(this)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize dictionary manager", e)
        }
    }

    private inner class BootComplete : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent == null) return
            if (intent.action == Intent.ACTION_USER_UNLOCKED) {
                try {
                    unregisterReceiver(this)
                } catch (e: Exception) {
                    flogError { e.toString() }
                }
                mainHandler.post { init() }
            }
        }
    }
}

private tailrec fun Context.florisApplication(): FlorisApplication {
    return when (this) {
        is FlorisApplication -> this
        is ContextWrapper -> when {
            this.baseContext != null -> this.baseContext.florisApplication()
            else -> FlorisApplicationReference.get() ?: throw IllegalStateException("FlorisApplication not initialized")
        }
        else -> tryOrNull { this.applicationContext as FlorisApplication } 
            ?: FlorisApplicationReference.get() 
            ?: throw IllegalStateException("FlorisApplication not available")
    }
}

fun Context.appContext() = lazyOf(this.florisApplication())

fun Context.cacheManager() = this.florisApplication().cacheManager

fun Context.clipboardManager() = this.florisApplication().clipboardManager

fun Context.editorInstance() = this.florisApplication().editorInstance

fun Context.extensionManager() = this.florisApplication().extensionManager

fun Context.glideTypingManager() = this.florisApplication().glideTypingManager

fun Context.keyboardManager() = this.florisApplication().keyboardManager

fun Context.nlpManager() = this.florisApplication().nlpManager

fun Context.subtypeManager() = this.florisApplication().subtypeManager

fun Context.themeManager() = this.florisApplication().themeManager
