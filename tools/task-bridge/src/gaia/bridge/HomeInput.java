package gaia.bridge;

import java.util.HashMap;
import java.util.Map;

/** Timing state only; no event contents are retained or logged. */
final class HomeInput {
    private final Map<String, Long> down = new HashMap<>();
    private final Map<String, Long> up = new HashMap<>();
    private long lastDouble = Long.MIN_VALUE / 2;

    boolean accept(String device, int value, long now) {
        if (value == 1) down.put(device, now);
        if (value != 0) return false;
        Long pressed = down.remove(device);
        if (pressed == null || now - pressed > 450 || now < pressed) {
            up.remove(device);
            return false;
        }
        Long previous = up.put(device, now);
        if (previous == null || now - previous < 80 || now - previous > 650) return false;
        up.remove(device);
        // Some remotes expose the same key through two input nodes.
        if (now - lastDouble < 700) return false;
        lastDouble = now;
        return true;
    }
    void clear() { down.clear(); up.clear(); }
}
