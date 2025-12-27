/*
 * Copyright (C) 2025 The FlorisBoard Contributors
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

package org.florisboard.lib.kotlin

import kotlin.reflect.KClass

/**
 * Gets the simple name of a class, handling Companion objects specially.
 * For Companion objects, returns the name of the enclosing class.
 * 
 * @return The simple name of the class, or the enclosing class name for Companion objects.
 *         Returns null if the class name cannot be determined.
 */
fun KClass<*>.simpleNameOrEnclosing(): String? {
    return try {
        if (this.simpleName == "Companion") {
            // Companion object => get the enclosing class
            this.java.enclosingClass?.simpleName
        } else {
            // Normal object => directly get class
            this.simpleName
        }
    } catch (e: Exception) {
        // Handle cases where reflection fails (e.g., obfuscated code)
        null
    }
}
