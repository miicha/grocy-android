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

package xyz.zedler.patrick.grocy.dao;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import io.reactivex.rxjava3.core.Single;
import java.util.List;
import xyz.zedler.patrick.grocy.model.PendingStockCount;

/** FORK (offline inventory): access to the queue of bookings entered while offline. */
@Dao
public interface PendingStockCountDao {

  @Query("SELECT * FROM pending_stock_count_table ORDER BY created_at ASC")
  LiveData<List<PendingStockCount>> getAllLive();

  @Query("SELECT * FROM pending_stock_count_table ORDER BY created_at ASC")
  Single<List<PendingStockCount>> getPendingStockCounts();

  @Query("SELECT COUNT(*) FROM pending_stock_count_table")
  LiveData<Integer> getCountLive();

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  Single<Long> insertPendingStockCount(PendingStockCount pendingStockCount);

  @Query("DELETE FROM pending_stock_count_table WHERE id = :id")
  Single<Integer> deletePendingStockCount(long id);

  @Query("DELETE FROM pending_stock_count_table")
  Single<Integer> deletePendingStockCounts();
}
