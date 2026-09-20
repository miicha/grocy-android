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
import android.os.Bundle;
import android.util.Log;
import androidx.annotation.NonNull;
import androidx.annotation.StringRes;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;
import androidx.lifecycle.ViewModelProvider;
import androidx.preference.PreferenceManager;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import org.json.JSONException;
import org.json.JSONObject;
import xyz.zedler.patrick.grocy.Constants;
import xyz.zedler.patrick.grocy.Constants.ARGUMENT;
import xyz.zedler.patrick.grocy.Constants.PREF;
import xyz.zedler.patrick.grocy.Constants.SETTINGS.STOCK;
import xyz.zedler.patrick.grocy.Constants.SETTINGS_DEFAULT;
import xyz.zedler.patrick.grocy.R;
import xyz.zedler.patrick.grocy.api.GrocyApi;
import xyz.zedler.patrick.grocy.form.FormDataInventory;
import xyz.zedler.patrick.grocy.fragment.InventoryFragmentArgs;
import xyz.zedler.patrick.grocy.fragment.bottomSheetDialog.DateBottomSheet;
import xyz.zedler.patrick.grocy.fragment.bottomSheetDialog.InputProductBottomSheet;
import xyz.zedler.patrick.grocy.fragment.bottomSheetDialog.LocationsBottomSheet;
import xyz.zedler.patrick.grocy.fragment.bottomSheetDialog.QuantityUnitsBottomSheet;
import xyz.zedler.patrick.grocy.fragment.bottomSheetDialog.QuickModeConfirmBottomSheet;
import xyz.zedler.patrick.grocy.fragment.bottomSheetDialog.StoresBottomSheet;
import xyz.zedler.patrick.grocy.helper.DownloadHelper;
import xyz.zedler.patrick.grocy.helper.DownloadHelper.OnObjectResponseListener;
import xyz.zedler.patrick.grocy.model.Event;
import xyz.zedler.patrick.grocy.model.InfoFullscreen;
import xyz.zedler.patrick.grocy.model.Location;
import xyz.zedler.patrick.grocy.model.Product;
import xyz.zedler.patrick.grocy.model.ProductBarcode;
import xyz.zedler.patrick.grocy.model.ProductDetails;
import xyz.zedler.patrick.grocy.model.QuantityUnit;
import xyz.zedler.patrick.grocy.model.QuantityUnitConversionResolved;
import xyz.zedler.patrick.grocy.model.PendingStockCount;
import xyz.zedler.patrick.grocy.model.SnackbarMessage;
import xyz.zedler.patrick.grocy.model.StockItem;
import xyz.zedler.patrick.grocy.model.StockLocation;
import xyz.zedler.patrick.grocy.model.Store;
import xyz.zedler.patrick.grocy.repository.InventoryRepository;
import xyz.zedler.patrick.grocy.util.ArrayUtil;
import xyz.zedler.patrick.grocy.util.DateUtil;
import xyz.zedler.patrick.grocy.util.GrocycodeUtil;
import xyz.zedler.patrick.grocy.util.GrocycodeUtil.Grocycode;
import xyz.zedler.patrick.grocy.util.LocationHierarchyUtil;
import xyz.zedler.patrick.grocy.util.NumUtil;
import xyz.zedler.patrick.grocy.util.PrefsUtil;
import xyz.zedler.patrick.grocy.util.QuantityUnitConversionUtil;
import xyz.zedler.patrick.grocy.util.VersionUtil;

public class InventoryViewModel extends BaseViewModel {

  private static final String TAG = InventoryViewModel.class.getSimpleName();
  private final SharedPreferences sharedPrefs;
  private final boolean debug;

  private final DownloadHelper dlHelper;
  private final GrocyApi grocyApi;
  private final InventoryRepository repository;
  private final FormDataInventory formData;

  private List<Product> products;
  private List<QuantityUnitConversionResolved> unitConversions;
  private List<ProductBarcode> barcodes;
  private List<Store> stores;
  private List<Location> locations;
  private HashMap<Integer, QuantityUnit> quantityUnitHashMap;
  // FORK (offline inventory): cached stock and locations, so the form can be filled without server
  private HashMap<Integer, StockItem> stockItemHashMap;
  private HashMap<Integer, Location> locationHashMap;
  private List<StockLocation> stockLocations;

