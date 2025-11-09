package com.example.crepeparty_hector_benjamin;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.res.ResourcesCompat;

public class DefaiteActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_defaite);

        TextView message = findViewById(R.id.textDefaite);
        Button rejouerBtn = findViewById(R.id.buttonRejouer);
        Button retourBtn  = findViewById(R.id.buttonRetour);

        message.setText("Vous avez perdu");
        message.setTypeface(ResourcesCompat.getFont(this, R.font.pixel));

        rejouerBtn.setOnClickListener(v -> {
            Intent intent = new Intent(DefaiteActivity.this, MainActivity.class);
            startActivity(intent);
            finish();
        });

        retourBtn.setOnClickListener(v -> {
            Intent intent = new Intent(DefaiteActivity.this, AccueilActivity.class);
            startActivity(intent);
            finish();
        });
    }
}
