package com.afonso.fiveminutediary.adapter;

import android.app.AlertDialog;
import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.afonso.fiveminutediary.R;
import com.afonso.fiveminutediary.data.DiaryEntry;
import com.afonso.fiveminutediary.utils.DiaryUtils;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class DiaryAdapter extends RecyclerView.Adapter<DiaryAdapter.ViewHolder> {

    private List<DiaryEntry> entries = new ArrayList<>();
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
        this.entries = entries;
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_diary_entry, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        DiaryEntry entry = entries.get(position);

        // Format date
        SimpleDateFormat sdf = new SimpleDateFormat("d MMM yyyy", new Locale("pt", "PT"));
        SimpleDateFormat dayFormat = new SimpleDateFormat("EEEE", new Locale("pt", "PT"));

        holder.dateText.setText(sdf.format(new Date(entry.getTimestamp())));
        holder.dayText.setText(dayFormat.format(new Date(entry.getTimestamp())));

        // Preview text (first 100 characters)
        String preview = entry.getText();
        if (preview.length() > 100) {
            preview = preview.substring(0, 100) + "...";
        }
        holder.previewText.setText(preview);

        // Set image based on entry
        holder.entryImage.setImageResource(DiaryUtils.getImageForEntry(entry));

        // Click listeners
        holder.itemView.setOnClickListener(v -> listener.onEntryClick(entry));

        holder.deleteButton.setOnClickListener(v -> showDeleteConfirmation(entry));
    }

    @Override
    public int getItemCount() {
        return entries.size();
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

    static class ViewHolder extends RecyclerView.ViewHolder {
        ImageView entryImage;
        TextView dateText;
        TextView dayText;
        TextView previewText;
        ImageButton deleteButton;

        ViewHolder(View view) {
            super(view);
            entryImage = view.findViewById(R.id.entryImage);
            dateText = view.findViewById(R.id.dateText);
            dayText = view.findViewById(R.id.dayText);
            previewText = view.findViewById(R.id.previewText);
            deleteButton = view.findViewById(R.id.deleteEntryButton);
        }
    }
}