  private final MutableLiveData<Boolean> isLoadingLive;
  private final MutableLiveData<InfoFullscreen> infoFullscreenLive;
  private final MutableLiveData<Boolean> quickModeEnabled;

  private Runnable queueEmptyAction;
  private boolean productWillBeFilled;
  // FORK (offline inventory): set when the form was filled from the cache, which means the
  // booking has to go into the local queue even if connectivity looks fine again
  private boolean productDetailsFromCache;
  private final int maxDecimalPlacesAmount;
  private final int decimalPlacesPriceInput;

  public InventoryViewModel(@NonNull Application application, InventoryFragmentArgs args) {
    super(application);

    sharedPrefs = PreferenceManager.getDefaultSharedPreferences(getApplication());
    debug = PrefsUtil.isDebuggingEnabled(sharedPrefs);
    maxDecimalPlacesAmount = sharedPrefs.getInt(
        STOCK.DECIMAL_PLACES_AMOUNT,
        SETTINGS_DEFAULT.STOCK.DECIMAL_PLACES_AMOUNT
    );
    decimalPlacesPriceInput = sharedPrefs.getInt(
        STOCK.DECIMAL_PLACES_PRICES_INPUT,
        SETTINGS_DEFAULT.STOCK.DECIMAL_PLACES_PRICES_INPUT
    );

    isLoadingLive = new MutableLiveData<>(false);
    dlHelper = new DownloadHelper(getApplication(), TAG, isLoadingLive::setValue, getOfflineLive());
    grocyApi = new GrocyApi(getApplication());
    repository = new InventoryRepository(application);
    formData = new FormDataInventory(application, sharedPrefs, args);

    infoFullscreenLive = new MutableLiveData<>();
    boolean quickModeStart;
    if (!args.getCloseWhenFinished()) {
      quickModeStart = sharedPrefs.getBoolean(
          Constants.PREF.QUICK_MODE_ACTIVE_INVENTORY,
          false
      );
    } else {
      quickModeStart = false;
    }
    quickModeEnabled = new MutableLiveData<>(quickModeStart);

    barcodes = new ArrayList<>();
  }

  public FormDataInventory getFormData() {
    return formData;
  }

  public void loadFromDatabase(boolean downloadAfterLoading) {
    repository.loadFromDatabase(data -> {
      this.products = data.getProducts();
      this.barcodes = data.getBarcodes();
      this.quantityUnitHashMap = ArrayUtil.getQuantityUnitsHashMap(data.getQuantityUnits());
      this.unitConversions = data.getQuantityUnitConversionsResolved();
      this.stores = data.getStores();
      this.locations = data.getLocations();
      this.stockItemHashMap = ArrayUtil.getStockItemHashMap(data.getStockItems());
      this.locationHashMap = ArrayUtil.getLocationsHashMap(data.getLocations());
      this.stockLocations = data.getStockLocations();
      formData.getProductsLive().setValue(Product.getActiveAndStockEnabledProductsOnly(products));
      if (downloadAfterLoading) {
        downloadData(false);
      } else {
        if (queueEmptyAction != null) {
          queueEmptyAction.run();
          queueEmptyAction = null;
        }
      }
    }, error -> onError(error, TAG));
  }

  public void downloadData(boolean forceUpdate) {
    if (isOfflineModeActive()) { // skip downloading
      isLoadingLive.setValue(false);
      // FORK (offline inventory): still run a pending action, otherwise a product passed in via
      // arguments never gets filled in while offline
      if (queueEmptyAction != null) {
        queueEmptyAction.run();
        queueEmptyAction = null;
      }
      return;
    }
    dlHelper.updateData(
        updated -> {
          if (updated) {
            loadFromDatabase(false);
          } else {
            if (queueEmptyAction != null) {
              queueEmptyAction.run();
              queueEmptyAction = null;
            }
          }
        },
        error -> onError(error, TAG),
        forceUpdate,
        false,
        Product.class,
        QuantityUnit.class,
        QuantityUnitConversionResolved.class,
        ProductBarcode.class,
        Store.class,
        Location.class,
        // FORK (offline inventory): keep cached amounts fresh, the offline form reads them
        StockItem.class
    );
  }

