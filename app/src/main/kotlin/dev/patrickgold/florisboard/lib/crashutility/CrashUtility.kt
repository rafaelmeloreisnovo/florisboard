/*
 * Copyright (C) 2020-2025 The FlorisBoard Contributors
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

package dev.patrickgold.florisboard.lib.crashutility

import android.annotation.SuppressLint
import android.app.Activity
import android.app.Application
import android.app.Application.ActivityLifecycleCallbacks
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Process
import android.util.Log
import dev.patrickgold.florisboard.BuildConfig
import dev.patrickgold.florisboard.FlorisImeService
import dev.patrickgold.florisboard.R
import dev.patrickgold.florisboard.lib.devtools.LogTopic
import dev.patrickgold.florisboard.lib.devtools.flogError
import dev.patrickgold.florisboard.lib.devtools.flogInfo
import java.lang.ref.WeakReference
import java.util.concurrent.atomic.AtomicReference
import org.florisboard.lib.kotlin.io.FsDir
import org.florisboard.lib.kotlin.io.FsFile
import org.florisboard.lib.kotlin.io.subDir
import org.florisboard.lib.kotlin.io.subFile
import kotlin.system.exitProcess

/**
 * Abstract class which holds several static methods used for handling unexpected errors.
 *
 * Parts of this class (especially the install() function and the uncaughtException() handler) have
 * been inspired by the great CustomActivityOnCrash library:
 *  https://github.com/Ereza/CustomActivityOnCrash (licensed under Apache 2.0)
 *  https://github.com/Ereza/CustomActivityOnCrash/blob/master/library/src/main/java/cat/ereza/customactivityoncrash/CustomActivityOnCrash.java
 */
