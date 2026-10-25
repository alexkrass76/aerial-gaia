package gaia.bridge;

import android.app.ActivityManager;
import android.content.ComponentName;
import android.os.*;
import java.util.*;
import org.json.*;

/** Shell-UID helper: task stacks and fixed Wi-Fi operations; no arbitrary commands or screen capture. */
public final class TaskBridge extends Binder {
    private static final String APP = "app.orchard.tv.gaia";
    private static final String TOKEN = "app.orchard.tv.gaia.tasks.v1";
    private final DeviceBridge devices = new DeviceBridge();
    private final int uid;
    private final Object service;
    private final Class<?> api;
    private TaskBridge(int uid) throws Exception {
        this.uid = uid;
        api = Class.forName("android.app.IActivityTaskManager");
        service = Class.forName("android.app.ActivityTaskManager").getMethod("getService").invoke(null);
    }
    private List<?> tasks() throws Exception {
        Object slice = api.getMethod("getRecentTasks", int.class, int.class, int.class).invoke(service, 40, 0, 0);
        return (List<?>) slice.getClass().getMethod("getList").invoke(slice);
    }
    private boolean eligible(ActivityManager.RecentTaskInfo t) {
        ComponentName c = t.baseIntent.getComponent();
        // Only existing activity stacks; exclude our own HOME and system UI.
        return c != null && t.numActivities > 0 && t.isRunning &&
            !c.getPackageName().equals(APP) && !c.getPackageName().equals("com.android.systemui") &&
            !c.getPackageName().contains("packageinstaller");
    }
    @Override protected boolean onTransact(int code, Parcel data, Parcel reply, int flags) {
        if (Binder.getCallingUid() != uid) throw new SecurityException("Launcher UID required");
        data.enforceInterface(TOKEN);
        long identity = Binder.clearCallingIdentity();
        try {
            if (code >= 10 && code <= 13) {
                String result;
                if (code == 10) result = devices.wifiSnapshot();
                else if (code == 11) result = devices.scanWifi();
                else if (code == 12) result = devices.connectSaved(data.readInt());
                else result = devices.addAndConnect(data.readString(), data.readString(), data.readString(), data.readInt());
                reply.writeNoException(); reply.writeString(result);
                return true;
            }
            if (code == 1) {
                JSONArray rows = new JSONArray();
                for (Object item : tasks()) {
                    ActivityManager.RecentTaskInfo t = (ActivityManager.RecentTaskInfo)item;
                    if (!eligible(t)) continue;
                    rows.put(new JSONObject().put("id", t.taskId).put("package", t.baseIntent.getComponent().getPackageName()).put("closable", TaskClosePolicy.allows(t.baseIntent.getComponent().getPackageName())));
                }
                reply.writeNoException(); reply.writeString(rows.toString());
                return true;
            }
            if (code == 2) {
                int id = data.readInt();
                boolean found = false;
                for (Object item : tasks()) {
                    ActivityManager.RecentTaskInfo t = (ActivityManager.RecentTaskInfo)item;
                    if (t.taskId == id && eligible(t)) found = true;
                }
                if (!found) throw new IllegalArgumentException("Task no longer available");
                int result = (Integer)api.getMethod("startActivityFromRecents", int.class, Bundle.class).invoke(service, id, null);
                if (result < 0) throw new IllegalStateException("Task resume failed: " + result);
                reply.writeNoException(); return true;
            }
            if (code == 3) {
                int id = data.readInt();
                boolean allowed = false;
                for (Object item : tasks()) {
                    ActivityManager.RecentTaskInfo t = (ActivityManager.RecentTaskInfo)item;
                    if (t.taskId == id && eligible(t) && TaskClosePolicy.allows(t.baseIntent.getComponent().getPackageName())) allowed = true;
                }
                if (!allowed) throw new IllegalArgumentException("Task cannot be closed");
                boolean removed = (Boolean)api.getMethod("removeTask", int.class).invoke(service, id);
                if (!removed) throw new IllegalStateException("Task close failed");
                reply.writeNoException(); return true;
            }
            return false;
        } catch (Exception e) {
            reply.writeException(new IllegalStateException("Task bridge operation failed", e));
            return true;
        } finally { Binder.restoreCallingIdentity(identity); }
    }
    private static volatile IBinder launcher;
    private static Handler handler;
    private static int retryMs = 1000;
    private static java.lang.Process input;
    private static java.nio.channels.FileLock instanceLock;
    private static java.io.RandomAccessFile lockFile;

    private static void connect() {
        if (launcher != null && launcher.isBinderAlive()) return;
        try {
            IBinder next = attach();
            next.linkToDeath(() -> handler.post(() -> {
                if (launcher == next) {
                    launcher = null;
                    handler.postDelayed(TaskBridge::connect, 1000);
                }
            }), 0);
            launcher = next;
            retryMs = 1000;
            System.out.println("Aerial Gaia task bridge attached");
        } catch (Exception e) {
            launcher = null;
            System.err.println("Waiting for launcher: " + e.getClass().getSimpleName());
            handler.postDelayed(TaskBridge::connect, retryMs);
            retryMs = Math.min(30000, retryMs * 2);
        }
    }

