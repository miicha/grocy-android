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

package xyz.zedler.patrick.grocy.repository;

import android.app.Application;
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.functions.Consumer;
import io.reactivex.rxjava3.schedulers.Schedulers;
import java.util.List;
import xyz.zedler.patrick.grocy.database.AppDatabase;
import xyz.zedler.patrick.grocy.model.PendingStockCount;

/** FORK (offline inventory): the queue of bookings entered while offline. */
public class PendingStockCountsRepository {

  private final AppDatabase appDatabase;

  public PendingStockCountsRepository(Application application) {
    this.appDatabase = AppDatabase.getAppDatabase(application);
  }

  public interface DataListener {

    void actionFinished(List<PendingStockCount> pendingStockCounts);
  }

  public void loadFromDatabase(DataListener onSuccess, Consumer<Throwable> onError) {
    appDatabase.pendingStockCountDao().getPendingStockCounts()
        .subscribeOn(Schedulers.io())
        .observeOn(AndroidSchedulers.mainThread())
        .doOnSuccess(onSuccess::actionFinished)
        .doOnError(onError)
        .onErrorComplete()
        .subscribe();
  }

  public void deletePendingStockCount(long id, Runnable onFinished) {
    appDatabase.pendingStockCountDao().deletePendingStockCount(id)
        .subscribeOn(Schedulers.io())
        .observeOn(AndroidSchedulers.mainThread())
        .doOnSuccess(i -> onFinished.run())
        .doOnError(e -> onFinished.run())
        .onErrorComplete()
        .subscribe();
  }

  public void deleteAllPendingStockCounts(Runnable onFinished) {
    appDatabase.pendingStockCountDao().deletePendingStockCounts()
        .subscribeOn(Schedulers.io())
        .observeOn(AndroidSchedulers.mainThread())
        .doOnSuccess(i -> onFinished.run())
        .doOnError(e -> onFinished.run())
        .onErrorComplete()
        .subscribe();
  }
}