  public void setProduct(int productId, ProductBarcode barcode) {
    productDetailsFromCache = false;
    OnObjectResponseListener<ProductDetails> listener = productDetails -> {
      Product updatedProduct = productDetails.getProduct();
      formData.getProductDetailsLive().setValue(productDetails);
      formData.getProductNameLive().setValue(updatedProduct.getName());

      // quantity unit
      HashMap<QuantityUnit, Double> unitFactors = QuantityUnitConversionUtil.getUnitFactors(
          quantityUnitHashMap,
          unitConversions,
          updatedProduct,
          VersionUtil.isGrocyServerMin400(sharedPrefs)
      );
      formData.getQuantityUnitsFactorsLive().setValue(unitFactors);
      QuantityUnit stock = quantityUnitHashMap.get(updatedProduct.getQuIdStockInt());
      formData.getQuantityUnitLive().setValue(stock);
      formData.getQuantityUnitStockLive().setValue(stock);

      // amount
      boolean isTareWeightEnabled = formData.isTareWeightEnabled();
      if (!isTareWeightEnabled && !isQuickModeEnabled()) {
        formData.getAmountLive().setValue(NumUtil.trimAmount(productDetails.getStockAmount(), maxDecimalPlacesAmount));
      }

      // purchased date
      if (formData.getPurchasedDateEnabled()) {
        formData.getPurchasedDateLive().setValue(DateUtil.getDateStringToday());
      }

      // due days
      if (isFeatureEnabled(PREF.FEATURE_STOCK_BBD_TRACKING)) {
        int dueDays = productDetails.getProduct().getDefaultDueDaysInt();
        if (dueDays < 0) {
          formData.getDueDateLive().setValue(Constants.DATE.NEVER_OVERDUE);
        } else if (dueDays == 0) {
          formData.getDueDateLive().setValue(null);
        } else {
          formData.getDueDateLive()
              .setValue(DateUtil.getTodayWithDaysAdded(dueDays));
        }
      }

      // price
      if (isFeatureEnabled(PREF.FEATURE_STOCK_PRICE_TRACKING)) {
        String lastPrice = productDetails.getLastPrice();
        if (lastPrice != null && !lastPrice.isEmpty()) {
          lastPrice = NumUtil.trimPrice(NumUtil.toDouble(lastPrice), decimalPlacesPriceInput);
        }
        formData.getPriceLive().setValue(lastPrice);
      }

      // store
      String storeId = productDetails.getDefaultShoppingLocationId();
      Store store = NumUtil.isStringInt(storeId) ? getStore(Integer.parseInt(storeId)) : null;
      formData.getStoreLive().setValue(store);
      formData.getShowStoreSection().setValue(store != null || !stores.isEmpty());

      // location
      if (isFeatureEnabled(PREF.FEATURE_STOCK_LOCATION_TRACKING)) {
        formData.getLocationLive().setValue(productDetails.getLocation());
      }

      // stock label type
      if (isFeatureEnabled(PREF.FEATURE_LABEL_PRINTER)) {
        formData.getPrintLabelTypeLive()
            .setValue(productDetails.getProduct().getDefaultStockLabelTypeInt());
      }

      // note
      if (barcode != null && barcode.getNote() != null) {
        formData.getNoteLive().setValue(barcode.getNote());
      }

      formData.isFormValid();
        if (isQuickModeEnabled()) {
            sendEvent(Event.FOCUS_INVALID_VIEWS);
        }
      warnIfStoredAtMultipleLocations(updatedProduct.getId());
    };

    // FORK (offline inventory): fill the form from the Room cache instead of asking the server,
    // either because offline mode is on or because the request failed out in the field.
    if (isOfflineModeActive()) {
      if (!setProductFromCache(productId, listener)) {
        showMessageAndContinueScanning(getString(R.string.error_no_product_details));
      }
      return;
    }
    ProductDetails.getProductDetails(
        dlHelper,
        productId,
        listener,
        error -> {
          if (!setProductFromCache(productId, listener)) {
            showMessageAndContinueScanning(getString(R.string.error_no_product_details));
          }
        }
    ).perform(dlHelper.getUuid());
  }

