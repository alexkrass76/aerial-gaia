package gaia.bridge;
public final class TaskClosePolicyTest {
    public static void main(String[] args) {
        for (String pkg : new String[]{null,"android","app.orchard.tv.gaia","com.android.systemui","com.cvte.tv.setting","com.wireguard.android","io.github.jqssun.airplay","com.maxhub.screenshare.server.tv"})
            if (TaskClosePolicy.allows(pkg)) throw new AssertionError("Protected: " + pkg);
        for (String pkg : new String[]{"app.fluffy.gaia","com.finalwire.aida64","org.localsend.localsend_app"})
            if (!TaskClosePolicy.allows(pkg)) throw new AssertionError("Ordinary task: " + pkg);
        System.out.println("TaskClosePolicy tests passed");
    }
}
