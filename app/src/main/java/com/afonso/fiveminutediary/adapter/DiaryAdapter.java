package com.afonso.fiveminutediary.adapter;

import android.app.AlertDialog;
import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.afonso.fiveminutediary.R;
import com.afonso.fiveminutediary.data.DiaryEntry;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class DiaryAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    private static final int TYPE_HEADER = 0;
    private static final int TYPE_ENTRY = 1;

    private List<Object> items = new ArrayList<>(); // Mixed list of headers and entries
    private Context context;
    private OnEntryClickListener listener;

    public interface OnEntryClickListener {
        void onEntryClick(DiaryEntry entry);
        void onDeleteClick(DiaryEntry entry);
    }

    public DiaryAdapter(Context context, OnEntryClickListener listener) {
        this.context = context;
        this.listener = listener;
    }

    public void setEntries(List<DiaryEntry> entries) {
        items.clear();

        if (entries.isEmpty()) {
            notifyDataSetChanged();
            return;
        }

        // Group entries by month/year
        String currentMonthYear = "";
        SimpleDateFormat monthYearFormat = new SimpleDateFormat("MMMM yyyy", Locale.getDefault());

        for (DiaryEntry entry : entries) {
            String entryMonthYear = monthYearFormat.format(new Date(entry.getTimestamp()));

            if (!entryMonthYear.equals(currentMonthYear)) {
                // Add month header
                items.add(entryMonthYear);
                currentMonthYear = entryMonthYear;
            }

            // Add entry
            items.add(entry);
        }

        notifyDataSetChanged();
    }

    @Override
    public int getItemViewType(int position) {
        return items.get(position) instanceof String ? TYPE_HEADER : TYPE_ENTRY;
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        if (viewType == TYPE_HEADER) {
            View view = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_month_header, parent, false);
            return new HeaderViewHolder(view);
        } else {
            View view = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_diary_entry, parent, false);
            return new EntryViewHolder(view);
        }
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        if (holder instanceof HeaderViewHolder) {
            String monthYear = (String) items.get(position);
            ((HeaderViewHolder) holder).bind(monthYear);
        } else if (holder instanceof EntryViewHolder) {
            DiaryEntry entry = (DiaryEntry) items.get(position);
            ((EntryViewHolder) holder).bind(entry);
        }
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    // Header ViewHolder
    class HeaderViewHolder extends RecyclerView.ViewHolder {
        TextView monthYearText;

        HeaderViewHolder(View view) {
            super(view);
            monthYearText = view.findViewById(R.id.monthYearText);
        }

        void bind(String monthYear) {
            monthYearText.setText(monthYear);
        }
    }

    // Entry ViewHolder
    class EntryViewHolder extends RecyclerView.ViewHolder {
        TextView dayNumber;
        TextView monthShort;
        TextView previewText;
        TextView metaText;
        ImageButton deleteButton;

        EntryViewHolder(View view) {
            super(view);
            dayNumber = view.findViewById(R.id.dayNumber);
            monthShort = view.findViewById(R.id.monthShort);
            previewText = view.findViewById(R.id.previewText);
            metaText = view.findViewById(R.id.metaText);
            deleteButton = view.findViewById(R.id.deleteEntryButton);
        }

        void bind(DiaryEntry entry) {
            Calendar cal = Calendar.getInstance();
            cal.setTimeInMillis(entry.getTimestamp());

            // Day number
            dayNumber.setText(String.valueOf(cal.get(Calendar.DAY_OF_MONTH)));

            // Month short (Jan, Fev, etc)
            SimpleDateFormat monthFormat = new SimpleDateFormat("MMM", Locale.getDefault());
            monthShort.setText(monthFormat.format(new Date(entry.getTimestamp())));

            // Preview text (first 80 characters)
            String preview = entry.getText();
            if (preview.length() > 80) {
                preview = preview.substring(0, 80) + "...";
            }
            previewText.setText(preview);

            // Word count
            int words = entry.getText().trim().split("\\s+").length;
            metaText.setText(words + (words == 1 ? " palavra" : " palavras"));

            // Click listeners
            itemView.setOnClickListener(v -> listener.onEntryClick(entry));
            deleteButton.setOnClickListener(v -> showDeleteConfirmation(entry));
        }

        private void showDeleteConfirmation(DiaryEntry entry) {
            new AlertDialog.Builder(context)
                    .setTitle("Eliminar entrada")
                    .setMessage("Tens a certeza?")
                    .setPositiveButton("Eliminar", (dialog, which) -> {
                        listener.onDeleteClick(entry);
                        Toast.makeText(context, "Entrada eliminada", Toast.LENGTH_SHORT).show();
                    })
                    .setNegativeButton("Cancelar", null)
                    .show();
        }
    }
}