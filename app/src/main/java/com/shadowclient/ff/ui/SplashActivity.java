package com.shadowclient.ff.ui;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.view.animation.DecelerateInterpolator;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.shadowclient.ff.R;
import com.shadowclient.ff.auth.KeyManager;
import com.shadowclient.ff.util.Prefs;

public class SplashActivity extends AppCompatActivity {

    private static final long SPLASH_MS = 1450L;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        setContentView(R.layout.activity_splash);

        ImageView logo = findViewById(R.id.imgLogo);
        TextView brand = findViewById(R.id.tvBrand);
        TextView tag = findViewById(R.id.tvTagline);
        View progress = findViewById(R.id.progress);

        logo.setScaleX(0.72f);
        logo.setScaleY(0.72f);
        logo.setAlpha(0f);
        brand.setAlpha(0f);
        tag.setAlpha(0f);
        progress.setAlpha(0f);

        logo.animate().scaleX(1f).scaleY(1f).alpha(1f)
                .setDuration(760).setInterpolator(new DecelerateInterpolator()).start();
        brand.animate().alpha(1f).setStartDelay(240).setDuration(520).start();
        tag.animate().alpha(0.8f).setStartDelay(380).setDuration(520).start();
        progress.animate().alpha(1f).setStartDelay(300).setDuration(400).start();

        new Handler(Looper.getMainLooper()).postDelayed(this::route, SPLASH_MS);
    }

    private void route() {
        // "keep me signed in" switched off -> the saved session does not survive a restart
        if (!Prefs.flag(this, Prefs.KEY_REMEMBER, true)) {
            KeyManager.signOut(this);
        }

        Intent next = new Intent(this,
                KeyManager.isActive(this) ? DashboardActivity.class : LoginActivity.class);
        startActivity(next);
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
        finish();
    }
}
