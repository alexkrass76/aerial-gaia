package gaia.bridge;

public final class HomeInputTest {
    private static void check(boolean value) { if (!value) throw new AssertionError(); }
    private static boolean tap(HomeInput keys, String name, long at, long held) {
        keys.accept(name, 1, at);
        return keys.accept(name, 0, at + held);
    }
    public static void main(String[] args) {
        HomeInput keys = new HomeInput();
        check(!tap(keys, "remote", 1000, 75));
        check(tap(keys, "remote", 1150, 112));
        check(!tap(keys, "duplicate node", 1100, 75));
        check(!tap(keys, "duplicate node", 1250, 112));
        keys = new HomeInput();
        check(!tap(keys, "remote", 1000, 60));
        check(!tap(keys, "other", 1200, 60));
        check(!tap(keys, "remote", 1900, 60));
        check(!tap(keys, "remote", 2050, 900));
        check(!tap(keys, "remote", 3100, 60));
        keys.clear();
        check(!tap(keys, "remote", 3300, 60));
        check(!keys.accept("remote", 0, 3450));
        System.out.println("Home input timing tests passed");
    }
}
