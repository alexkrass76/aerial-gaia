package gaia.bridge;

import java.nio.charset.StandardCharsets;

/** Shared input validation; no shell quoting, logging or credential persistence. */
public final class WifiInputPolicy {
    public static String security(String capabilities) {
        String s = capabilities == null ? "" : capabilities;
        if (s.contains("EAP") || s.contains("WEP")) return "unsupported";
        if (s.contains("PSK") && (s.contains("WPA2") || s.contains("RSN"))) return "wpa2";
        if (s.contains("SAE") || s.contains("OWE")) return "unsupported";
        return s.contains("ESS") && !s.contains("WPA") ? "open" : "unsupported";
    }
    public static void validate(String ssid, String security, String password) {
        if (ssid == null || ssid.isEmpty() || ssid.getBytes(StandardCharsets.UTF_8).length > 32 || ssid.chars().anyMatch(c -> c < 32 || c == 127))
            throw new IllegalArgumentException("Invalid network name");
        if (password == null) throw new IllegalArgumentException("Invalid credentials");
        if ("open".equals(security)) {
            if (!password.isEmpty()) throw new IllegalArgumentException("Unexpected credentials");
        } else if ("wpa2".equals(security)) {
            boolean hex = password.length() == 64 && password.matches("[0-9a-fA-F]{64}");
            boolean ascii = password.length() >= 8 && password.length() <= 63 && password.chars().allMatch(c -> c >= 32 && c <= 126);
            if (!hex && !ascii) throw new IllegalArgumentException("Invalid credentials");
        } else throw new IllegalArgumentException("Unsupported security");
    }
    public static String quote(String value) { return "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\""; }
}
