package com.example.zonebrowser;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.PowerManager;

/** Owns the user-started import lifetime; all WebView work stays on the main thread. */
public final class ScanService extends Service {
    private static final String CHANNEL = "collection-import";
    private static final String STOP = "stop-import";
    private static final int ID = 41;
    private static ScanService instance;
    private static Runnable startWork, cancelWork;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private PowerManager.WakeLock wake;
    private int count;
    private boolean stopping, committing;
    private final Runnable deadline = this::cancel;

    static void begin(Context context, Runnable work, Runnable cancel) {
        startWork = work; cancelWork = cancel;
        if (instance != null && !instance.stopping) { instance.runWork(); return; }
        try { context.startForegroundService(new Intent(context, ScanService.class)); }
        catch (RuntimeException e) { startWork = null; cancelWork = null; throw e; }
    }
    static void end(Context context) {
        startWork = null; cancelWork = null;
        if (instance != null) instance.stopping = true;
        context.stopService(new Intent(context, ScanService.class));
    }
    static void committing() {
        if (instance == null) return;
        instance.committing = true; cancelWork = null;
        instance.handler.removeCallbacks(instance.deadline);
        instance.getSystemService(NotificationManager.class).notify(ID, instance.notification());
    }
    static void progress(int cards) {
        if (instance == null || instance.count == cards) return;
        instance.count = cards;
        if (Build.VERSION.SDK_INT < 33 || instance.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS)
                == android.content.pm.PackageManager.PERMISSION_GRANTED)
            instance.getSystemService(NotificationManager.class).notify(ID, instance.notification());
    }
    private Notification notification() {
        PendingIntent open = PendingIntent.getActivity(this, 0,
                new Intent(this, ZoneSyncActivity.class).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_CLEAR_TOP),
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        PendingIntent stop = PendingIntent.getService(this, 1, new Intent(this, ScanService.class).setAction(STOP),
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        Notification.Builder builder = new Notification.Builder(this, CHANNEL)
                .setSmallIcon(android.R.drawable.stat_sys_download)
                .setContentTitle("TCG Dex · Actualizando colección")
                .setContentText(committing ? "Guardando y verificando cantidades…" : count + " cartas distintas · toca para volver")
                .setContentIntent(open).setOngoing(true).setOnlyAlertOnce(true);
        if (!committing) builder.addAction(new Notification.Action.Builder(null, "Detener", stop).build());
        return builder.build();
    }
    @Override public void onCreate() {
        super.onCreate(); instance = this;
        getSystemService(NotificationManager.class).createNotificationChannel(
                new NotificationChannel(CHANNEL, "Lectura de colección", NotificationManager.IMPORTANCE_LOW));
    }
    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && STOP.equals(intent.getAction())) { cancel(); return START_NOT_STICKY; }
        if (startWork == null) { stopSelf(); return START_NOT_STICKY; }
        if (Build.VERSION.SDK_INT >= 29) startForeground(ID, notification(), ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC);
        else startForeground(ID, notification());
        runWork();
        return START_NOT_STICKY;
    }
    private void runWork() {
        if (wake == null) {
            wake = getSystemService(PowerManager.class).newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, getPackageName() + ":collection");

        }
        if (wake.isHeld()) wake.release();
        wake.acquire(16 * 60 * 1000L);
        count = 0; committing = false;
        handler.removeCallbacks(deadline); handler.postDelayed(deadline, 15 * 60 * 1000L);
        Runnable work = startWork; startWork = null; work.run();
    }
    private void cancel() {
        if (committing) return; // Room finishes its atomic transaction before releasing the service.
        stopping = true;
        Runnable cancel = cancelWork; cancelWork = null;
        if (cancel != null) cancel.run();
        stopSelf();
    }
    @Override public void onTimeout(int startId, int fgsType) { cancel(); }
    @Override public void onDestroy() {
        handler.removeCallbacksAndMessages(null);
        if (wake != null && wake.isHeld()) wake.release();
        wake = null; instance = null;
        // A new start may already be queued while the previous service is being destroyed.
        if (startWork == null) cancelWork = null;
        super.onDestroy();
    }
    @Override public IBinder onBind(Intent intent) { return null; }
}
