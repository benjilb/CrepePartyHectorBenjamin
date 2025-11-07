package com.example.crepeparty_hector_benjamin;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
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

        SharedPreferences sharedPref = this.getPreferences(Context.MODE_PRIVATE);
        int valeur_y = sharedPref.getInt("valeur_y", 0);
        valeur_y = (valeur_y + 100) % 400;
        SharedPreferences.Editor editor = sharedPref.edit();
        editor.putInt("valeur_y", valeur_y);
        editor.apply();


        GameView gv = new GameView(this);
        setContentView(gv);
        SharedPreferences prefs = getSharedPreferences("crepe_prefs", MODE_PRIVATE);
        int total = prefs.getInt("games_played", 0);
        //Toast.makeText(this, "Total de parties : " + total, Toast.LENGTH_SHORT).show();





        ViewCompat.setOnApplyWindowInsetsListener(gv, (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

    }
}