  /**
   * FORK (offline inventory): an inventory booking sets the product's total amount across all
   * locations — grocy has no per-location inventory. Counting a product that also lies elsewhere
   * would wipe the stock at those other locations, so warn about it and point at consume, which
   * does take a location.
   */
  private void warnIfStoredAtMultipleLocations(int productId) {
    if (stockLocations == null) {
      return;
    }
    int locationCount = 0;
    for (StockLocation stockLocation : stockLocations) {
      if (stockLocation.getProductId() == productId) {
        locationCount++;
      }
    }
    if (locationCount > 1) {
      showMessage(getString(
          R.string.msg_inventory_multiple_locations, String.valueOf(locationCount)
      ));
    }
  }

  /**
   * FORK (offline inventory): assembles the product details from cached data. Returns false when
   * the product is not in the cache, in which case the caller reports the usual error.
   */
  private boolean setProductFromCache(int productId, OnObjectResponseListener<ProductDetails> l) {
    Product product = Product.getProductFromId(products, productId);
    if (product == null) {
      return false;
    }
    StockItem stockItem = stockItemHashMap != null ? stockItemHashMap.get(productId) : null;
    Location location = locationHashMap != null
        ? locationHashMap.get(product.getLocationIdInt()) : null;
    QuantityUnit quStock = quantityUnitHashMap != null
        ? quantityUnitHashMap.get(product.getQuIdStockInt()) : null;
    QuantityUnit quPurchase = quantityUnitHashMap != null
        ? quantityUnitHashMap.get(product.getQuIdPurchaseInt()) : null;
    QuantityUnit quConsume = quantityUnitHashMap != null
        ? quantityUnitHashMap.get(product.getQuIdConsumeInt()) : null;
    QuantityUnit quPrice = quantityUnitHashMap != null
        ? quantityUnitHashMap.get(product.getQuIdPriceInt()) : null;
    // last price is deliberately left out: when "price" is absent from the request body the
    // server falls back to the product's last price itself, which is the same result
    productDetailsFromCache = true;
    l.onResponse(ProductDetails.fromCache(
        product, stockItem, location, quStock, quPurchase, quConsume, quPrice, null
    ));
    return true;
  }

  public void onBarcodeRecognized(String barcode) {
    if (formData.getProductDetailsLive().getValue() != null) {
      if (ProductBarcode.getFromBarcode(barcodes, barcode) == null) {
        formData.getBarcodeLive().setValue(barcode);
      } else {
        showMessage(R.string.msg_clear_form_first);
      }
      return;
    }
    Product product = null;
    Grocycode grocycode = GrocycodeUtil.getGrocycode(barcode);
    if (grocycode != null && grocycode.isProduct()) {
      product = Product.getProductFromId(products, grocycode.getObjectId());
      if (product == null) {
        showMessageAndContinueScanning(R.string.msg_not_found);
        return;
      }
    } else if (grocycode != null) {
      showMessageAndContinueScanning(R.string.error_wrong_grocycode_type);
      return;
    }
    ProductBarcode productBarcode = null;
    if (product == null) {
      productBarcode = ProductBarcode.getFromBarcode(barcodes, barcode);
      product = productBarcode != null
          ? Product.getProductFromId(products, productBarcode.getProductIdInt()) : null;
    }
    if (product != null) {
      setProduct(product.getId(), productBarcode);
    } else {
      Bundle bundle = new Bundle();
      bundle.putString(ARGUMENT.BARCODE, barcode);
      sendEvent(Event.CHOOSE_PRODUCT, bundle);
    }
  }

