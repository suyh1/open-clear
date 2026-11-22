package family.chumen;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import java.util.Locale;
import java.util.function.Consumer;

/** A foreground-only fix. Location data stays in memory for this trip. */
final class TripLocation {
    private final Context context;
    private final LocationManager manager;
    private final Consumer<String> update;
    private final Handler timer = new Handler(Looper.getMainLooper());
    private Location best;
    private LocationListener listener;

    TripLocation(Context context, Consumer<String> update) {
        this.context = context;
        this.manager = context.getSystemService(LocationManager.class);
        this.update = update;
    }

    private boolean granted(String permission) {
        return context.checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED;
    }
    boolean allowed() {
        return granted(Manifest.permission.ACCESS_COARSE_LOCATION) || granted(Manifest.permission.ACCESS_FINE_LOCATION);
    }
    private static boolean fresh(Location fix) {
        if (fix == null || !fix.hasAccuracy() || fix.getAccuracy() <= 0) return false;
        long age = SystemClock.elapsedRealtimeNanos() - fix.getElapsedRealtimeNanos();
        return age >= 0 && age <= 120_000_000_000L;
    }
    private boolean precise() {
        return fresh(best) && granted(Manifest.permission.ACCESS_FINE_LOCATION) && best.getAccuracy() <= 150;
    }
    String nearby() {
        if (!allowed() || !fresh(best)) return "";
        // City-scale search bias. The exact start is shared only with the map app.
        return String.format(Locale.ROOT, "&location=%.2f,%.2f", best.getLongitude(), best.getLatitude());
    }
    String origin() {
        if (!precise()) return "";
        return "&slat=" + best.getLatitude() + "&slon=" + best.getLongitude() + "&sname=" + Places.encode("当前位置");
    }
    Location current() { return allowed() && fresh(best) ? new Location(best) : null; }
    void start() {
        stop();
        if (!allowed()) { best = null; update.accept("使用当前位置"); return; }
        if (!fresh(best)) best = null;
        if (manager == null) { update.accept("暂时无法定位 · 重试"); return; }
        LocationListener request = new LocationListener() {
            @Override public void onLocationChanged(Location fix) {
                if (listener != this || !accept(fix)) return;
                showFix();
                if (precise()) stop();
            }
            @Override public void onProviderDisabled(String name) {}
            @Override public void onProviderEnabled(String name) {}
            @Override public void onStatusChanged(String name, int status, Bundle extras) {}
        };
        listener = request;
        boolean listening = false;
        for (String provider : new String[]{LocationManager.NETWORK_PROVIDER, LocationManager.GPS_PROVIDER}) {
            try {
                if (!manager.isProviderEnabled(provider)) continue;
                accept(manager.getLastKnownLocation(provider));
                manager.requestLocationUpdates(provider, 1000L, 0, request, Looper.getMainLooper());
                listening = true;
            } catch (IllegalArgumentException | SecurityException unavailable) {
                // Some devices offer only one provider or only approximate location.
            }
        }
        if (fresh(best)) showFix();
        else update.accept(listening ? "正在定位…" : "定位不可用 · 重试");
        if (!listening || precise()) { stop(); return; }
        timer.postDelayed(() -> {
            stop();
            if (!fresh(best)) update.accept("定位超时 · 重试");
        }, 12000);
    }
    private boolean accept(Location fix) {
        if (!fresh(fix)) return false;
        if (fresh(best) && best.getAccuracy() < fix.getAccuracy()) return false;
        best = fix;
        return true;
    }
    private void showFix() {
        update.accept(precise() ? "起点 · 当前位置" : "已取得大致位置");
    }
    void stop() {
        timer.removeCallbacksAndMessages(null);
        if (listener != null && manager != null) {
            try { manager.removeUpdates(listener); } catch (SecurityException revoked) { best = null; }
        }
        listener = null;
    }
}
