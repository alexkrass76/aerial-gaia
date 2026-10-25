package gaia.bridge;

import android.content.*;
import android.os.*;
import dalvik.system.PathClassLoader;

/** Bounded passive TV API probe; no key handling or system mutations. */
public final class VolumeNotifyProbe {
    private static final String API = "com.cvte.tv.api.aidl.";
    private static Object manager, callback;
    private static Class<?> managerType, listenerType;
    public static void main(String[] args) throws Exception {
        Looper.prepareMainLooper();
        Handler main = new Handler(Looper.getMainLooper());
        Class<?> activity = Class.forName("android.app.ActivityThread");
        Object thread = activity.getMethod("systemMain").invoke(null);
        Context system = (Context) activity.getMethod("getSystemContext").invoke(thread);
        Context context = system.createPackageContext("com.android.shell", 0);
        ClassLoader loader = new PathClassLoader("/system/framework/cn.cvte.tv.api.jar", VolumeNotifyProbe.class.getClassLoader());
        managerType = Class.forName(API + "ITvApiManager", false, loader);
        listenerType = Class.forName(API + "INotifyListener", false, loader);
        Binder receiver = new Binder() {
            @Override protected boolean onTransact(int code, Parcel data, Parcel reply, int flags) throws RemoteException {
                if (code == INTERFACE_TRANSACTION) { if (reply != null) reply.writeString(API + "INotifyListener"); return true; }
                if (code != 1) return super.onTransact(code, data, reply, flags);
                if (Binder.getCallingUid() != 1000) throw new SecurityException("System required");
                data.enforceInterface(API + "INotifyListener");
                String action = data.readString();
                if (data.readInt() != 0) Bundle.CREATOR.createFromParcel(data);
                if ("notifySoundSpeakerOutChange".equals(action) || "notifySoundSpeakerOutMuteChange".equals(action) ||
                    "notifySoundArcAmpVolumeChange".equals(action) || "notifySoundArcAmpMuteChange".equals(action)) {
                    // Read state after acknowledging the callback; never reenter TV API synchronously.
                    main.post(() -> {
                        try {
                            Object speaker = managerType.getMethod("getTVApiSoundSpeakerOut").invoke(manager);
                            Class<?> sound = Class.forName(API + "ITVApiSoundSpeakerOutAidl", false, loader);
                            System.out.println(action + " speakerVolume=" + sound.getMethod("eventSoundSpeakerOutGetVolume").invoke(speaker)
                                + " speakerMute=" + sound.getMethod("eventSoundSpeakerOutIsMute").invoke(speaker));
                        } catch (Exception e) { System.out.println("READ_FAILED " + e.getClass().getSimpleName()); }
                    });
                }
                if (reply != null) reply.writeNoException();
                return true;
            }
        };
        callback = Class.forName(API + "INotifyListener$Stub", false, loader).getMethod("asInterface", IBinder.class).invoke(null, receiver);
        ServiceConnection connection = new ServiceConnection() {
            public void onServiceConnected(ComponentName name, IBinder binder) {
                try {
                    if (!(API + "ITvApiManager").equals(binder.getInterfaceDescriptor())) throw new SecurityException("Unexpected service");
                    manager = Class.forName(API + "ITvApiManager$Stub", false, loader).getMethod("asInterface", IBinder.class).invoke(null, binder);
                    managerType.getMethod("addNotifyListener", listenerType).invoke(manager, callback);
                    System.out.println("TV_API_SUBSCRIBED uid=" + android.os.Process.myUid());
                } catch (Exception e) { e.printStackTrace(); System.exit(1); }
            }
            public void onServiceDisconnected(ComponentName name) { System.out.println("DISCONNECTED"); System.exit(1); }
        };
        boolean bound = context.bindService(new Intent("com.cvte.tv.api.TV_API_SERVICE").setComponent(
            new ComponentName("com.cvte.tv.api.impl", "com.cvte.tv.api.TvApiService")), connection, Context.BIND_AUTO_CREATE);
        if (!bound) throw new IllegalStateException("Bind rejected");
        main.postDelayed(() -> {
            try { if (manager != null) managerType.getMethod("removeNotifyListener", listenerType).invoke(manager, callback); }
            catch (Exception e) { System.out.println("UNREGISTER_FAILED"); }
            context.unbindService(connection);
            System.out.println("TV_API_UNSUBSCRIBED"); System.exit(0);
        }, 120000);
        Looper.loop();
    }
}
