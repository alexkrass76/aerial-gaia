package gaia.bridge;

import android.os.*;

/** Bounded, read-only CVTE event probe. Never changes volume or hides vendor UI. */
public final class VolumeProbe {
    public static void main(String[] args) throws Exception {
        final String descriptor = "com.cvte.os.ICtvTaskCallback";
        Binder receiver = new Binder() {
            @Override protected boolean onTransact(int code, Parcel data, Parcel reply, int flags)
                    throws RemoteException {
                if (code == INTERFACE_TRANSACTION) {
                    if (reply != null) reply.writeString(descriptor);
                    return true;
                }
                if (code != 1) return super.onTransact(code, data, reply, flags);
                if (Binder.getCallingUid() != 1000) return false;
                data.enforceInterface(descriptor);
                if (data.readInt() == 0) return true;
                Message event = Message.CREATOR.createFromParcel(data);
                try {
                    if (event.what == 1000 && event.getData().containsKey("volume")) {
                        int volume = event.getData().getInt("volume", -1);
                        if (volume >= 0 && volume <= 100)
                            System.out.println("VOLUME " + volume);
                    } else if (event.what == 1001 && event.getData().containsKey("mute")) {
                        System.out.println("MUTE " + event.getData().getBoolean("mute"));
                    }
                } finally { event.recycle(); }
                return true;
            }
        };
        Class<?> callbackType = Class.forName(descriptor);
        Object callback = Class.forName(descriptor + "$Stub")
            .getMethod("asInterface", IBinder.class).invoke(null, receiver);
        Class<?> manager = Class.forName("com.cvte.os.CtvManager");
        Object registered = manager.getMethod("registerTaskCallback", int[].class, callbackType)
            .invoke(null, new int[]{1000, 1001}, callback);
        if (!Boolean.TRUE.equals(registered)) throw new IllegalStateException("CVTE registration rejected");
        System.out.println("SUBSCRIBED uid=" + android.os.Process.myUid() + " timeout=120s");
        new Thread(() -> {
            try { Thread.sleep(120000); }
            catch (InterruptedException ignored) { Thread.currentThread().interrupt(); }
            try {
                Object removed = manager.getMethod("unRegisterTaskCallback", callbackType).invoke(null, callback);
                System.out.println("UNSUBSCRIBED " + removed);
            } catch (Exception e) { System.out.println("UNREGISTER_FAILED " + e.getClass().getSimpleName()); }
            System.exit(0);
        }, "volume-probe-timeout").start();
        Class.forName("com.android.internal.os.BinderInternal").getMethod("joinThreadPool").invoke(null);
    }
}
