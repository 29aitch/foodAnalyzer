package com.example.foodanalyzer;


import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.provider.MediaStore;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.google.ai.client.generativeai.GenerativeModel;
import com.google.ai.client.generativeai.java.GenerativeModelFutures;
import com.google.ai.client.generativeai.type.Content;
import com.google.ai.client.generativeai.type.GenerateContentResponse;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.MoreExecutors;

import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

public class MainActivity extends AppCompatActivity {
    private static final int CAMERA_PERMISSION_REQUEST_CODE = 100;
    private static final int CAMERA_CAPTURE_REQUEST_CODE = 200;
    private static final int GALLERY_PICK_REQUEST_CODE = 300;

    private ImageView foodImageView;
    private Button captureButton;
    private Button uploadButton;
    private TextView resultTextView;
    private Bitmap selectedImage;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Initialize UI components
        foodImageView = findViewById(R.id.foodImageView);
        captureButton = findViewById(R.id.captureButton);
        uploadButton = findViewById(R.id.uploadButton);
        resultTextView = findViewById(R.id.resultTextView);

        // Set up button click listeners
        captureButton.setOnClickListener(v -> checkCameraPermissionAndCapture());
        uploadButton.setOnClickListener(v -> openGallery());
    }

    private void checkCameraPermissionAndCapture() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.CAMERA},
                    CAMERA_PERMISSION_REQUEST_CODE);
        } else {
            launchCamera();
        }
    }

    private void launchCamera() {
        Intent cameraIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        startActivityForResult(cameraIntent, CAMERA_CAPTURE_REQUEST_CODE);
    }

    private void openGallery() {
        Intent galleryIntent = new Intent(Intent.ACTION_PICK,
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
        startActivityForResult(galleryIntent, GALLERY_PICK_REQUEST_CODE);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == CAMERA_PERMISSION_REQUEST_CODE) {
            if (grantResults.length > 0 &&
                    grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                launchCamera();
            } else {
                Toast.makeText(this, "Camera permission denied",
                        Toast.LENGTH_SHORT).show();
            }
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode,
                                    @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (resultCode == RESULT_OK) {
            if (requestCode == CAMERA_CAPTURE_REQUEST_CODE && data != null) {
                selectedImage = (Bitmap) data.getExtras().get("data");
                foodImageView.setImageBitmap(selectedImage);
                analyzeFood(selectedImage);
            } else if (requestCode == GALLERY_PICK_REQUEST_CODE && data != null) {
                try {
                    selectedImage = MediaStore.Images.Media.getBitmap(
                            this.getContentResolver(), data.getData());
                    foodImageView.setImageBitmap(selectedImage);
                    analyzeFood(selectedImage);
                } catch (Exception e) {
                    Toast.makeText(this, "Error loading image",
                            Toast.LENGTH_SHORT).show();
                }
            }
        }
    }

    private void analyzeFood(Bitmap image) {
        // Initialize Gemini model
        GenerativeModel gm = new GenerativeModel(
                "gemini-1.5-flash",
                BuildConfig.GM_API_KEY
        );
        GenerativeModelFutures model = GenerativeModelFutures.from(gm);

        // Prepare content for analysis
        Content content = new Content.Builder()
                .addText("Analyze this food image. Please provide: " +
                        "1. Name of the food\n" +
                        "2. List of ingredients\n" +
                        "3. A simple recipe to prepare this dish")
                .addImage(image)
                .build();

        // Execute Gemini analysis
        Executor executor = Executors.newSingleThreadExecutor();
        ListenableFuture<GenerateContentResponse> response =
                model.generateContent(content);

        Futures.addCallback(
                response,
                new FutureCallback<GenerateContentResponse>() {
                    @Override
                    public void onSuccess(GenerateContentResponse result) {
                        runOnUiThread(() -> {
                            String resultText = result.getText();
                            resultTextView.setText(resultText);
                        });
                    }

                    @Override
                    public void onFailure(Throwable t) {
                        runOnUiThread(() -> {
                            Toast.makeText(MainActivity.this,
                                    "Food analysis failed: " + t.getMessage(),
                                    Toast.LENGTH_LONG).show();
                        });
                    }
                },
                MoreExecutors.directExecutor()
        );
    }
}