package io.github.clonechooser;

public final class ProfilePolicyTest {
    private static int checks;
    private static void check(boolean ok, String message) {
        if (!ok) throw new AssertionError(message);
        checks++;
    }
    private static void reject(int uid, int user) {
        try { ProfilePolicy.callerUser(uid, user); throw new AssertionError("accepted caller " + uid); }
        catch (SecurityException expected) { checks++; }
    }
    public static void main(String[] args) {
        check(ProfilePolicy.callerUser(10300, 0) == 0, "main origin");
        check(ProfilePolicy.callerUser(1010300, 10) == 10, "clone origin");
        reject(1010300, 0); reject(10300, 10); reject(1000, 0);
        reject(1099000, 10); reject(1020300, 10); reject(-1, 0);
        check(ProfilePolicy.isFamilyMember(0, "android.os.usertype.full.SYSTEM", -1, true), "primary allowed");
        check(ProfilePolicy.isFamilyMember(10, "android.os.usertype.profile.CLONE", 0, true), "native clone allowed");
        check(!ProfilePolicy.isFamilyMember(10, "android.os.usertype.profile.MANAGED", 0, true), "work profile rejected");
        check(!ProfilePolicy.isFamilyMember(11, "android.os.usertype.profile.CLONE", 12, true), "other family rejected");
        check(!ProfilePolicy.isFamilyMember(10, "android.os.usertype.profile.CLONE", 0, false), "disabled rejected");
        check(!ProfilePolicy.isFamilyMember(12, "android.os.usertype.full.SECONDARY", -1, true), "secondary user rejected");
        check(ProfilePolicy.label(0, 10).equals("主应用"), "reverse main label");
        check(ProfilePolicy.label(10, 10).contains("当前空间"), "source label");
        System.out.println("PASS " + checks + " profile boundary checks");
    }
}
