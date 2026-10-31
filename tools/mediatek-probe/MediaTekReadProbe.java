package aerial.probe;

import dalvik.system.PathClassLoader;
import java.lang.reflect.*;
import org.json.*;

/** Fixed scalar API entry point. Reads by default; writes require validated expected state. */
public final class MediaTekReadProbe {
    private static final String[] KEYS = {
        "g_video__picture_mode", "g_video__brightness", "g_video__contrast",
        "g_video__vid_sat", "g_video__vid_shp", "g_video__vid_hue",
        "g_fusion_picture__backlight", "g_disp__disp_back_light", "g_disp__disp_adp_back_light", "g_audio__aud_balance", "g_audio__aud_out_port",
        "g_audio__spdif", "g_cec__cec_func", "g_cec__cec_auto_off", "g_cec__cec_auto_on",
        "g_cec__cec_pass_through", "g_cec__cec_sac_func"
    };
    public static String snapshot(ClassLoader loader) {
        JSONObject out = new JSONObject();
        try {
            out.put("uid", android.os.Process.myUid()).put("sdk", android.os.Build.VERSION.SDK_INT);
            Class<?> type = Class.forName("com.mediatek.twoworlds.tv.MtkTvConfig", true, loader);
            Object config = type.getMethod("getInstance").invoke(null);
            Method read = type.getMethod("getConfigValue", int.class, String.class);
            Method range = type.getMethod("getMinMaxConfigValue", String.class);
            JSONArray items = new JSONArray();
            for (String key : KEYS) {
                JSONObject row = new JSONObject().put("key", key);
                try {
                    row.put("raw", read.invoke(config, -1, key));
                    row.put("rangeRaw", range.invoke(config, key));
                    if (key.equals("g_disp__disp_back_light")) {
                        row.put("visible", type.getMethod("isConfigVisible", String.class).invoke(config,key));
                        row.put("enabled", type.getMethod("isConfigEnabled", String.class).invoke(config,key));
                    }
                    row.put("status", "read_returned_unvalidated");
                } catch (Throwable e) { row.put("error", cause(e)); }
                items.put(row);
            }
            out.put("items", items);
            Class<?> inputs = Class.forName("com.mediatek.twoworlds.tv.MtkTvInputSource", true, loader);
            out.put("source", inputs.getMethod("getCurrentInputSourceName", String.class)
                .invoke(inputs.getMethod("getInstance").invoke(null), "main"));
        } catch (Throwable e) { try { out.put("error", cause(e)); } catch (Exception ignored) {} }
        return out.toString();
    }
    private static String cause(Throwable e) {
        while (e.getCause() != null) e = e.getCause();
        return e.getClass().getName() + ": " + e.getMessage();
    }
    public static void main(String[] args) {
        if (args.length != 1 && args.length != 2) throw new IllegalArgumentException("Installed settings APK path required");
        try {
            Class<?> factory = Class.forName("com.android.internal.os.ClassLoaderFactory");
            ClassLoader loader = (ClassLoader) factory.getMethod("createClassLoader", String.class,
                String.class, String.class, ClassLoader.class, int.class, boolean.class, String.class)
                .invoke(null, args[0], "/vendor/lib:/system/lib", "/system:/vendor:/apex",
                    ClassLoader.getSystemClassLoader(), 30, true, null);
            JSONObject result = new JSONObject(snapshot(loader));
            if (args.length == 2) {
                if (android.os.Process.myUid() != 2000) throw new SecurityException("Shell required");
                JSONObject request = new JSONObject(args[1]);
                String key = request.getString("key");
                int target = request.getInt("value");
                MediaTekWritePolicy.validate(result, request.getJSONObject("expected"), key, target);
                Class<?> config = Class.forName("com.mediatek.twoworlds.tv.MtkTvConfig", true, loader);
                int code = (Integer)config.getMethod("setConfigValue", int.class, String.class, int.class, int.class)
                    .invoke(config.getMethod("getInstance").invoke(null), -1, key, target, 3);
                JSONObject after = new JSONObject(snapshot(loader));
                MediaTekWritePolicy.context(after, result);
                if(code != 0 || MediaTekWritePolicy.value(after,key) != target)
                    throw new IllegalStateException("Firmware write or read-back failed");
                result = after;
            }
            System.out.println(result);
            System.exit(0);
        } catch (Throwable e) { System.out.println("Loader: " + cause(e)); System.exit(1); }
    }
}
