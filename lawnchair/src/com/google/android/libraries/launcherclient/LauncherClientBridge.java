package com.google.android.libraries.launcherclient;

import android.content.ComponentName;
import android.content.ServiceConnection;
import android.os.IBinder;
import android.os.Handler;
import android.os.Looper;
import android.os.RemoteException;

import amirz.aidlbridge.IBridge;
import amirz.aidlbridge.IBridgeCallback;

public class LauncherClientBridge extends IBridgeCallback.Stub implements ServiceConnection {
    private static final String INTERFACE_DESCRIPTOR = "amirz.aidlbridge.IBridge";

    private final BaseClientService mClientService;
    private final int mFlags;
    private ComponentName mConnectionName;

    public LauncherClientBridge(BaseClientService launcherClientService, int flags) {
        mClientService = launcherClientService;
        mFlags = flags;
    }

    /**
     * Fugueti: through the Fugueti Feed bridge these callbacks arrive as binder calls, on a
     * binder thread, while LauncherClient uses the overlay connection on the main thread. A
     * disconnect then cleared it in the middle of exchangeConfig() (NullPointerException at
     * Launcher.onAttachedToWindow). Hand them to the main thread, as a plain ServiceConnection is.
     */
    private final Handler mMainHandler = new Handler(Looper.getMainLooper());

    @Override
    public void onServiceConnected(ComponentName name, IBinder service) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mMainHandler.post(() -> onServiceConnected(name, service));
            return;
        }
        try {
            if (INTERFACE_DESCRIPTOR.equals(service.getInterfaceDescriptor())) {
                IBridge bridge = IBridge.Stub.asInterface(service);
                try {
                    bridge.bindService(this, mFlags);
                } catch (RemoteException e) {
                    e.printStackTrace();
                }
            } else {
                mClientService.onServiceConnected(name, service);
                mConnectionName = name;
            }
        } catch (RemoteException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void onServiceDisconnected(ComponentName name) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mMainHandler.post(() -> onServiceDisconnected(name));
            return;
        }
        if (mConnectionName != null) {
            mClientService.onServiceDisconnected(mConnectionName);
            mConnectionName = null;
        }
    }
}
