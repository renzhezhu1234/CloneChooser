package io.github.clonechooser;

import android.os.Bundle;
import android.os.Parcel;
import android.os.ResultReceiver;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/** Uses the boot class loader to reproduce system_server's receiver decoding. */
public final class ReceiverTransportTest {
    public static void main(String[] args) throws Exception {
        CountDownLatch delivered = new CountDownLatch(1);
        ResultReceiver callback = new ResultReceiver(null) {
            @Override protected void onReceiveResult(int code, Bundle data) {
                if (code == 73 && data.getIntArray("users")[0] == 10) delivered.countDown();
            }
        };
        boolean oldRejected = false;
        Parcel broken = Parcel.obtain();
        try {
            broken.writeParcelable(callback, 0); broken.setDataPosition(0);
            broken.readParcelable(null);
        } catch (android.os.BadParcelableException expected) { oldRejected = true; }
        finally { broken.recycle(); }
        if (!oldRejected) throw new AssertionError("Could not reproduce old module-class-loader failure");
        ResultReceiver transport = ReceiverTransport.frameworkReceiver(callback);
        if (transport.getClass() != ResultReceiver.class) throw new AssertionError("Not a framework class");
        Parcel wire = Parcel.obtain();
        ResultReceiver restored;
        try {
            wire.writeParcelable(transport, 0); wire.setDataPosition(0);
            restored = wire.readParcelable(null);
        } finally { wire.recycle(); }
        Bundle data = new Bundle(); data.putIntArray("users", new int[]{10});
        restored.send(73, data);
        if (!delivered.await(3, TimeUnit.SECONDS)) throw new AssertionError("Callback was lost");
        System.out.println("PASS: old subclass rejected; framework receiver decoded; clone result delivered");
        System.exit(0);
    }
}
