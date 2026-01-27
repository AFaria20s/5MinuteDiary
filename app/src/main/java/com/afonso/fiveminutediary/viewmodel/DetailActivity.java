package com.afonso.fiveminutediary.viewmodel;

import android.app.AlertDialog;
import android.os.Bundle;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.afonso.fiveminutediary.R;
import com.afonso.fiveminutediary.data.DataRepository;
import com.afonso.fiveminutediary.data.DiaryEntry;
import com.afonso.fiveminutediary.utils.DiaryUtils;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class DetailActivity extends AppCompatActivity {

    private DiaryEntry entry;
    private DataRepository repo;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_detail);

        repo = DataRepository.getInstance(this);
        entry = (DiaryEntry) getIntent().getSerializableExtra("entry");

        if (entry == null) {
            finish();
            return;
        }

        initViews();
    }

    private void initViews() {
        TextView dateText = findViewById(R.id.detailDate);
        TextView contentText = findViewById(R.id.detailContent);
        TextView wordCountText = findViewById(R.id.wordCount);
        ImageView headerImage = findViewById(R.id.headerImage);
        ImageButton backButton = findViewById(R.id.backButton);
        ImageButton deleteButton = findViewById(R.id.deleteDetailButton);

        // Format date
        SimpleDateFormat sdf = new SimpleDateFormat("EEEE, d 'de' MMMM 'de' yyyy", new Locale("pt", "PT"));
        dateText.setText(sdf.format(new Date(entry.getTimestamp())));

        // Set content
        contentText.setText(entry.getText());

        // Word count
        int words = entry.getText().trim().split("\\s+").length;
        wordCountText.setText(words + " palavras");

        // Set header image based on day
        headerImage.setImageResource(DiaryUtils.getImageForEntry(entry));

        // Listeners
        backButton.setOnClickListener(v -> finish());
        deleteButton.setOnClickListener(v -> showDeleteConfirmation());
    }

    private void showDeleteConfirmation() {
        new AlertDialog.Builder(this)
                .setTitle("Eliminar entrada")
                .setMessage("Tens a certeza que queres eliminar esta entrada?")
                .setPositiveButton("Eliminar", (dialog, which) -> deleteEntry())
                .setNegativeButton("Cancelar", null)
                .show();
    }

    private void deleteEntry() {
        repo.deleteEntry(entry);
        Toast.makeText(this, "Entrada eliminada", Toast.LENGTH_SHORT).show();
        finish();
    }
}