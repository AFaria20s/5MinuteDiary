package com.afonso.fiveminutediary.viewmodel;

import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;

import com.afonso.fiveminutediary.R;
import com.afonso.fiveminutediary.data.DataRepository;
import com.afonso.fiveminutediary.data.DiaryEntry;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class DetailActivity extends BaseActivity {

    private static final int PICK_IMAGE_REQUEST = 1;

    private DataRepository repo;
    private DiaryEntry entry;
    private ImageView headerImage;
    private TextView detailDate;
    private TextView wordCount;
    private TextView detailContent;
    private ImageButton backButton;
    private ImageButton deleteDetailButton;
    private ImageButton changeImageButton;

    private boolean hasCustomImage = false;

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
        loadData();
        setupListeners();
    }

    private void initViews() {
        headerImage = findViewById(R.id.headerImage);
        detailDate = findViewById(R.id.detailDate);
        wordCount = findViewById(R.id.wordCount);
        detailContent = findViewById(R.id.detailContent);
        backButton = findViewById(R.id.backButton);
        deleteDetailButton = findViewById(R.id.deleteDetailButton);
        changeImageButton = findViewById(R.id.changeImageButton);
    }

    private void loadData() {
        // Use current locale for date formatting
        SimpleDateFormat sdf = new SimpleDateFormat(getString(R.string.detail_date_format), Locale.getDefault());
        detailDate.setText(sdf.format(new Date(entry.getTimestamp())));

        // Count words
        int words = entry.getText().trim().split("\\s+").length;
        String wordLabel = words == 1 ? getString(R.string.word_singular) : getString(R.string.words_plural);
        wordCount.setText(String.format(getString(R.string.word_count_format), words, wordLabel));

        // Load text with formatting
        String formatting = entry.getFormatting();
        if (formatting != null && !formatting.isEmpty()) {
            android.text.SpannableString formatted =
                    com.afonso.fiveminutediary.data.TextFormattingSerializer.deserializeFormatting(
                            entry.getText(), formatting
                    );
            detailContent.setText(formatted);
        } else {
            detailContent.setText(entry.getText());
        }

        // Load image
        if (entry.getImagePath() != null) {
            Bitmap bmp = BitmapFactory.decodeFile(entry.getImagePath());
            if (bmp != null) {
                headerImage.setImageBitmap(bmp);
                hasCustomImage = true;
                return;
            }
        }
        setDefaultHeaderImage();
    }

    private void setDefaultHeaderImage() {
        Drawable gradient = getResources().getDrawable(R.drawable.detail_header_gradient, null);
        headerImage.setImageDrawable(gradient);
        hasCustomImage = false;
    }

    private void setupListeners() {
        backButton.setOnClickListener(v -> finish());

        deleteDetailButton.setOnClickListener(v -> showDeleteConfirmation());

        changeImageButton.setOnClickListener(v -> showImageOptions());
    }

    private void showImageOptions() {
        String[] options = hasCustomImage ?
                new String[]{
                        getString(R.string.choose_new_image_option),
                        getString(R.string.back_to_default_color)
                } :
                new String[]{getString(R.string.choose_image_option)};

        new AlertDialog.Builder(this)
                .setTitle(getString(R.string.image_background_title))
                .setItems(options, (dialog, which) -> {
                    if (hasCustomImage && which == 1) {
                        // Delete custom image file
                        if (entry.getImagePath() != null) {
                            File f = new File(entry.getImagePath());
                            if (f.exists()) f.delete();
                        }
                        entry.setImagePath(null);
                        repo.updateEntry(entry, null);
                        setDefaultHeaderImage();
                    } else {
                        openImagePicker();
                    }
                })
                .setNegativeButton(getString(R.string.cancel), null)
                .show();
    }

    private void openImagePicker() {
        Intent intent = new Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
        intent.setType("image/*");
        startActivityForResult(intent, PICK_IMAGE_REQUEST);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == PICK_IMAGE_REQUEST && resultCode == RESULT_OK && data != null) {
            Uri imageUri = data.getData();
            if (imageUri != null) {
                try {
                    Bitmap selectedBitmap = MediaStore.Images.Media.getBitmap(getContentResolver(), imageUri);

                    // Save image to internal storage
                    String filename = "entry_" + entry.getId() + ".png";
                    File file = new File(getFilesDir(), filename);
                    FileOutputStream out = new FileOutputStream(file);
                    selectedBitmap.compress(Bitmap.CompressFormat.PNG, 100, out);
                    out.close();

                    // Update entry with image path
                    entry.setImagePath(file.getAbsolutePath());
                    repo.updateEntry(entry, task -> {
                        runOnUiThread(() -> {
                            headerImage.setImageBitmap(selectedBitmap);
                            hasCustomImage = true;
                            Toast.makeText(this, getString(R.string.image_updated), Toast.LENGTH_SHORT).show();
                        });
                    });

                } catch (IOException e) {
                    Toast.makeText(this, getString(R.string.image_load_error), Toast.LENGTH_SHORT).show();
                }
            }
        }
    }

    private void showDeleteConfirmation() {
        new AlertDialog.Builder(this)
                .setTitle(getString(R.string.delete_entry_confirmation_title))
                .setMessage(getString(R.string.delete_entry_confirmation_message))
                .setPositiveButton(getString(R.string.delete_button), (dialog, which) -> {
                    repo.deleteEntry(entry, task -> {
                        runOnUiThread(() -> {
                            Toast.makeText(this, getString(R.string.entry_deleted_success), Toast.LENGTH_SHORT).show();
                            finish();
                        });
                    });
                })
                .setNegativeButton(getString(R.string.cancel), null)
                .show();
    }
}