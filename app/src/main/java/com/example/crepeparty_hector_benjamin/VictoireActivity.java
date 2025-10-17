package com.example.crepeparty_hector_benjamin;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

public class VictoireActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_victoire);

        TextView message = findViewById(R.id.textVictoire);
        Button retourBtn = findViewById(R.id.buttonRetour);

        message.setText("Félicitations ! Vous avez gagné");

        retourBtn.setOnClickListener(v -> {
            Intent intent = new Intent(VictoireActivity.this, AccueilActivity.class);
            startActivity(intent);
            finish();
        });
    }
}
