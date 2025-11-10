package com.example.crepeparty_hector_benjamin;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.GridView;
import android.widget.ImageView;
import android.widget.TextView;
import android.Manifest;
import android.content.pm.PackageManager;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

public class AccueilActivity extends AppCompatActivity {

    private TextView compteur;
    private ImageView skinCourantImg;

    private static final String PREFS = "crepe_prefs";
    private static final String KEY_SKIN = "selected_skin_res";

    private final int[] SKINS = new int[]{
            R.drawable.car_01,
            R.drawable.car_02,
            R.drawable.car_03,
            R.drawable.car_04,
            R.drawable.car_05,
            R.drawable.car_07,
            R.drawable.car_08,
            R.drawable.car_09,
            R.drawable.car_10
    };

    private static final int REQ_REC_AUDIO = 1001;
    private MicVisualizerView micViz;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_accueil);
        micViz = findViewById(R.id.micViz);
        compteur = findViewById(R.id.textCompteur);
        skinCourantImg = findViewById(R.id.imageSkinCourant);
        Button jouerBtn = findViewById(R.id.buttonJouerChrono);
        Button jouerEndlessBtn   = findViewById(R.id.buttonJouerEndless); // nouveau

        Button skinBtn  = findViewById(R.id.buttonChoisirSkin);

        actualiserCompteur();
        actualiserVignetteSkin();

        jouerBtn.setOnClickListener(v -> {
            Intent i = new Intent(this, MainActivity.class);
            i.putExtra("mode", "TIMER");
            startActivity(i);
        });

        jouerEndlessBtn.setOnClickListener(v -> {
            Intent i = new Intent(this, MainActivity.class);
            i.putExtra("mode", "ENDLESS");
            startActivity(i);
        });

        skinBtn.setOnClickListener(v -> ouvrirSkinDialog());
    }

    @Override
    protected void onResume() {
        super.onResume();
        actualiserCompteur();
        actualiserVignetteSkin();
        ensureMic();
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (micViz != null) micViz.stop();
    }

    private void actualiserCompteur() {
        SharedPreferences prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        int total = prefs.getInt("games_played", 0);
        compteur.setText("Parties jouées : " + total);
    }

    private void actualiserVignetteSkin() {
        SharedPreferences prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        int def = R.drawable.car_01;
        int sel = prefs.getInt(KEY_SKIN, def);
        skinCourantImg.setImageResource(sel);
    }

    private void ensureMic() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.RECORD_AUDIO},
                    REQ_REC_AUDIO);
        } else {
            if (micViz != null) micViz.start();
        }
    }

    private void ouvrirSkinDialog() {
        View content = LayoutInflater.from(this).inflate(R.layout.dialog_choose_skin, null, false);
        GridView grid = content.findViewById(R.id.gridSkins);
        grid.setAdapter(new SkinAdapter());

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("Choisir une voiture")
                .setView(content)
                .create();

        grid.setOnItemClickListener((parent, view, position, id) -> {
            int resId = SKINS[position];
            getSharedPreferences(PREFS, MODE_PRIVATE).edit()
                    .putInt(KEY_SKIN, resId)
                    .apply();
            actualiserVignetteSkin();
            dialog.dismiss();
        });

        dialog.show();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] perms, int[] grants) {
        super.onRequestPermissionsResult(requestCode, perms, grants);
        if (requestCode == REQ_REC_AUDIO) {
            if (grants.length > 0 && grants[0] == PackageManager.PERMISSION_GRANTED) {
                if (micViz != null) micViz.start();
            } else {
                if (micViz != null) micViz.stop();
            }
        }
    }

    /** Adapter minimal pour afficher les PNG du dossier drawable en grille. */
    private class SkinAdapter extends BaseAdapter {
        @Override public int getCount() { return SKINS.length; }
        @Override public Object getItem(int i) { return SKINS[i]; }
        @Override public long getItemId(int i) { return i; }

        @Override
        public View getView(int position, View convertView, android.view.ViewGroup parent) {
            View v = convertView;
            if (v == null) {
                v = getLayoutInflater().inflate(R.layout.item_skin, parent, false);
            }
            ImageView img = v.findViewById(R.id.imgSkin);
            img.setImageResource(SKINS[position]);
            return v;
        }
    }
}
