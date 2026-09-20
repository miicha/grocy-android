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

package xyz.zedler.patrick.grocy.adapter;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import java.util.ArrayList;
import java.util.List;
import xyz.zedler.patrick.grocy.R;
import xyz.zedler.patrick.grocy.databinding.RowPendingStockCountBinding;
import xyz.zedler.patrick.grocy.model.PendingStockCount;

/** FORK (offline inventory): list of bookings waiting to be sent to the server. */
public class PendingStockCountAdapter
    extends RecyclerView.Adapter<PendingStockCountAdapter.ViewHolder> {

  private final Context context;
  private final List<PendingStockCount> items;
  private final PendingStockCountAdapterListener listener;

  public PendingStockCountAdapter(
      Context context,
      List<PendingStockCount> items,
      PendingStockCountAdapterListener listener
  ) {
    this.context = context;
    this.items = new ArrayList<>(items);
    this.listener = listener;
  }

  public static class ViewHolder extends RecyclerView.ViewHolder {

    private final RowPendingStockCountBinding binding;

    public ViewHolder(RowPendingStockCountBinding binding) {
      super(binding.getRoot());
      this.binding = binding;
    }
  }

  @NonNull
  @Override
  public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
    return new ViewHolder(RowPendingStockCountBinding.inflate(
        LayoutInflater.from(parent.getContext()), parent, false
    ));
  }

  @Override
  public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
    PendingStockCount item = items.get(position);

    holder.binding.nameProduct.setText(item.getProductName());

    String amount = item.getAmount() != null ? item.getAmount() : "";
    if (item.getQuantityUnitName() != null && !item.getQuantityUnitName().isEmpty()) {
      amount = amount + " " + item.getQuantityUnitName();
    }
    holder.binding.textAmount.setText(context.getString(
        item.isInventory()
            ? R.string.label_pending_count_inventory
            : R.string.label_pending_count_consume,
        amount
    ));

    if (item.getLocationName() != null && !item.getLocationName().isEmpty()) {
      holder.binding.textLocation.setText(
          context.getString(R.string.label_pending_count_location, item.getLocationName())
      );
      holder.binding.textLocation.setVisibility(View.VISIBLE);
    } else {
      holder.binding.textLocation.setVisibility(View.GONE);
    }

    holder.binding.buttonDelete.setOnClickListener(v -> listener.onDeleteClicked(item));
  }

  @Override
  public int getItemCount() {
    return items.size();
  }

  public void updateData(List<PendingStockCount> newItems) {
    items.clear();
    items.addAll(newItems);
    notifyDataSetChanged();
  }

  public interface PendingStockCountAdapterListener {

    void onDeleteClicked(PendingStockCount pendingStockCount);
  }
}
