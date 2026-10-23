package com.boxzero.app.ui;

import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.boxzero.app.R;
import com.boxzero.app.core.Prefs;

public class SettingsActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);
        setTitle("设置");

        EditText input = findViewById(R.id.configInput);
        input.setText(Prefs.configUrl(this));

        Button save = findViewById(R.id.btnSave);
        save.setOnClickListener(v -> {
            String url = input.getText().toString().trim();
            Prefs.configUrl(this, url);
            Toast.makeText(this, url.isEmpty() ? "已切换为内置演示源" : "已保存", Toast.LENGTH_SHORT).show();
            finish();
        });
    }
}