  public void checkProductInput() {
    formData.isProductNameValid();
    String input = formData.getProductNameLive().getValue();
      if (input == null || input.isEmpty()) {
          return;
      }
    Product product = Product.getProductFromName(products, input);

    Grocycode grocycode = GrocycodeUtil.getGrocycode(input.trim());
    if (grocycode != null && grocycode.isProduct()) {
      product = Product.getProductFromId(products, grocycode.getObjectId());
      if (product == null) {
        showMessageAndContinueScanning(R.string.msg_not_found);
        return;
      }
    } else if (grocycode != null) {
      showMessageAndContinueScanning(R.string.error_wrong_grocycode_type);
      return;
    }
    if (product == null) {
      ProductBarcode barcode = null;
      for (ProductBarcode code : barcodes) {
        if (code.getBarcode().equals(input.trim())) {
          barcode = code;
          product = Product.getProductFromId(products, code.getProductIdInt());
        }
      }
      if (product != null) {
        setProduct(product.getId(), barcode);
        return;
      }
    }

    ProductDetails currentProductDetails = formData.getProductDetailsLive().getValue();
    Product currentProduct = currentProductDetails != null
        ? currentProductDetails.getProduct() : null;
    if (currentProduct != null && product != null && currentProduct.getId() == product.getId()) {
      return;
    }

    if (product != null) {
      setProduct(product.getId(), null);
    } else {
      showInputProductBottomSheet(input);
    }
  }

  public void addBarcodeToExistingProduct(String barcode) {
    formData.getBarcodeLive().setValue(barcode);
    formData.getProductNameLive().setValue(null);
  }

  public void inventoryProduct() {
    if (!formData.isFormValid()) {
      showMessage(R.string.error_missing_information);
      return;
    }
    // FORK (offline inventory): a scanned barcode cannot be uploaded without a server. Drop it
    // and book the count anyway — the barcode can be added later when back online.
    if (formData.getBarcodeLive().getValue() != null) {
      if (isOfflineModeActive() || productDetailsFromCache) {
        formData.getBarcodeLive().setValue(null);
        showMessage(R.string.msg_offline_barcode_discarded);
      } else {
        uploadProductBarcode(this::inventoryProduct, false);
        return;
      }
    }

    Product product = formData.getProductDetailsLive().getValue().getProduct();
    JSONObject body = formData.getFilledJSONObject();

    if (isOfflineModeActive() || productDetailsFromCache) {
      storeInventoryOffline(product, body);
      return;
    }
    dlHelper.postWithArray(
        grocyApi.inventoryProduct(product.getId()),
        body,
        response -> {
          // UNDO OPTION
          String transactionId = null;
          double amountDiff = 0;
          try {
            transactionId = response.getJSONObject(0)
                .getString("transaction_id");
            for (int i = 0; i < response.length(); i++) {
              amountDiff += response.getJSONObject(i).getDouble("amount");
            }
          } catch (JSONException e) {
            if (debug)
              Log.e(TAG, "inventoryProduct: " + e);
          }
          if (debug)
            Log.i(TAG, "inventoryProduct: transaction successful");

          SnackbarMessage snackbarMessage = new SnackbarMessage(
              formData.getTransactionSuccessMsg(amountDiff)
          );
          if (transactionId != null) {
            String transId = transactionId;
            snackbarMessage.setAction(
                getString(R.string.action_undo),
                v -> undoTransaction(transId)
            );
            snackbarMessage.setDurationSecs(sharedPrefs.getInt(
                    Constants.SETTINGS.BEHAVIOR.MESSAGE_DURATION,
                    Constants.SETTINGS_DEFAULT.BEHAVIOR.MESSAGE_DURATION));
          }
          showSnackbar(snackbarMessage);
          sendEvent(Event.TRANSACTION_SUCCESS);
        },
        error -> {
          showNetworkErrorMessage(error);
            if (debug) {
                Log.i(TAG, "inventoryProduct: " + error);
            }
        }
    );
  }

  /**
   * FORK (offline inventory): true while bookings must not go to the server — either the user
   * switched offline mode on deliberately or the last refresh failed.
   */
  public boolean isOfflineModeActive() {
    return isOffline() || sharedPrefs.getBoolean(
        Constants.SETTINGS.BEHAVIOR.OFFLINE_MODE,
        Constants.SETTINGS_DEFAULT.BEHAVIOR.OFFLINE_MODE
    );
  }

