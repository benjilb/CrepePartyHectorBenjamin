package com.example.crepeparty_hector_benjamin;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.widget.Button;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

public class AccueilActivity extends AppCompatActivity {

    private TextView compteur;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_accueil);

        compteur = findViewById(R.id.textCompteur);
        Button jouerBtn = findViewById(R.id.buttonJouer);

        actualiserCompteur();
        jouerBtn.setOnClickListener(v -> {
            Intent intent = new Intent(AccueilActivity.this, MainActivity.class);
            startActivity(intent);
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        actualiserCompteur();
    }

    private void actualiserCompteur() {
        SharedPreferences prefs = getSharedPreferences("crepe_prefs", MODE_PRIVATE);
        int total = prefs.getInt("games_played", 0);
        compteur.setText("Parties jouées : " + total);
    }
}
