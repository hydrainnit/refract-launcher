package com.hydrainnit.refract;

import android.service.dreams.DreamService;

/**
 * "Refract Ambient" as the TV's own screensaver (Settings -> Device Preferences -> Screen saver):
 * the same Ambient screen Home shows when idle. Any key ends it, as with every screensaver.
 */
public class RefractDream extends DreamService {
    Ambient ambient;

    @Override
    public void onAttachedToWindow() {
        super.onAttachedToWindow();
        setInteractive(false);
        setFullscreen(true);
        // Dimmed where the TV supports it, when the user asked for safe dimming.
        setScreenBright(Ambient.prefs(this).getInt("dim", 0) == 0);
        ambient = new Ambient(this);
        setContentView(ambient);
    }

    @Override
    public void onDreamingStarted() {
        super.onDreamingStarted();
        if (ambient != null) ambient.start();
    }

    @Override
    public void onDreamingStopped() {
        super.onDreamingStopped();
        if (ambient != null) ambient.stop();
    }
}
