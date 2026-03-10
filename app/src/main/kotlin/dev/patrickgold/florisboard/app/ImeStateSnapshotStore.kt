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

package dev.patrickgold.florisboard.app

import android.content.Context
import dev.patrickgold.florisboard.ime.keyboard.KeyboardManager

class ImeStateSnapshotStore(context: Context) {
    companion object {
        private const val PREFS_NAME = "florisboard-ime-state-snapshot"
        private const val KEY_VERSION = "version"
        private const val KEY_CREATED_AT = "created_at"
        private const val KEY_IME_UI_MODE = "ime_ui_mode"
        private const val KEY_KEYBOARD_MODE = "keyboard_mode"
        private const val KEY_ACTIVE_SUBTYPE_ID = "active_subtype_id"
        private const val KEY_IS_ACTIONS_OVERFLOW_VISIBLE = "is_actions_overflow_visible"
        private const val KEY_IS_ACTIONS_EDITOR_VISIBLE = "is_actions_editor_visible"
        private const val KEY_IS_SUBTYPE_SELECTION_VISIBLE = "is_subtype_selection_visible"

        private const val SNAPSHOT_VERSION = 1
        private const val SNAPSHOT_TTL_MS = 6L * 60L * 60L * 1000L
    }

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun save(snapshot: KeyboardManager.RuntimeStateSnapshot, nowMs: Long = System.currentTimeMillis()) {
        prefs.edit()
            .putInt(KEY_VERSION, SNAPSHOT_VERSION)
            .putLong(KEY_CREATED_AT, nowMs)
            .putInt(KEY_IME_UI_MODE, snapshot.imeUiMode)
            .putInt(KEY_KEYBOARD_MODE, snapshot.keyboardMode)
            .putLong(KEY_ACTIVE_SUBTYPE_ID, snapshot.activeSubtypeId)
            .putBoolean(KEY_IS_ACTIONS_OVERFLOW_VISIBLE, snapshot.isActionsOverflowVisible)
            .putBoolean(KEY_IS_ACTIONS_EDITOR_VISIBLE, snapshot.isActionsEditorVisible)
            .putBoolean(KEY_IS_SUBTYPE_SELECTION_VISIBLE, snapshot.isSubtypeSelectionVisible)
            .apply()
    }

    fun restore(nowMs: Long = System.currentTimeMillis()): KeyboardManager.RuntimeStateSnapshot? {
        val version = prefs.getInt(KEY_VERSION, -1)
        if (version != SNAPSHOT_VERSION) {
            clear()
            return null
        }
        val createdAt = prefs.getLong(KEY_CREATED_AT, 0L)
        if (createdAt <= 0L || nowMs < createdAt || nowMs - createdAt > SNAPSHOT_TTL_MS) {
            clear()
            return null
        }
        val activeSubtypeId = prefs.getLong(KEY_ACTIVE_SUBTYPE_ID, 0L)
        if (activeSubtypeId <= 0L) {
            clear()
            return null
        }
        return KeyboardManager.RuntimeStateSnapshot(
            imeUiMode = prefs.getInt(KEY_IME_UI_MODE, 0),
            keyboardMode = prefs.getInt(KEY_KEYBOARD_MODE, 0),
            activeSubtypeId = activeSubtypeId,
            isActionsOverflowVisible = prefs.getBoolean(KEY_IS_ACTIONS_OVERFLOW_VISIBLE, false),
            isActionsEditorVisible = prefs.getBoolean(KEY_IS_ACTIONS_EDITOR_VISIBLE, false),
            isSubtypeSelectionVisible = prefs.getBoolean(KEY_IS_SUBTYPE_SELECTION_VISIBLE, false),
        )
    }

    fun clear() {
        prefs.edit().clear().apply()
    }
}
