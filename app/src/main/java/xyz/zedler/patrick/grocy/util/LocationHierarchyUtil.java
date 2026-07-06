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

import android.text.TextUtils;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import java.text.Collator;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import xyz.zedler.patrick.grocy.model.Location;

// FORK (sublocations): the grocy server fork gives locations a parent_location_id,
// forming a tree. Path/depth/descendants are computed client-side from the cached
// locations so everything also works offline and against vanilla servers (where all
// locations are roots and path == name, i.e. behavior is identical to upstream).
public class LocationHierarchyUtil {

  // Same separator the server fork uses in its locations_hierarchy view
  private static final String PATH_SEPARATOR = " > ";

  public static HashMap<Integer, Location> getLocationsById(@Nullable List<Location> locations) {
    HashMap<Integer, Location> locationsById = new HashMap<>();
    if (locations == null) {
      return locationsById;
    }
    for (Location location : locations) {
      locationsById.put(location.getId(), location);
    }
    return locationsById;
  }

  // Names from root to the given location itself; cycle-guarded
  @NonNull
  private static List<String> getPathNames(
      @NonNull Location location,
      @NonNull Map<Integer, Location> locationsById
  ) {
    Deque<String> names = new ArrayDeque<>();
    Set<Integer> visited = new HashSet<>();
    Location current = location;
    while (current != null && visited.add(current.getId())) {
      names.addFirst(current.getName());
      current = current.hasParentLocation()
          ? locationsById.get(current.getParentLocationIdInt())
          : null;
    }
    return new ArrayList<>(names);
  }

  @NonNull
  public static String getPath(
      @Nullable Location location,
      @NonNull Map<Integer, Location> locationsById
  ) {
    if (location == null) {
      return "";
    }
    // TextUtils.join instead of String.join (which needs API 26, minSdk is lower)
    return TextUtils.join(PATH_SEPARATOR, getPathNames(location, locationsById));
  }

  @NonNull
  public static String getPath(@Nullable Location location, @Nullable List<Location> locations) {
    if (location == null) {
      return "";
    }
    return getPath(location, getLocationsById(locations));
  }

  // Ids of the given location and all of its descendants
  @NonNull
  public static Set<Integer> getLocationIdsIncludingSub(
      @Nullable List<Location> locations,
      int locationId
  ) {
    Set<Integer> ids = new HashSet<>();
    ids.add(locationId);
    if (locations == null) {
      return ids;
    }
    boolean changed = true;
    while (changed) {
      changed = false;
      for (Location location : locations) {
        if (location.hasParentLocation()
            && ids.contains(location.getParentLocationIdInt())
            && ids.add(location.getId())) {
          changed = true;
        }
      }
    }
    return ids;
  }

  // Depth-first order: every location right after its parent, siblings sorted
  // locale-aware by name. With no parents anywhere (vanilla server) this equals
  // SortUtil.sortLocationsByName.
  public static void sortLocationsByPath(@Nullable List<Location> locations, boolean ascending) {
    if (locations == null) {
      return;
    }
    Map<Integer, Location> locationsById = getLocationsById(locations);
    HashMap<Integer, List<String>> pathNamesById = new HashMap<>();
    for (Location location : locations) {
      pathNamesById.put(location.getId(), getPathNames(location, locationsById));
    }
    Locale locale = LocaleUtil.getLocale();
    Collator collator = Collator.getInstance(locale);
    Collections.sort(locations, (item1, item2) -> {
      List<String> path1 = pathNamesById.get((ascending ? item1 : item2).getId());
      List<String> path2 = pathNamesById.get((ascending ? item2 : item1).getId());
      assert path1 != null && path2 != null;
      int length = Math.min(path1.size(), path2.size());
      for (int i = 0; i < length; i++) {
        int result = collator.compare(
            path1.get(i).toLowerCase(), path2.get(i).toLowerCase()
        );
        if (result != 0) {
          return result;
        }
      }
      return Integer.compare(path1.size(), path2.size());
    });
  }
}
