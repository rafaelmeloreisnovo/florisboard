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

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.PowerManager
import android.provider.Settings
import android.widget.Toast
import dev.patrickgold.florisboard.R
import dev.patrickgold.florisboard.lib.devtools.flogError

object BatteryOptimizationUtils {
    fun isIgnoringBatteryOptimizations(context: Context): Boolean {
        val powerManager = context.getSystemService(PowerManager::class.java)
        return powerManager?.isIgnoringBatteryOptimizations(context.packageName) == true
    }

    fun canOpenIgnoreBatteryOptimizationRequest(context: Context): Boolean {
        val requestIntent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
            data = Uri.parse("package:${context.packageName}")
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        return requestIntent.resolveActivity(context.packageManager) != null
    }

    fun openBatteryOptimizationSettings(context: Context): Boolean {
        val requestIntent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
            data = Uri.parse("package:${context.packageName}")
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        val fallbackIntent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }

        val intentsToTry = listOf(requestIntent, fallbackIntent)
        for (intent in intentsToTry) {
            if (intent.resolveActivity(context.packageManager) == null) {
                continue
            }
            try {
                context.startActivity(intent)
                return true
            } catch (e: ActivityNotFoundException) {
                flogError { e.toString() }
            } catch (e: SecurityException) {
                flogError { e.toString() }
            }
        }

        Toast.makeText(context, context.getString(R.string.general__battery_optimization__settings_not_available), Toast.LENGTH_LONG).show()
        return false
    }
}