    private static IBinder attach() throws Exception {
        // Use Binder directly: no ActivityThread, system Context, resources or graphics startup.
        IBinder packageBinder = (IBinder)Class.forName("android.os.ServiceManager")
            .getMethod("getService", String.class).invoke(null, "package");
        Object packages = Class.forName("android.content.pm.IPackageManager$Stub")
            .getMethod("asInterface", IBinder.class).invoke(null, packageBinder);
        int uid = (Integer)Class.forName("android.content.pm.IPackageManager")
            .getMethod("getPackageUid", String.class, long.class, int.class)
            .invoke(packages, APP, 0L, 0);
        if (uid < 10000) throw new IllegalStateException("Gaia launcher UID unavailable");
        Bundle extras = new Bundle(); extras.putBinder("bridge", new TaskBridge(uid));
        // app_process is not a registered Android application; acquire an external provider.
        Object am = Class.forName("android.app.ActivityManager").getMethod("getService").invoke(null);
        Class<?> amApi = Class.forName("android.app.IActivityManager");
        IBinder token = new Binder();
        Object holder = amApi.getMethod("getContentProviderExternal", String.class, int.class, IBinder.class, String.class)
            .invoke(am, APP + ".tasks", 0, token, "aerial-task-bridge");
        if (holder == null) throw new IllegalStateException("Provider unavailable");
        Object provider = holder.getClass().getField("provider").get(holder);
        android.content.AttributionSource attribution = new android.content.AttributionSource.Builder(android.os.Process.myUid())
            .setPackageName("com.android.shell").build();
        Bundle result;
        try {
            result = (Bundle)Class.forName("android.content.IContentProvider")
                .getMethod("call", android.content.AttributionSource.class, String.class, String.class, String.class, Bundle.class)
                .invoke(provider, attribution, APP + ".tasks", "attach", null, extras);
        } finally {
            amApi.getMethod("removeContentProviderExternalAsUser", String.class, IBinder.class, int.class)
                .invoke(am, APP + ".tasks", token, 0);
        }
        if (result == null || !result.getBoolean("attached")) throw new IllegalStateException("Attach failed");
        IBinder lifetime = result.getBinder("lifetime");
        if (lifetime == null) throw new IllegalStateException("Missing launcher lifetime");
        return lifetime;
    }

    private static String deviceName(String path) {
        String leaf = path.substring(path.lastIndexOf('/') + 1);
        if (!leaf.matches("event[0-9]+")) return "";
        try (java.io.BufferedReader reader = new java.io.BufferedReader(new java.io.FileReader(
                "/sys/class/input/" + leaf + "/device/name"))) {
            String name = reader.readLine();
            return name == null ? "" : name;
        } catch (Exception e) { return ""; }
    }

    public static void main(String[] args) throws Exception {
        // File lock is held for the process lifetime: a second launch cannot create another reader.
        lockFile = new java.io.RandomAccessFile("/data/local/tmp/aerial-task-bridge.lock", "rw");
        instanceLock = lockFile.getChannel().tryLock();
        if (instanceLock == null) { System.out.println("Aerial task bridge already running"); return; }
        Looper.prepareMainLooper();
        handler = new Handler(Looper.getMainLooper());
        final Map<String, Integer> homes = new HashMap<>();
        for (String arg : args) {
            int colon = arg.lastIndexOf(':');
            if (!arg.startsWith("name=") || colon <= 5) throw new IllegalArgumentException("Expected name=Input device name:decimalHomeCode");
            homes.put(arg.substring(5, colon), Integer.parseInt(arg.substring(colon + 1)));
        }
        Runtime.getRuntime().addShutdownHook(new Thread(() -> { if (input != null) input.destroy(); }));
        if (!homes.isEmpty()) {
            input = new ProcessBuilder("/system/bin/getevent", "-t").start();
            Thread reader = new Thread(() -> {
                HomeInput taps = new HomeInput();
                java.util.regex.Pattern pattern = java.util.regex.Pattern.compile(
                    "(/dev/input/event[0-9]+):\\s+0001\\s+([0-9a-f]+)\\s+([0-9a-f]+)");
                try (java.io.BufferedReader lines = new java.io.BufferedReader(new java.io.InputStreamReader(input.getInputStream()))) {
                    String line;
                    while ((line = lines.readLine()) != null) {
                        java.util.regex.Matcher match = pattern.matcher(line);
                        if (!match.find()) continue;
                        int code = Integer.parseInt(match.group(2), 16);
                        if (!homes.containsValue(code)) continue;
                        String name = deviceName(match.group(1));
                        Integer home = homes.get(name);
                        if (home == null || home != code) continue;
                        IBinder target = launcher;
                        if (target == null) { taps.clear(); continue; }
                        if (!taps.accept(name, Integer.parseInt(match.group(3), 16), SystemClock.uptimeMillis())) continue;
                        Parcel event = Parcel.obtain();
                        try {
                            event.writeInterfaceToken("app.orchard.tv.gaia.home.v1");
                            target.transact(1, event, null, IBinder.FLAG_ONEWAY);
                        } catch (RemoteException e) {
                            taps.clear();
                        } finally { event.recycle(); }
                    }
                } catch (Exception e) { System.err.println("Home input reader stopped: " + e.getClass().getSimpleName()); }
            }, "GaiaHomeInput");
            reader.setDaemon(true);
            reader.start();
        }
        connect();
        Looper.loop();
    }
}
