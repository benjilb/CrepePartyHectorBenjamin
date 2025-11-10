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

        // Police pixel si dispo
        try {
            message.setTypeface(ResourcesCompat.getFont(this, R.font.pixel));
        } catch (Exception ignored) {}

        // Texte centré avec distance si ENDLESS
        String mode = getIntent().getStringExtra("mode"); // "TIMER" ou "ENDLESS"
        int meters = getIntent().getIntExtra("score_meters", -1);
        if ("ENDLESS".equals(mode) && meters >= 0) {
            message.setText("Vous avez perdu\nDistance : " + meters + " m");
        } else {
            message.setText("Vous avez perdu");
        }

        // Rejouer dans le même mode qu'on vient de jouer
        rejouerBtn.setOnClickListener(v -> {
            Intent intent = new Intent(DefaiteActivity.this, MainActivity.class);
            if ("ENDLESS".equals(mode)) {
                intent.putExtra("mode", "ENDLESS");
            } else {
                intent.putExtra("mode", "TIMER");
            }
            startActivity(intent);
            finish();
        });

        retourBtn.setOnClickListener(v -> {
            startActivity(new Intent(DefaiteActivity.this, AccueilActivity.class));
            finish();
        });
    }
}
