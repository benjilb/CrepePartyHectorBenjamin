package com.example.crepeparty_hector_benjamin;

import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Typeface;
import android.os.Bundle;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

public class VictoireActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_victoire);

        TextView message = findViewById(R.id.textVictoire);
        Button retourBtn = findViewById(R.id.buttonRetour);
        ImageView imgCar = findViewById(R.id.imgCar);
        ImageView imgCup = findViewById(R.id.imgCup);

        message.setText("Vous avez gagnez felicitations !");
        try {
            Typeface pixel = getResources().getFont(R.font.pixel);
            message.setTypeface(pixel);
        } catch (Exception ignored) {}

        SharedPreferences prefs = getSharedPreferences("crepe_prefs", MODE_PRIVATE);
        int carResId = prefs.getInt("selected_skin_res", R.drawable.car_01);
        imgCar.setImageResource(carResId);

        retourBtn.setOnClickListener(v -> {
            Intent intent = new Intent(VictoireActivity.this, AccueilActivity.class);
            startActivity(intent);
            finish();
        });
    }
}
