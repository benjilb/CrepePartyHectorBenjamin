package com.example.crepeparty_hector_benjamin;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.res.ResourcesCompat;
import android.content.SharedPreferences;
import android.view.View;

public class DefaiteActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_defaite);

        TextView message = findViewById(R.id.textDefaite);
        TextView tvBest  = findViewById(R.id.textBestEndlessDefaite);
        Button rejouerBtn = findViewById(R.id.buttonRejouer);
        Button retourBtn  = findViewById(R.id.buttonRetour);

        try { message.setTypeface(ResourcesCompat.getFont(this, R.font.pixel)); } catch (Exception ignored) {}

        String mode = getIntent().getStringExtra("mode"); // "Chrono" ou "Infini"
        int meters = getIntent().getIntExtra("score_meters", -1);

        if ("ENDLESS".equals(mode) && meters >= 0) {
            message.setText("Vous avez perdu\nDistance : " + meters + " m");

            SharedPreferences prefs = getSharedPreferences("crepe_prefs", MODE_PRIVATE);
            int best = prefs.getInt("best_endless", 0);
            if (meters > best) {
                best = meters;
                prefs.edit().putInt("best_endless", best).apply();
            }
            tvBest.setText("Meilleur score en mode infini : " + best + " m");
            tvBest.setVisibility(View.VISIBLE);
        } else {
            message.setText("Vous avez perdu");
            tvBest.setVisibility(View.GONE);
        }

        rejouerBtn.setOnClickListener(v -> {
            Intent intent = new Intent(DefaiteActivity.this, MainActivity.class);
            intent.putExtra("mode", "ENDLESS".equals(mode) ? "ENDLESS" : "TIMER");
            startActivity(intent);
            finish();
        });

        retourBtn.setOnClickListener(v -> {
            startActivity(new Intent(DefaiteActivity.this, AccueilActivity.class));
            finish();
        });
    }
}