abstract class CrashUtility private constructor() {
    companion object {
        private const val SHARED_PREFS_FILE = "crash_utility"
        private const val SHARED_PREFS_LAST_CRASH_TIMESTAMP = "last_crash_timestamp"

        private const val NOTIFICATION_CHANNEL_ID = "${BuildConfig.APPLICATION_ID}.lib.crashutility"
        private const val NOTIFICATION_ID = 0xFBAD0100

        private const val UNHANDLED_STACKTRACES_DIR_NAME = "unhandled_stacktraces"
        private const val UNHANDLED_STACKTRACE_FILE_EXT = "stacktrace"

        private var lastActivityCreated: WeakReference<Activity?> = WeakReference(null)
        private val stagedException = AtomicReference<Throwable?>(null)

        fun install(context: Context?): Boolean {
            if (context == null) {
                flogError(LogTopic.CRASH_UTILITY) {
                    "Can't install crash handler with a null Context object, doing nothing!"
                }
                return false
            }
            val oldHandler = Thread.getDefaultUncaughtExceptionHandler()
            if (oldHandler is UncaughtExceptionHandler) {
                flogInfo(LogTopic.CRASH_UTILITY) {
                    "Crash handler is already installed, doing nothing!"
                }
            } else {
                val application = context.applicationContext
                if (application != null && application is Application) {
                    try {
                        Thread.setDefaultUncaughtExceptionHandler(
                            UncaughtExceptionHandler(
                                WeakReference(application),
                                WeakReference(oldHandler),
                                context.getUstDir(),
                            )
                        )
                        flogInfo(LogTopic.CRASH_UTILITY) {
                            "Successfully installed crash handler for this application!"
                        }
                    } catch (e: SecurityException) {
                        flogError(LogTopic.CRASH_UTILITY) {
                            "Failed to install crash handler, probably due to missing runtime permission 'setDefaultUncaughtExceptionHandler':\n$e"
                        }
                        return false
                    } catch (e: Exception) {
                        flogError(LogTopic.CRASH_UTILITY) {
                            "Failed to install crash handler due to an unspecified error:\n$e"
                        }
                        return false
                    }
                    application.registerActivityLifecycleCallbacks(object : ActivityLifecycleCallbacks {
                        override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {
                            if (activity !is CrashDialogActivity) {
                                lastActivityCreated = WeakReference(activity)
                            }
                        }
                        override fun onActivityStarted(activity: Activity) {}
                        override fun onActivityResumed(activity: Activity) {}
                        override fun onActivityPaused(activity: Activity) {}
                        override fun onActivityStopped(activity: Activity) {}
                        override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}
                        override fun onActivityDestroyed(activity: Activity) {}
                    })
                    try {
                        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE)
                        if (notificationManager is NotificationManager) {
                            val notificationChannel = NotificationChannel(
                                NOTIFICATION_CHANNEL_ID,
                                context.resources.getString(R.string.crash_notification_channel__title),
                                NotificationManager.IMPORTANCE_HIGH
                            )
                            notificationManager.createNotificationChannel(notificationChannel)
                        }
                        flogInfo(LogTopic.CRASH_UTILITY) {
                            "Successfully created crash handler notification channel!"
                        }
                    } catch (e: Exception) {
                        flogError(LogTopic.CRASH_UTILITY) {
                            "Failed to create crash handler notification channel due to an unspecified error:\n$e"
                        }
                    }
                } else {
                    flogError(LogTopic.CRASH_UTILITY) {
                        "Can't install crash handler with a null Application object, doing nothing!"
                    }
                    return false
                }
            }
            return true
        }

        fun stageException(e: Throwable?) {
            if (e != null) {
                stagedException.compareAndSet(null, e)
            }
        }

        fun handleStagedButUnhandledExceptions() {
            val e = stagedException.getAndSet(null) ?: return
            val handler = Thread.getDefaultUncaughtExceptionHandler()
            if (handler is UncaughtExceptionHandler) {
                handler.uncaughtException(Thread.currentThread(), e)
            }
        }

        fun getUnhandledStacktraces(context: Context?): List<Stacktrace> {
            context ?: return listOf()
            val retList = mutableListOf<Stacktrace>()
            val ustDir = context.getUstDir()
            if (ustDir.isDirectory) {
                (ustDir.listFiles { pathname ->
                    pathname.name.endsWith(".$UNHANDLED_STACKTRACE_FILE_EXT")
                })?.forEach { file ->
                    flogInfo(LogTopic.CRASH_UTILITY) {
                        "Reading unhandled stacktrace: ${file.name}"
                    }
                    retList.add(Stacktrace(file.name, readFile(file)))
                    file.delete()
                }
            }
            return retList.toList()
        }

        fun hasUnhandledStacktraceFiles(context: Context): Boolean {
            val ustDir = context.getUstDir()
            return if (ustDir.isDirectory) {
                (ustDir.listFiles { pathname ->
                    pathname.name.endsWith(".$UNHANDLED_STACKTRACE_FILE_EXT")
                })?.isNotEmpty() ?: false
            } else {
                false
            }
        }

        private fun getLastCrashTimestamp(context: Context?): Long {
            context ?: return 0
            return context.getSharedPreferences(SHARED_PREFS_FILE, Context.MODE_PRIVATE)
                .getLong(SHARED_PREFS_LAST_CRASH_TIMESTAMP, 0)
        }

        @SuppressLint("ApplySharedPref")
        private fun setLastCrashTimestamp(context: Context?, value: Long) {
            context ?: return
            context.getSharedPreferences(SHARED_PREFS_FILE, Context.MODE_PRIVATE)
                .edit()
                .putLong(SHARED_PREFS_LAST_CRASH_TIMESTAMP, value)
                .commit()
        }

        private fun Context.getUstDir(): FsDir {
            return this.noBackupFilesDir.subDir(UNHANDLED_STACKTRACES_DIR_NAME).also { it.mkdirs() }
        }

        private fun Context.getUstFile(timestamp: Long): FsFile {
            return this.getUstDir().subFile("$timestamp.$UNHANDLED_STACKTRACE_FILE_EXT")
        }

        private fun pushNotification(context: Context?, id: Int, title: String, body: String) {
            context ?: return
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE)
            if (notificationManager is NotificationManager) {
                val notificationBuilder = Notification.Builder(context.applicationContext, NOTIFICATION_CHANNEL_ID)
                val crashDialogIntent = Intent(context, CrashDialogActivity::class.java)
                val notification = notificationBuilder.run {
                    setContentTitle(title)
                    style = Notification.BigTextStyle().bigText(body)
                    setContentText(body)
                    setSmallIcon(android.R.drawable.stat_notify_error)
                    setContentIntent(PendingIntent.getActivity(context, 0, crashDialogIntent, PendingIntent.FLAG_IMMUTABLE)).setAutoCancel(true)
                    build()
                }
                notificationManager.notify(id, notification)
            }
        }

        private fun pushCrashOnceNotification(context: Context?) {
            context ?: return
            pushNotification(
                context,
                NOTIFICATION_ID.toInt(),
                context.resources.getString(R.string.crash_once_notification__title),
                context.resources.getString(R.string.crash_once_notification__body)
            )
        }

        private fun pushCrashMultipleNotification(context: Context?) {
            context ?: return
            pushNotification(
                context,
                NOTIFICATION_ID.toInt(),
                context.resources.getString(R.string.crash_multiple_notification__title),
                context.resources.getString(R.string.crash_multiple_notification__body)
            )
        }

        private fun readFile(file: FsFile): String {
            val retText = StringBuilder()
            if (file.exists()) {
                val newLine = System.lineSeparator()
                file.forEachLine {
                    retText.append(it)
                    retText.append(newLine)
                }
            }
            return retText.toString()
        }

        private fun writeToFile(file: FsFile, text: String) {
            try {
                file.writeText(text)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    data class Stacktrace(
        val name: String,
        val details: String,
    )

    class UncaughtExceptionHandler(
        private val application: WeakReference<Application>,
        private val oldHandler: WeakReference<Thread.UncaughtExceptionHandler?>,
        private val ustDir: FsDir,
    ) : Thread.UncaughtExceptionHandler {
        override fun uncaughtException(thread: Thread, throwable: Throwable) {
            flogInfo(LogTopic.CRASH_UTILITY) {
                "Detected application crash, executing custom crash handler."
            }
            val timestamp = System.currentTimeMillis()
            val stacktrace = Log.getStackTraceString(throwable)
            val ustFile = ustDir.subFile("$timestamp.$UNHANDLED_STACKTRACE_FILE_EXT")
            writeToFile(ustFile, stacktrace)
            val application = application.get()
            if (application != null) {
                val lastTimestamp = getLastCrashTimestamp(application)
                if (lastTimestamp > 0) {
                    val lastFile = application.getUstFile(lastTimestamp)
                    val lastStacktrace = readFile(lastFile)
                    if (lastStacktrace == stacktrace) {
                        lastFile.delete()
                    }
                }
                setLastCrashTimestamp(application, timestamp)
                if (timestamp - lastTimestamp < 5000) {
                    pushCrashMultipleNotification(application)
                    FlorisImeService.switchToPrevInputMethod() || FlorisImeService.switchToNextInputMethod()
                } else {
                    pushCrashOnceNotification(application)
                }
            }
            val lastActivity = lastActivityCreated.get()
            if (lastActivity != null) {
                lastActivity.finish()
                lastActivityCreated.clear()
            }
            Process.killProcess(Process.myPid())
            exitProcess(10)
        }
    }
}
