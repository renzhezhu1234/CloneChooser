package io.github.clonechooser;

/** Restricts routing to primary user 0 and its native clone profiles. */
final class ProfilePolicy {
    static int callerUser(int uid, int requestedUser) {
        int user = uid / 100000;
        int appId = uid % 100000;
        if (uid < 0 || appId < 10000 || appId >= 20000 || requestedUser != user)
            throw new SecurityException("Unsupported origin");
        return user;
    }

    static boolean isFamilyMember(int id, String type, int parentId, boolean enabled) {
        return enabled && (id == 0 || (id > 0 && parentId == 0
                && "android.os.usertype.profile.CLONE".equals(type)));
    }

    static String label(int user, int sourceUser) {
        String label = user == 0 ? "主应用" : "分身应用 · 空间 " + user;
        return user == sourceUser ? label + "（当前空间）" : label;
    }
}