  /**
   * FORK (offline inventory): puts the booking into the local queue. The body is frozen as it
   * would have been posted; because the inventory endpoint takes an absolute new_amount, sending
   * it later yields the same end state regardless of order.
   */
  private void storeInventoryOffline(Product product, JSONObject body) {
    PendingStockCount count = new PendingStockCount();
    count.setAction(PendingStockCount.ACTION_INVENTORY);
    count.setProductId(product.getId());
    count.setProductName(product.getName());
    count.setAmount(body.optString("new_amount", null));
    QuantityUnit quStock = quantityUnitHashMap != null
        ? quantityUnitHashMap.get(product.getQuIdStockInt()) : null;
    count.setQuantityUnitName(quStock != null ? quStock.getName() : null);
    count.setLocationName(
        LocationHierarchyUtil.getPath(formData.getLocationLive().getValue(), locations)
    );
    count.setBody(body.toString());
    count.setCreatedAt(System.currentTimeMillis());
    repository.insertPendingStockCount(
        count,
        id -> {
          if (debug) {
            Log.i(TAG, "storeInventoryOffline: queued " + count);
          }
          showSnackbar(new SnackbarMessage(getString(R.string.msg_offline_count_stored)));
          sendEvent(Event.TRANSACTION_SUCCESS);
        },
        () -> showMessage(R.string.error_undefined)
    );
  }

  private void undoTransaction(String transactionId) {
    dlHelper.post(
        grocyApi.undoStockTransaction(transactionId),
        success -> {
          showMessage(getString(R.string.msg_undone_transaction));
            if (debug) {
                Log.i(TAG, "undoTransaction: undone");
            }
        },
        this::showNetworkErrorMessage
    );
  }

  public void uploadProductBarcode(Runnable onSuccess, boolean withoutForm) {
    ProductBarcode productBarcode;
    if (withoutForm) {
      productBarcode = formData.fillProductBarcodeWithoutForm();
    } else {
      productBarcode = formData.fillProductBarcode();
    }
    JSONObject body = productBarcode.getJsonFromProductBarcode(debug, TAG);
    ProductBarcode.addProductBarcode(dlHelper, body, () -> {
      formData.getBarcodeLive().setValue(null);
      barcodes.add(productBarcode); // add to list so it will be found on next scan without reload
        if (onSuccess != null) {
            onSuccess.run();
        }
    }, error -> showMessage(R.string.error_failed_barcode_upload)).perform(dlHelper.getUuid());
  }

  private Store getStore(int id) {
    for (Store store : stores) {
        if (store.getId() == id) {
            return store;
        }
    }
    return null;
  }

  public void showInputProductBottomSheet(@NonNull String input) {
    Bundle bundle = new Bundle();
    bundle.putString(Constants.ARGUMENT.PRODUCT_INPUT, input);
    showBottomSheet(new InputProductBottomSheet(), bundle);
  }

  public void showQuantityUnitsBottomSheet(boolean hasFocus) {
      if (!hasFocus) {
          return;
      }
    HashMap<QuantityUnit, Double> unitsFactors = getFormData()
        .getQuantityUnitsFactorsLive().getValue();
    Bundle bundle = new Bundle();
    bundle.putParcelableArrayList(
        Constants.ARGUMENT.QUANTITY_UNITS,
        unitsFactors != null ? new ArrayList<>(unitsFactors.keySet()) : null
    );
    QuantityUnit quantityUnit = formData.getQuantityUnitLive().getValue();
    bundle.putInt(ARGUMENT.SELECTED_ID, quantityUnit != null ? quantityUnit.getId() : -1);
    showBottomSheet(new QuantityUnitsBottomSheet(), bundle);
  }

  public void showPurchasedDateBottomSheet() {
      if (!formData.isProductNameValid()) {
          return;
      }
    Bundle bundle = new Bundle();
    bundle.putString(Constants.ARGUMENT.DEFAULT_DAYS_FROM_NOW, String.valueOf(0));
    bundle.putString(
        Constants.ARGUMENT.SELECTED_DATE,
        formData.getPurchasedDateLive().getValue()
    );
    bundle.putInt(DateBottomSheet.DATE_TYPE, DateBottomSheet.PURCHASED_DATE);
    showBottomSheet(new DateBottomSheet(), bundle);
  }

  public void showDueDateBottomSheet(boolean hasFocus) {
      if (!hasFocus || !formData.isProductNameValid()) {
          return;
      }
    Product product = formData.getProductDetailsLive().getValue().getProduct();
    Bundle bundle = new Bundle();
    bundle.putString(
        Constants.ARGUMENT.DEFAULT_DAYS_FROM_NOW,
        String.valueOf(product.getDefaultDueDaysInt())
    );
    bundle.putString(
        Constants.ARGUMENT.SELECTED_DATE,
        formData.getDueDateLive().getValue()
    );
    bundle.putInt(DateBottomSheet.DATE_TYPE, DateBottomSheet.DUE_DATE);
    showBottomSheet(new DateBottomSheet(), bundle);
  }

