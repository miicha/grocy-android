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

package xyz.zedler.patrick.grocy.model;

import androidx.annotation.NonNull;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.PrimaryKey;
import java.util.List;

/**
 * FORK (offline inventory): a stock booking that was entered while offline and still has to be
 * sent to the server.
 *
 * <p>The request body is frozen at entry time. For {@link #ACTION_INVENTORY} that is safe to
 * replay: the inventory endpoint takes an absolute {@code new_amount}, so order does not matter
 * and sending it twice yields the same end state. {@link #ACTION_CONSUME} carries a delta and is
 * therefore at-most-once — an entry is only deleted after the server confirmed it.
 */
@Entity(tableName = "pending_stock_count_table")
public class PendingStockCount {

  public final static String ACTION_INVENTORY = "inventory";
  public final static String ACTION_CONSUME = "consume";

  @PrimaryKey(autoGenerate = true)
  @ColumnInfo(name = "id")
  private int id;

  @ColumnInfo(name = "action")
  private String action;

  @ColumnInfo(name = "product_id")
  private int productId;

  @ColumnInfo(name = "product_name")
  private String productName;

  /** Display only — the booking itself carries the location inside {@link #body}. */
  @ColumnInfo(name = "location_name")
  private String locationName;

  /** Amount in the product's stock quantity unit, for display in the pending list. */
  @ColumnInfo(name = "amount")
  private String amount;

  /** Quantity unit name for display, already pluralized by the caller. */
  @ColumnInfo(name = "quantity_unit_name")
  private String quantityUnitName;

  /** The complete JSON request body as it would have been posted while online. */
  @ColumnInfo(name = "body")
  private String body;

  @ColumnInfo(name = "created_at")
  private long createdAt;

  public PendingStockCount() {
  }

  public int getId() {
    return id;
  }

  public void setId(int id) {
    this.id = id;
  }

  public String getAction() {
    return action;
  }

  public void setAction(String action) {
    this.action = action;
  }

  public boolean isInventory() {
    return ACTION_INVENTORY.equals(action);
  }

  public int getProductId() {
    return productId;
  }

  public void setProductId(int productId) {
    this.productId = productId;
  }

  public String getProductName() {
    return productName;
  }

  public void setProductName(String productName) {
    this.productName = productName;
  }

  public String getLocationName() {
    return locationName;
  }

  public void setLocationName(String locationName) {
    this.locationName = locationName;
  }

  public String getAmount() {
    return amount;
  }

  public void setAmount(String amount) {
    this.amount = amount;
  }

  public String getQuantityUnitName() {
    return quantityUnitName;
  }

  public void setQuantityUnitName(String quantityUnitName) {
    this.quantityUnitName = quantityUnitName;
  }

  public String getBody() {
    return body;
  }

  public void setBody(String body) {
    this.body = body;
  }

  public long getCreatedAt() {
    return createdAt;
  }

  public void setCreatedAt(long createdAt) {
    this.createdAt = createdAt;
  }

  public static PendingStockCount getFromId(List<PendingStockCount> counts, int id) {
    for (PendingStockCount count : counts) {
      if (count.getId() == id) {
        return count;
      }
    }
    return null;
  }

  @NonNull
  @Override
  public String toString() {
    return "PendingStockCount(" + action + ' ' + productId + ')';
  }
}
