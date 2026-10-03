package io.github.clonechooser;

import android.os.Parcel;
import android.os.ResultReceiver;

/** Send the framework class, never the module's anonymous ResultReceiver subclass. */
final class ReceiverTransport {
    static ResultReceiver frameworkReceiver(ResultReceiver callback) {
        Parcel parcel = Parcel.obtain();
        try {
            callback.writeToParcel(parcel, 0);
            parcel.setDataPosition(0);
            return ResultReceiver.CREATOR.createFromParcel(parcel);
        } finally { parcel.recycle(); }
    }
}
