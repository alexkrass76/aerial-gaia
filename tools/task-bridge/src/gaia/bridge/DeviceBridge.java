package gaia.bridge;

import android.content.Context;
import android.content.ContextWrapper;
import android.content.AttributionSource;
import android.net.wifi.*;
import org.json.*;
import java.util.*;

/** Fixed device operations under shell identity. Never return stored Wi-Fi credentials. */
final class DeviceBridge {
    private Context context;
    private synchronized Context context() throws Exception {
        if (context == null) {
            java.util.concurrent.FutureTask<Context> task = new java.util.concurrent.FutureTask<>(() -> {
                Class<?> type = Class.forName("android.app.ActivityThread");
                Object thread = type.getMethod("systemMain").invoke(null);
                Context system = (Context)type.getMethod("getSystemContext").invoke(thread);
                final Context shell = system.createPackageContext("com.android.shell", 0);
                return new ContextWrapper(shell) {
                    @Override public String getPackageName() { return "com.android.shell"; }
                    @Override public String getOpPackageName() { return "com.android.shell"; }
                    @Override public Context getApplicationContext() { return this; }
                    @Override public AttributionSource getAttributionSource() {
                        return new AttributionSource.Builder(android.os.Process.myUid()).setPackageName("com.android.shell").build();
                    }
                };
            });
            if (android.os.Looper.myLooper() == android.os.Looper.getMainLooper()) task.run();
            else new android.os.Handler(android.os.Looper.getMainLooper()).post(task);
            context = task.get(8, java.util.concurrent.TimeUnit.SECONDS);
        }
        return context;
    }
    private WifiManager wifi;
    private synchronized WifiManager wifi() throws Exception {
        if (wifi == null) {
            android.os.IBinder binder = (android.os.IBinder)Class.forName("android.os.ServiceManager")
                .getMethod("getService", String.class).invoke(null, "wifi");
            Class<?> api = Class.forName("android.net.wifi.IWifiManager");
            Object service = Class.forName("android.net.wifi.IWifiManager$Stub")
                .getMethod("asInterface", android.os.IBinder.class).invoke(null, binder);
            wifi = (WifiManager)WifiManager.class.getConstructor(Context.class, api, android.os.Looper.class)
                .newInstance(context(), service, android.os.Looper.getMainLooper());
        }
        return wifi;
    }
    String wifiSnapshot() throws Exception {
        WifiManager wifi = wifi();
        JSONArray saved = new JSONArray(), nearby = new JSONArray();
        List<WifiConfiguration> configs = wifi.getConfiguredNetworks();
        Set<Integer> seen = new HashSet<>();
        if (configs != null) for (WifiConfiguration c : configs) {
            if (c.networkId < 0 || !seen.add(c.networkId) || saved.length() >= 128) continue;
            saved.put(new JSONObject().put("id", c.networkId).put("ssid", cleanSsid(c.SSID)).put("security", c.allowedKeyManagement.get(WifiConfiguration.KeyMgmt.WPA_PSK) ? "wpa2" : "unsupported"));
        }
        List<ScanResult> scans = wifi.getScanResults();
        if (scans != null) {
            Map<String, ScanResult> best = new TreeMap<>();
            for (ScanResult s : scans) {
                if (s.SSID == null || s.SSID.isEmpty()) continue;
                String key = s.SSID + "\u0000" + WifiInputPolicy.security(s.capabilities);
                ScanResult previous = best.get(key);
                if (previous == null || previous.level < s.level) best.put(key, s);
            }
            for (ScanResult s : best.values()) {
                if (nearby.length() >= 64) break;
                nearby.put(new JSONObject().put("ssid", s.SSID).put("level", s.level)
                    .put("security", WifiInputPolicy.security(s.capabilities)));
            }
        }
        WifiInfo info = wifi.getConnectionInfo();
        return new JSONObject().put("enabled", wifi.isWifiEnabled())
            .put("current", info == null ? -1 : info.getNetworkId())
            .put("saved", saved).put("nearby", nearby).toString();
    }
    String scanWifi() throws Exception {
        if (!wifi().isWifiEnabled() || !wifi().startScan()) throw new IllegalStateException("Scan unavailable or throttled");
        return "{}";
    }
    synchronized String connectSaved(int id) throws Exception {
        if (id < 0) throw new IllegalArgumentException("Invalid network id");
        WifiManager wifi = wifi();
        List<WifiConfiguration> list = wifi.getConfiguredNetworks();
        boolean found = false;
        if (list != null) for (WifiConfiguration c : list) if (c.networkId == id) found = true;
        if (!found) throw new IllegalArgumentException("Network no longer saved");
        if (!wifi.enableNetwork(id, true)) throw new IllegalStateException("Connection request refused");
        return "{}"; // Request accepted, not proof of association or Internet access.
    }
    synchronized String addAndConnect(String ssid, String security, String password, int savedId) throws Exception {
        if (savedId < -1) throw new IllegalArgumentException("Invalid network id");
        WifiInputPolicy.validate(ssid, security, password);
        WifiManager manager = wifi();
        boolean visible = false;
        for (ScanResult s : manager.getScanResults())
            if (ssid.equals(s.SSID) && security.equals(WifiInputPolicy.security(s.capabilities))) visible = true;
        if (!visible) throw new IllegalStateException("Network no longer visible");
        List<WifiConfiguration> before = manager.getConfiguredNetworks();
        Set<Integer> ids = new HashSet<>();
        WifiConfiguration existing = null;
        if (before != null) for (WifiConfiguration c : before) {
            ids.add(c.networkId);
            if (savedId < 0 && ssid.equals(cleanSsid(c.SSID))) throw new IllegalStateException("Use saved profile");
            if (savedId == c.networkId) existing = c;
        }
        if (savedId >= 0 && (existing == null || !ssid.equals(cleanSsid(existing.SSID)) ||
            !existing.allowedKeyManagement.get(WifiConfiguration.KeyMgmt.WPA_PSK) || !"wpa2".equals(security)))
            throw new IllegalStateException("Saved profile changed");
        WifiConfiguration config = existing == null ? new WifiConfiguration() : new WifiConfiguration(existing);
        config.SSID = WifiInputPolicy.quote(ssid);
        if ("open".equals(security)) config.allowedKeyManagement.set(WifiConfiguration.KeyMgmt.NONE);
        else {
            config.allowedKeyManagement.set(WifiConfiguration.KeyMgmt.WPA_PSK);
            config.allowedProtocols.set(WifiConfiguration.Protocol.RSN);
            config.preSharedKey = password.length() == 64 ? password : WifiInputPolicy.quote(password);
        }
        int id;
        try { id = existing == null ? manager.addNetwork(config) : manager.updateNetwork(config); }
        finally { config.preSharedKey = null; }
        if (id < 0) throw new IllegalStateException("Profile rejected");
        if (existing == null && ids.contains(id)) throw new IllegalStateException("Existing profile changed concurrently");
        if (!manager.enableNetwork(id, true)) {
            // Only the newly created profile; never delete an earlier saved network.
            if (existing == null) manager.removeNetwork(id);
            throw new IllegalStateException("Connection request refused");
        }
        return new JSONObject().put("id", id).toString();
    }
    private static String cleanSsid(String s) {
        if (s == null) return "";
        return s.length() >= 2 && s.startsWith("\"") && s.endsWith("\"") ? s.substring(1, s.length() - 1) : s;
    }
}
