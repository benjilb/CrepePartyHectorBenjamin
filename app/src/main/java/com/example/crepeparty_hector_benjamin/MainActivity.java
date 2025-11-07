package com.example.crepeparty_hector_benjamin;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.Window;
import android.view.WindowManager;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

public class MainActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        this.requestWindowFeature(Window.FEATURE_NO_TITLE);

        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,
                WindowManager.LayoutParams.FLAG_FULLSCREEN);

        EdgeToEdge.enable(this);

        // petite démo de SharedPreferences déjà présente chez toi
        SharedPreferences sharedPref = this.getPreferences(Context.MODE_PRIVATE);
        int valeur_y = sharedPref.getInt("valeur_y", 0);
        valeur_y = (valeur_y + 100) % 400;
        SharedPreferences.Editor editor = sharedPref.edit();
        editor.putInt("valeur_y", valeur_y);
        editor.apply();

        // >>> Utilise maintenant un layout avec GameView + bouton
        setContentView(R.layout.activity_main);

        GameView gv = findViewById(R.id.gameView);
        findViewById(R.id.btnSkin).setOnClickListener(v -> openSkinDialog(gv));

        // Optionnel : insets
        ViewCompat.setOnApplyWindowInsetsListener(gv, (view, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            view.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });
    }

    /** Ouvre un sélecteur simple de skins et applique sur GameView */
    private void openSkinDialog(GameView gv) {
        final int[] SKINS = new int[]{
                R.drawable.car_01,    // mets tes fichiers PNG dans res/drawable/
                R.drawable.car_02,
                R.drawable.car_03,
                R.drawable.car_04,
                R.drawable.car_05,
                R.drawable.car_07,
                R.drawable.car_08,
                R.drawable.car_09,
                R.drawable.car_10
        };
        final String[] NAMES = {"Rouge", "Blanche", "Bleue", "Jaune", "Verte",
                "Cyan", "Rose", "Orange", "Noire"};

        new AlertDialog.Builder(this)
                .setTitle("Choisir une voiture")
                .setItems(NAMES, (d, which) -> gv.setSkin(SKINS[which]))
                .show();
    }
}
