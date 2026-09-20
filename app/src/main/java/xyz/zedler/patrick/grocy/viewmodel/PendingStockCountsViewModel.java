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

package xyz.zedler.patrick.grocy.viewmodel;

import android.app.Application;
import android.content.SharedPreferences;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.util.Log;
import androidx.annotation.NonNull;
import androidx.lifecycle.MutableLiveData;
import androidx.preference.PreferenceManager;
import com.android.volley.VolleyError;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import org.json.JSONException;
import org.json.JSONObject;
import xyz.zedler.patrick.grocy.Constants;
import xyz.zedler.patrick.grocy.R;
import xyz.zedler.patrick.grocy.api.GrocyApi;
import xyz.zedler.patrick.grocy.helper.DownloadHelper;
import xyz.zedler.patrick.grocy.model.PendingStockCount;
import xyz.zedler.patrick.grocy.model.SnackbarMessage;
import xyz.zedler.patrick.grocy.repository.PendingStockCountsRepository;
import xyz.zedler.patrick.grocy.util.PrefsUtil;

/**
 * FORK (offline inventory): shows the bookings that were entered while offline and sends them to
 * the server one after another.
 */
public class PendingStockCountsViewModel extends BaseViewModel {

  private static final String TAG = PendingStockCountsViewModel.class.getSimpleName();

  /**
   * Grocy rejects an inventory whose new amount already equals the current stock amount. When
   * replaying a queue that simply means the target state is already reached, so it counts as
   * success rather than as a failure that would block the queue.
   */
  private static final String ERROR_AMOUNT_EQUALS = "cannot equal the current stock amount";

  private final SharedPreferences sharedPrefs;
  private final boolean debug;

  private final DownloadHelper dlHelper;
  private final GrocyApi grocyApi;
  private final PendingStockCountsRepository repository;

  private final MutableLiveData<Boolean> isLoadingLive;
  private final MutableLiveData<Boolean> isSyncingLive;
  private final MutableLiveData<List<PendingStockCount>> itemsLive;
  private final MutableLiveData<Boolean> offlineModeLive;
  /** Kept as a field — SharedPreferences only holds a weak reference to its listeners. */
  private final OnSharedPreferenceChangeListener prefsListener;

  public PendingStockCountsViewModel(@NonNull Application application) {
    super(application);

    sharedPrefs = PreferenceManager.getDefaultSharedPreferences(getApplication());
    debug = PrefsUtil.isDebuggingEnabled(sharedPrefs);

    isLoadingLive = new MutableLiveData<>(false);
    isSyncingLive = new MutableLiveData<>(false);
    itemsLive = new MutableLiveData<>(new ArrayList<>());
    offlineModeLive = new MutableLiveData<>(readOfflineMode());
    // the mode can be flipped from the drawer while this screen is open
    prefsListener = (prefs, key) -> {
      if (Constants.SETTINGS.BEHAVIOR.OFFLINE_MODE.equals(key)) {
        offlineModeLive.setValue(readOfflineMode());
      }
    };
    sharedPrefs.registerOnSharedPreferenceChangeListener(prefsListener);

    dlHelper = new DownloadHelper(getApplication(), TAG, isLoadingLive::setValue, getOfflineLive());
    grocyApi = new GrocyApi(getApplication());
    repository = new PendingStockCountsRepository(application);
  }

  public void loadFromDatabase() {
    repository.loadFromDatabase(
        itemsLive::setValue,
        error -> onError(error, TAG)
    );
  }

  public MutableLiveData<List<PendingStockCount>> getItemsLive() {
    return itemsLive;
  }

  public MutableLiveData<Boolean> getIsLoadingLive() {
    return isLoadingLive;
  }

  public MutableLiveData<Boolean> getIsSyncingLive() {
    return isSyncingLive;
  }

  public MutableLiveData<Boolean> getOfflineModeLive() {
    return offlineModeLive;
  }

  private boolean readOfflineMode() {
    return sharedPrefs.getBoolean(
        Constants.SETTINGS.BEHAVIOR.OFFLINE_MODE,
        Constants.SETTINGS_DEFAULT.BEHAVIOR.OFFLINE_MODE
    );
  }

