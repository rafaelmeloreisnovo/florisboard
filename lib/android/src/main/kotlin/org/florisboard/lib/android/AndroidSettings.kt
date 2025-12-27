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

package org.florisboard.lib.android

import android.content.Context
import android.net.Uri
import android.provider.Settings
import org.florisboard.lib.kotlin.tryOrNull
import java.lang.reflect.Modifier
import kotlin.reflect.KClass

abstract class AndroidSettingsHelper(
    private val kClass: KClass<*>,
    val groupId: String,
) {
    // Cache for reflection results to avoid repeated reflection overhead
    private val fieldCache: Map<String, String> by lazy {
        buildMap {
            for (field in kClass.java.declaredFields) {
                if (Modifier.isStatic(field.modifiers)) {
                    tryOrNull {
                        // Attempt to make the field accessible; skip if access cannot be granted
                        try {
                            field.isAccessible = true
                        } catch (e: SecurityException) {
                            // Skip fields that cannot be made accessible
                            return@tryOrNull
                        }
                        val value = field.get(null) as? String
                        if (value != null) {
                            put(field.name, value)
                        }
                    }
                }
            }
        }
    }

    abstract fun getString(context: Context, key: String): String?

    abstract fun getUriFor(key: String): Uri?

    /**
     * Gets all static String fields from the settings class using reflection.
     * Results are cached after first call for performance.
     * 
     * @return Sequence of field name to field value pairs
     */
    fun getAllKeys(): Sequence<Pair<String, String>> {
        return fieldCache.entries.asSequence().map { it.toPair() }
    }

    fun observe(context: Context, key: String, observer: SystemSettingsObserver) {
        getUriFor(key)?.let { uri ->
            context.contentResolver.registerContentObserver(uri, false, observer)
            observer.dispatchChange(false, uri)
        }
    }

    fun removeObserver(context: Context, observer: SystemSettingsObserver) {
        context.contentResolver.unregisterContentObserver(observer)
    }
}

object AndroidSettings {
    val Global = object : AndroidSettingsHelper(Settings.Global::class, "global") {
        override fun getString(context: Context, key: String): String? {
            return tryOrNull { Settings.Global.getString(context.contentResolver, key) }
        }

        override fun getUriFor(key: String): Uri? {
            return tryOrNull { Settings.Global.getUriFor(key) }
        }
    }

    val Secure = object : AndroidSettingsHelper(Settings.Secure::class, "secure") {
        override fun getString(context: Context, key: String): String? {
            return tryOrNull { Settings.Secure.getString(context.contentResolver, key) }
        }

        override fun getUriFor(key: String): Uri? {
            return tryOrNull { Settings.Secure.getUriFor(key) }
        }
    }

    val System = object : AndroidSettingsHelper(Settings.System::class, "system") {
        override fun getString(context: Context, key: String): String? {
            return tryOrNull { Settings.System.getString(context.contentResolver, key) }
        }

        override fun getUriFor(key: String): Uri? {
            return tryOrNull { Settings.System.getUriFor(key) }
        }
    }
}
