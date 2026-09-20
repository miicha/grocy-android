/*
 * This file is part of Grocy Android.
 *
 * Grocy Android is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Grocy Android is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Grocy Android. If not, see http://www.gnu.org/licenses/.
 *
 * Copyright (c) 2020-2024 by Patrick Zedler and Dominic Zedler
 * Copyright (c) 2024-2026 by Patrick Zedler
 */

package xyz.zedler.patrick.grocy.util;

import android.content.SharedPreferences;
import xyz.zedler.patrick.grocy.Constants.SETTINGS.BEHAVIOR;
import xyz.zedler.patrick.grocy.Constants.SETTINGS_DEFAULT;

/**
 * FORK (offline inventory): the one place that owns the offline state.
 *
 * <p>The state is a single preference, so the switch in the drawer always shows the truth — no
 * matter whether the user flipped it or a failed request did. A second, hidden flag remembers
 * which of the two it was: a state that switched itself on clears itself again as soon as the
 * server answers, while a deliberate choice stays until the user revokes it.
 *
 * <p>Sticky matters because {@code offlineLive} in BaseViewModel is per screen and starts
 * optimistic, so without this every screen would run into the network timeout again.
 */
public class OfflineModeUtil {

  public static boolean isEnabled(SharedPreferences sharedPrefs) {
    return sharedPrefs.getBoolean(BEHAVIOR.OFFLINE_MODE, SETTINGS_DEFAULT.BEHAVIOR.OFFLINE_MODE);
  }

  /** A request failed because the server could not be reached. */
  public static void enableDetected(SharedPreferences sharedPrefs) {
    if (isEnabled(sharedPrefs)) {
      return;
    }
    sharedPrefs.edit()
        .putBoolean(BEHAVIOR.OFFLINE_MODE, true)
        .putBoolean(BEHAVIOR.OFFLINE_MODE_AUTO, true)
        .apply();
  }

  /**
   * The user asked to go back online but the server did not answer. Stays offline, but from now
   * on as a detected state, so it clears itself once the server is reachable again.
   */
  public static void markDetected(SharedPreferences sharedPrefs) {
    sharedPrefs.edit()
        .putBoolean(BEHAVIOR.OFFLINE_MODE, true)
        .putBoolean(BEHAVIOR.OFFLINE_MODE_AUTO, true)
        .apply();
  }

  /** A request succeeded, so drop an offline state that nobody asked for. */
  public static void clearDetected(SharedPreferences sharedPrefs) {
    if (!isEnabled(sharedPrefs) || !sharedPrefs.getBoolean(BEHAVIOR.OFFLINE_MODE_AUTO, false)) {
      return;
    }
    sharedPrefs.edit()
        .putBoolean(BEHAVIOR.OFFLINE_MODE, false)
        .putBoolean(BEHAVIOR.OFFLINE_MODE_AUTO, false)
        .apply();
  }

  /** The user flipped the switch. */
  public static void setByUser(SharedPreferences sharedPrefs, boolean enabled) {
    sharedPrefs.edit()
        .putBoolean(BEHAVIOR.OFFLINE_MODE, enabled)
        .putBoolean(BEHAVIOR.OFFLINE_MODE_AUTO, false)
        .apply();
  }
}
