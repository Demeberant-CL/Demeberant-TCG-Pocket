package com.example.zonebrowser;

import android.content.Intent;
import android.os.Looper;
import android.os.PowerManager;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.Shadows;
import org.robolectric.android.controller.ServiceController;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = {34})
public class ScanServiceTest {
    @Test public void commitKeepsWakeLockAndIgnoresStaleStopUntilDatabaseFinishes() throws Exception {
        ServiceController<ScanService> controller = Robolectric.buildService(ScanService.class).create();
        ScanService service = controller.get();
        AtomicInteger cancelled = new AtomicInteger();
        boolean destroyed = false;
        try {
            ScanService.begin(service, () -> {}, cancelled::incrementAndGet);
            java.lang.reflect.Field field = ScanService.class.getDeclaredField("wake");
            field.setAccessible(true);
            PowerManager.WakeLock wake = (PowerManager.WakeLock) field.get(service);
            assertTrue(wake.isHeld());
            ScanService.committing();
            service.onStartCommand(new Intent(service, ScanService.class).setAction("stop-import"), 0, 1);
            Shadows.shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMinutes(15));
            assertEquals(0, cancelled.get());
            assertFalse(Shadows.shadowOf(service).isStoppedBySelf());
            assertTrue(wake.isHeld());
            ScanService.end(service);
            controller.destroy(); destroyed = true;
            assertFalse(wake.isHeld());
        } finally {
            ScanService.end(service);
            if (!destroyed) controller.destroy();
        }
    }

    @Test public void scanStillCancelsBeforeCommit() {
        ServiceController<ScanService> controller = Robolectric.buildService(ScanService.class).create();
        ScanService service = controller.get();
        AtomicInteger cancelled = new AtomicInteger();
        try {
            ScanService.begin(service, () -> {}, cancelled::incrementAndGet);
            service.onStartCommand(new Intent(service, ScanService.class).setAction("stop-import"), 0, 1);
            assertEquals(1, cancelled.get());
            assertTrue(Shadows.shadowOf(service).isStoppedBySelf());
        } finally { ScanService.end(service); controller.destroy(); }
    }
}
