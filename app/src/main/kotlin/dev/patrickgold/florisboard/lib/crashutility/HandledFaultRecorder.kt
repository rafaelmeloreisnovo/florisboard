/*
 * Copyright (C) 2026 The FlorisBoard Contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package dev.patrickgold.florisboard.lib.crashutility

import android.content.Context
import android.os.Process
import android.util.Log
import java.io.File

/**
 * Persists exceptions which were deliberately caught so they are not lost from the
 * beta-test evidence chain. Files intentionally use CrashUtility's stacktrace suffix,
 * therefore the existing crash UI can surface and consume them without another store.
 */
object HandledFaultRecorder {
    private const val STACKTRACE_DIR = "unhandled_stacktraces"
    private const val HANDLED_PREFIX = "handled-"
    private const val STACKTRACE_SUFFIX = ".stacktrace"
    private const val MAX_HANDLED_FAULT_FILES = 20

    fun record(context: Context, source: String, throwable: Throwable): Boolean {
        return try {
            val dir = File(context.noBackupFilesDir, STACKTRACE_DIR)
            if (!dir.exists() && !dir.mkdirs()) {
                return false
            }

            val now = System.currentTimeMillis()
            val safeSource = source
                .lowercase()
                .replace(Regex("[^a-z0-9._-]+"), "-")
                .trim('-')
                .take(64)
                .ifBlank { "unknown" }
            val target = File(dir, "$HANDLED_PREFIX$now-$safeSource$STACKTRACE_SUFFIX")
            val payload = buildString {
                appendLine("kind=handled_runtime_fault")
                appendLine("source=$source")
                appendLine("timestamp_ms=$now")
                appendLine("pid=${Process.myPid()}")
                appendLine("thread=${Thread.currentThread().name}")
                appendLine("throwable=${throwable.javaClass.name}")
                appendLine()
                append(Log.getStackTraceString(throwable))
            }
            target.writeText(payload)
            rotate(dir)
            true
        } catch (recordingFailure: Exception) {
            Log.e("HandledFaultRecorder", "Failed to persist handled fault from $source", recordingFailure)
            false
        }
    }

    private fun rotate(dir: File) {
        val handled = dir.listFiles { file ->
            file.isFile && file.name.startsWith(HANDLED_PREFIX) && file.name.endsWith(STACKTRACE_SUFFIX)
        }?.sortedByDescending { it.lastModified() } ?: return

        handled.drop(MAX_HANDLED_FAULT_FILES).forEach { old ->
            if (!old.delete()) {
                Log.w("HandledFaultRecorder", "Could not delete old handled fault ${old.name}")
            }
        }
    }
}
