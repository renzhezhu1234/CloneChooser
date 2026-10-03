package io.github.clonechooser;

import android.content.ClipData;
import android.content.ComponentName;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Parcel;
import java.util.Arrays;

/** Runs on the real Android framework with app_process, without enabling hooks. */
public final class PayloadTest {
    private static int checks;
    private static void check(boolean value, String label) {
        if (!value) throw new AssertionError(label);
        checks++;
    }
    public static void main(String[] args) {
        ComponentName target = new ComponentName("test.target", "test.target.Detail");
        Intent original = new Intent("example.ACTION", Uri.parse("example://product?id=42&coupon=a%2Bb"));
        original.setComponent(target).setPackage(target.getPackageName());
        original.addCategory("example.category");
        original.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_GRANT_READ_URI_PERMISSION);
        original.putExtra("text", "中文、优惠券 + & =");
        original.putExtra("number", 1234567890123L);
        original.putExtra("bool", true);
        original.putExtra("bytes", new byte[]{0, 1, 2, -1});
        Bundle nested = new Bundle(); nested.putString("nested", "value"); original.putExtra("bundle", nested);
        original.setClipData(ClipData.newRawUri("payload", Uri.parse("content://example.provider/file/1")));
        Intent request = RouteIntents.cloneRequest(original, target, 10);
        check(original.getComponent().equals(target), "source component unchanged");
        check(!RouteIntents.hasReservedExtras(original), "source extras unchanged");
        check((original.getFlags() & Intent.FLAG_ACTIVITY_NEW_TASK) == 0, "source flags unchanged");
        Parcel parcel = Parcel.obtain(); request.writeToParcel(parcel, 0); parcel.setDataPosition(0);
        Intent transported = Intent.CREATOR.createFromParcel(parcel); parcel.recycle();
        Intent restored = RouteIntents.restore(transported);
        check(target.equals(restored.getComponent()), "component restored");
        check(original.getData().equals(restored.getData()), "encoded deep link preserved");
        check(original.getAction().equals(restored.getAction()), "action preserved");
        check(original.getPackage().equals(restored.getPackage()), "package preserved");
        check(restored.hasCategory("example.category"), "category preserved");
        check(original.getStringExtra("text").equals(restored.getStringExtra("text")), "unicode extra preserved");
        check(restored.getLongExtra("number", 0) == 1234567890123L, "long preserved");
        check(restored.getBooleanExtra("bool", false), "boolean preserved");
        check(Arrays.equals(original.getByteArrayExtra("bytes"), restored.getByteArrayExtra("bytes")), "bytes preserved");
        check("value".equals(restored.getBundleExtra("bundle").getString("nested")), "nested bundle preserved");
        check(original.getClipData().getItemAt(0).getUri().equals(restored.getClipData().getItemAt(0).getUri()), "clip URI preserved");
        check((restored.getFlags() & original.getFlags()) == original.getFlags(), "original flags preserved");
        check(!RouteIntents.hasReservedExtras(restored), "bridge extras stripped");
        Intent forged = new Intent(request).putExtra(Config.ORIGINAL, "other.package/other.package.Private");
        try { RouteIntents.restore(forged); throw new AssertionError("forgery accepted"); }
        catch (SecurityException expected) { checks++; }
        Intent collision = new Intent(original).putExtra(Config.MODE, "existing app data");
        try { RouteIntents.cloneRequest(collision, target, 10); throw new AssertionError("collision accepted"); }
        catch (IllegalArgumentException expected) { checks++; }
        Intent implicit = new Intent(Intent.ACTION_VIEW, Uri.parse("https://example.com/product/42"));
        check(target.equals(RouteIntents.restore(RouteIntents.cloneRequest(implicit, target, 10)).getComponent()), "implicit target resolved");
        check(implicit.getComponent() == null, "implicit original unchanged");
        System.out.println("PASS " + checks + " Android payload checks");
    }
}
