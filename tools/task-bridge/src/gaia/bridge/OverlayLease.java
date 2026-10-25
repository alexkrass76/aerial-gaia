package gaia.bridge;

import java.io.*;
import java.nio.charset.StandardCharsets;

/** Fixed-function lease; an independent shell watchdog survives launcher/helper death. */
final class OverlayLease {
    private static final File DIR = new File("/data/local/tmp/aerial-task-bridge/overlay-lease");
    private static Process watchdog;
    private static long sequence;
    private static boolean releasing;
    private static void write(File path, String value) throws IOException {
        try (FileOutputStream stream = new FileOutputStream(path)) { stream.write(value.getBytes(StandardCharsets.UTF_8)); }
    }
    static synchronized String renew() throws Exception {
        if (releasing && watchdog != null && watchdog.isAlive()) throw new IOException("Restoration pending");
        releasing = false;
        if (!DIR.isDirectory() && !DIR.mkdirs()) throw new IOException("Lease directory unavailable");
        File beat = new File(DIR, "beat");
        File temp = new File(DIR, "beat.tmp");
        write(temp, Long.toString(++sequence));
        if (!temp.renameTo(beat)) throw new IOException("Lease update failed");
        if (watchdog == null || !watchdog.isAlive()) {
            File script = new File(DIR, "watch.sh");
            write(script,
                "#!/system/bin/sh\n" +
                "cd /data/local/tmp/aerial-task-bridge/overlay-lease || exit 1\n" +
                "restore() { am startservice --user 0 -n com.cvte.tv.globalui/.OptionService; }\n" +
                "trap 'restore; exit' HUP INT TERM\n" +
                "last=$(cat beat)\nmiss=0\n" +
                "am stopservice --user 0 -n com.cvte.tv.globalui/.OptionService\n" +
                "while [ -f beat ]; do\n sleep 4\n now=$(cat beat 2>/dev/null)\n" +
                " if [ \"$now\" = \"$last\" ]; then miss=$((miss+1)); else miss=0; fi\n" +
                " last=$now\n [ $miss -ge 3 ] && break\ndone\nrestore\n");
            watchdog = new ProcessBuilder("/system/bin/sh", script.getAbsolutePath())
                .redirectErrorStream(true).redirectOutput(new File(DIR, "watch.log")).start();
        }
        return "renewed";
    }
    static synchronized String release() throws Exception {
        releasing = true;
        new File(DIR, "beat").delete();
        // The watchdog restores as well. Immediate explicit restoration shortens normal disable.
        Process restore = new ProcessBuilder("/system/bin/am", "startservice", "--user", "0", "-n",
            "com.cvte.tv.globalui/.OptionService").redirectErrorStream(true)
            .redirectOutput(new File("/data/local/tmp/aerial-task-bridge/overlay-restore.log")).start();
        return "restoring";
    }
}