  public void showStoresBottomSheet() {
      if (!formData.isProductNameValid() || stores == null || stores.isEmpty()) {
          return;
      }
    Bundle bundle = new Bundle();
    bundle.putParcelableArrayList(Constants.ARGUMENT.STORES, new ArrayList<>(stores));
    bundle.putInt(
        Constants.ARGUMENT.SELECTED_ID,
        formData.getStoreLive().getValue() != null
            ? formData.getStoreLive().getValue().getId()
            : -1
    );
    bundle.putBoolean(ARGUMENT.DISPLAY_EMPTY_OPTION, true);
    showBottomSheet(new StoresBottomSheet(), bundle);
  }

  public void showLocationsBottomSheet() {
      if (!formData.isProductNameValid()) {
          return;
      }
    Bundle bundle = new Bundle();
    bundle.putParcelableArrayList(Constants.ARGUMENT.LOCATIONS, new ArrayList<>(locations));
    bundle.putInt(
        Constants.ARGUMENT.SELECTED_ID,
        formData.getLocationLive().getValue() != null
            ? formData.getLocationLive().getValue().getId()
            : -1
    );
    showBottomSheet(new LocationsBottomSheet(), bundle);
  }

  public void showConfirmationBottomSheet() {
    Bundle bundle = new Bundle();
    bundle.putString(Constants.ARGUMENT.TEXT, formData.getConfirmationText());
    showBottomSheet(new QuickModeConfirmBottomSheet(), bundle);
  }

  private void showMessageAndContinueScanning(String msg) {
    formData.clearForm();
    showMessage(msg);
    sendEvent(Event.CONTINUE_SCANNING);
  }

  private void showMessageAndContinueScanning(@StringRes int msg) {
    showMessageAndContinueScanning(getString(msg));
  }

  @NonNull
  public MutableLiveData<Boolean> getIsLoadingLive() {
    return isLoadingLive;
  }

  @NonNull
  public MutableLiveData<InfoFullscreen> getInfoFullscreenLive() {
    return infoFullscreenLive;
  }

  public void setQueueEmptyAction(Runnable queueEmptyAction) {
    this.queueEmptyAction = queueEmptyAction;
  }

  public void setProductWillBeFilled(boolean productWillBeFilled) {
    this.productWillBeFilled = productWillBeFilled;
  }

  public boolean isProductWillBeFilled() {
    return productWillBeFilled;
  }

  public boolean isQuickModeEnabled() {
      if (quickModeEnabled.getValue() == null) {
          return false;
      }
    return quickModeEnabled.getValue();
  }

  public MutableLiveData<Boolean> getQuickModeEnabled() {
    return quickModeEnabled;
  }

  public boolean toggleQuickModeEnabled() {
    quickModeEnabled.setValue(!isQuickModeEnabled());
    sendEvent(isQuickModeEnabled() ? Event.QUICK_MODE_ENABLED : Event.QUICK_MODE_DISABLED);
    sharedPrefs.edit()
        .putBoolean(Constants.PREF.QUICK_MODE_ACTIVE_INVENTORY, isQuickModeEnabled())
        .apply();
    return true;
  }

  public boolean isFeatureEnabled(String pref) {
      if (pref == null) {
          return true;
      }
    return sharedPrefs.getBoolean(pref, true);
  }

  @Override
  protected void onCleared() {
    dlHelper.destroy();
    super.onCleared();
  }

  public static class InventoryViewModelFactory implements ViewModelProvider.Factory {

    private final Application application;
    private final InventoryFragmentArgs args;

    public InventoryViewModelFactory(Application application, InventoryFragmentArgs args) {
      this.application = application;
      this.args = args;
    }

    @NonNull
    @Override
    @SuppressWarnings("unchecked")
    public <T extends ViewModel> T create(@NonNull Class<T> modelClass) {
      return (T) new InventoryViewModel(application, args);
    }
  }
}
