package com.shadowclient.ff.ui;

import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.SwitchCompat;

import com.shadowclient.ff.Config;
import com.shadowclient.ff.R;
import com.shadowclient.ff.auth.KeyManager;
import com.shadowclient.ff.util.Prefs;
import com.shadowclient.ff.util.Ui;

public class LoginActivity extends AppCompatActivity {

    private EditText etKey;
    private Button btnActivate;
    private TextView tvStatus;
    private SwitchCompat swRemember;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        setContentView(R.layout.activity_login);

        etKey = findViewById(R.id.etKey);
        btnActivate = findViewById(R.id.btnActivate);
        tvStatus = findViewById(R.id.tvStatus);
        swRemember = findViewById(R.id.swRemember);

        swRemember.setChecked(Prefs.flag(this, Prefs.KEY_REMEMBER, true));
        swRemember.setOnCheckedChangeListener((v, checked) ->
                Prefs.setFlag(this, Prefs.KEY_REMEMBER, checked));

        findViewById(R.id.btnTelegram).setOnClickListener(v -> Ui.openUrl(this, Config.TELEGRAM_URL));

        btnActivate.setOnClickListener(v -> attempt());

        etKey.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                attempt();
                return true;
            }
            return false;
        });

        if (KeyManager.isActive(this)) {
            go();
        }
    }

    private void attempt() {
        String key = etKey.getText().toString().trim();
        if (TextUtils.isEmpty(key)) {
            status(getString(R.string.login_empty), false);
            return;
        }
        setBusy(true);
        status(getString(R.string.login_verifying), true);

        KeyManager.activate(this, key, (code, label, expiry) -> {
            setBusy(false);
            switch (code) {
                case KeyManager.ERR_OK:
                    Prefs.setFlag(this, Prefs.KEY_REMEMBER, swRemember.isChecked());
                    status(getString(R.string.login_success), true);
                    go();
                    break;
                case KeyManager.ERR_EXPIRED:
                    status(getString(R.string.login_expired), false);
                    break;
                case KeyManager.ERR_DEVICE:
                    status(getString(R.string.login_device_limit), false);
                    break;
                case KeyManager.ERR_OFFLINE:
                    status(getString(R.string.login_offline), false);
                    break;
                case KeyManager.ERR_EMPTY:
                    status(getString(R.string.login_empty), false);
                    break;
                default:
                    status(getString(R.string.login_invalid), false);
                    break;
            }
        });
    }

    private void go() {
        startActivity(new Intent(this, DashboardActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP));
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
        finish();
    }

    private void setBusy(boolean busy) {
        btnActivate.setEnabled(!busy);
        btnActivate.setAlpha(busy ? 0.6f : 1f);
        btnActivate.setText(busy ? R.string.login_verifying : R.string.login_button);
    }

    private void status(String msg, boolean ok) {
        tvStatus.setText(msg);
        tvStatus.setTextColor(Color.parseColor(ok ? "#C98BFF" : "#FF5A6E"));
        tvStatus.setVisibility(View.VISIBLE);
    }
}
