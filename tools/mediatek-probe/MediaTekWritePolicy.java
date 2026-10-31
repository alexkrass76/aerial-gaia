package aerial.probe;

import org.json.*;

/** Shared validation; seven scalars only, never profiles, outputs, CEC or factory controls. */
public final class MediaTekWritePolicy {
    public static boolean allows(String key) {
        return key.equals("g_video__brightness") || key.equals("g_video__contrast") ||
            key.equals("g_video__vid_sat") || key.equals("g_video__vid_shp") ||
            key.equals("g_video__vid_hue") || key.equals("g_audio__aud_balance") || key.equals("g_disp__disp_back_light");
    }
    public static JSONObject item(JSONObject root, String key) throws JSONException {
        JSONArray rows = root.getJSONArray("items");
        for (int i=0;i<rows.length();i++) if (key.equals(rows.getJSONObject(i).optString("key"))) return rows.getJSONObject(i);
        throw new IllegalArgumentException("Missing value");
    }
    public static int value(JSONObject root, String key) throws JSONException {
        JSONObject row = item(root,key);
        if(row.has("error")) throw new IllegalArgumentException("Unreadable value");
        return row.getInt("raw");
    }
    public static void context(JSONObject current, JSONObject expected) throws JSONException {
        String source = current.getString("source");
        if(source.isEmpty() || !source.equals(expected.getString("source"))) throw new IllegalStateException("Input changed");
        for(String key:new String[]{"g_video__picture_mode","g_audio__aud_out_port","g_audio__spdif"})
            if(value(current,key)!=value(expected,key)) throw new IllegalStateException("Profile or audio route changed");
    }
    public static void validate(JSONObject current, JSONObject expected, String key, int target) throws JSONException {
        if(!allows(key)) throw new IllegalArgumentException("Unsupported write");
        context(current,expected);
        if(value(current,key)!=value(expected,key)) throw new IllegalStateException("Value changed externally");
        int packed = item(current,key).getInt("rangeRaw");
        if(packed!=item(expected,key).getInt("rangeRaw")) throw new IllegalStateException("Range changed");
        int low=(short)(packed & 65535), high=(short)(packed >>>16);
        int hardLow=key.equals("g_audio__aud_balance")?-50:(key.equals("g_disp__disp_back_light")?5:0);
        if(key.equals("g_disp__disp_back_light")) {
            for(JSONObject state:new JSONObject[]{current,expected})
                if(item(state,key).optInt("visible",-1)!=0 || item(state,key).optInt("enabled",-1)!=0)
                    throw new IllegalStateException("Backlight unavailable");
        }
        int hardHigh=key.equals("g_audio__aud_balance")?50:100;
        if(value(current,key)<hardLow || value(current,key)>hardHigh || packed==0 || low>high || target<low || target>high || target<hardLow || target>hardHigh)
            throw new IllegalArgumentException("Outside verified range");
    }
}