  /** Lets the user leave offline mode straight from this screen. */
  public void disableOfflineMode() {
    sharedPrefs.edit().putBoolean(Constants.SETTINGS.BEHAVIOR.OFFLINE_MODE, false).apply();
  }

  /** Sends every queued booking, in the order it was entered. */
  public void transferAll() {
    List<PendingStockCount> items = itemsLive.getValue();
    if (items == null || items.isEmpty()) {
      showMessage(R.string.msg_pending_stock_counts_empty);
      return;
    }
    if (Boolean.TRUE.equals(isSyncingLive.getValue())) {
      return;
    }
    // sending while offline mode is still on would just fail for every single entry
    if (readOfflineMode()) {
      showMessage(R.string.msg_pending_stock_counts_offline_mode);
      return;
    }
    isSyncingLive.setValue(true);
    sendNext(new ArrayList<>(items), 0, 0, new ArrayList<>());
  }

  private void sendNext(
      List<PendingStockCount> items,
      int index,
      int transferred,
      List<String> failed
  ) {
    if (index >= items.size()) {
      finishSync(transferred, failed);
      return;
    }
    PendingStockCount item = items.get(index);
    JSONObject body;
    try {
      body = new JSONObject(item.getBody());
    } catch (JSONException e) {
      if (debug) {
        Log.e(TAG, "sendNext: unreadable body of " + item + ": " + e);
      }
      failed.add(item.getProductName());
      sendNext(items, index + 1, transferred, failed);
      return;
    }
    String url = item.isInventory()
        ? grocyApi.inventoryProduct(item.getProductId())
        : grocyApi.consumeProduct(item.getProductId());
    dlHelper.postWithArray(
        url,
        body,
        response -> dropAndContinue(items, index, transferred + 1, failed),
        error -> {
          if (item.isInventory() && isAmountAlreadyReached(error)) {
            // nothing left to book — treat as done so the entry does not block the queue
            dropAndContinue(items, index, transferred + 1, failed);
            return;
          }
          if (debug) {
            Log.e(TAG, "sendNext: " + item + " failed: " + error);
          }
          failed.add(item.getProductName());
          sendNext(items, index + 1, transferred, failed);
        }
    );
  }

  private void dropAndContinue(
      List<PendingStockCount> items,
      int index,
      int transferred,
      List<String> failed
  ) {
    repository.deletePendingStockCount(
        items.get(index).getId(),
        () -> sendNext(items, index + 1, transferred, failed)
    );
  }

  private void finishSync(int transferred, List<String> failed) {
    isSyncingLive.setValue(false);
    loadFromDatabase();
    if (failed.isEmpty()) {
      showSnackbar(new SnackbarMessage(getResources().getQuantityString(
          R.plurals.msg_pending_stock_counts_transferred, transferred, transferred
      )));
    } else {
      showSnackbar(new SnackbarMessage(getString(
          R.string.msg_pending_stock_counts_partly_transferred,
          String.valueOf(transferred),
          String.valueOf(failed.size())
      )));
    }
  }

  /** Reads grocy's error_message out of the response body. */
  private boolean isAmountAlreadyReached(VolleyError error) {
    if (error == null || error.networkResponse == null || error.networkResponse.data == null) {
      return false;
    }
    try {
      String responseBody = new String(error.networkResponse.data, StandardCharsets.UTF_8);
      JSONObject json = new JSONObject(responseBody);
      String message = json.optString("error_message", "");
      return message.contains(ERROR_AMOUNT_EQUALS);
    } catch (JSONException e) {
      return false;
    }
  }

  public void deleteAll() {
    repository.deleteAllPendingStockCounts(() -> {
      loadFromDatabase();
      showMessage(R.string.msg_pending_stock_counts_discarded);
    });
  }

  public void delete(PendingStockCount item) {
    repository.deletePendingStockCount(item.getId(), this::loadFromDatabase);
  }

  @Override
  protected void onCleared() {
    sharedPrefs.unregisterOnSharedPreferenceChangeListener(prefsListener);
    dlHelper.destroy();
    super.onCleared();
  }
}
