package com.hydrainnit.refract;

import android.accessibilityservice.AccessibilityService;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;

import java.util.ArrayList;
import java.util.List;

/**
 * Refract's accessibility switch, turned on by the user in the TV's Accessibility settings. It does
 * two things, both only when asked from Refract: put the TV to sleep (Power Off, Sleep Timer) on TVs
 * where device admin is switched off, and close apps for Clean by pressing Force stop on each app's
 * info page. It reads nothing else on screen.
 */
public class PowerService extends AccessibilityService {
    static PowerService instance;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final List<String> queue = new ArrayList<>();
    private Runnable onDone;
    private int step;

    @Override
    protected void onServiceConnected() {
        instance = this;
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent e) {
    }

    @Override
    public void onInterrupt() {
    }

    @Override
    public void onDestroy() {
        instance = null;
        super.onDestroy();
    }

    /** Screen off / sleep. Returns false when the service isn't on. */
    static boolean sleep() {
        PowerService s = instance;
        return s != null && android.os.Build.VERSION.SDK_INT >= 28 && s.performGlobalAction(GLOBAL_ACTION_LOCK_SCREEN);
    }

    /** Force-stops each app via its App info page, then returns to Refract and runs {@code done}. */
    static void forceStop(Context c, List<String> pkgs, Runnable done) {
        PowerService s = instance;
        if (s == null) {
            done.run();
            return;
        }
        s.queue.clear();
        s.queue.addAll(pkgs);
        s.onDone = done;
        s.next();
    }

    private void next() {
        if (queue.isEmpty()) {
            Intent home = new Intent(this, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
            startActivity(home);
            if (onDone != null) handler.postDelayed(onDone, 300);
            onDone = null;
            return;
        }
        String pkg = queue.remove(0);
        try {
            startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + pkg))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_NO_HISTORY));
        } catch (Exception e) {
            next();
            return;
        }
        step = 0;
        handler.postDelayed(this::pressForceStop, 900);
    }

    /** Presses "Force stop", then the confirmation "OK"; gives up on an app after a few tries. */
    private void pressForceStop() {
        AccessibilityNodeInfo root = getRootInActiveWindow();
        boolean clicked = false;
        if (root != null) {
            String[] labels = step == 0 ? new String[]{"Force stop", "FORCE STOP", "Force Stop"} : new String[]{"OK", "Ok"};
            for (String l : labels) {
                List<AccessibilityNodeInfo> found = root.findAccessibilityNodeInfosByText(l);
                for (AccessibilityNodeInfo n : found) {
                    AccessibilityNodeInfo t = n;
                    while (t != null && !t.isClickable()) t = t.getParent();
                    if (t != null && t.isEnabled() && t.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
                        clicked = true;
                        break;
                    }
                }
                if (clicked) break;
            }
        }
        if (clicked && step == 0) {
            step = 1;
            handler.postDelayed(this::pressForceStop, 600);
            return;
        }
        if (!clicked && step == 0 && retries++ < 3) {
            handler.postDelayed(this::pressForceStop, 500);
            return;
        }
        retries = 0;
        performGlobalAction(GLOBAL_ACTION_BACK);
        handler.postDelayed(this::next, 400);
    }

    private int retries;
}
