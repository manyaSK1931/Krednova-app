package com.krednova.payroll.security;

import java.util.Locale;
import java.util.Set;

/** One place for password rules. Length matters most, so there are no "must contain a symbol" rules. */
public final class PasswordPolicy {
    private PasswordPolicy() {}

    private static final Set<String> COMMON = Set.of("qwertyuiopas", "qwertyuiop12", "abcdefghijkl", "iloveyou1234",
        "welcome12345", "administrator", "changeme1234", "letmein12345");

    /** Returns a message describing the problem, or null when the password is acceptable. (fullName is kept for callers; names are allowed in passwords.) */
    public static String check(String pw, String email, String fullName) {
        if (pw == null || pw.length() < 12) return "Password must be at least 12 characters.";
        if (pw.length() > 128) return "Password must be at most 128 characters.";
        String p = pw.toLowerCase(Locale.ROOT);
        if (pw.chars().distinct().count() < 5) return "Password is too repetitive. Use a longer, more varied password.";
        if (p.contains("password") || p.contains("krednova") || p.contains("123456") || COMMON.contains(p))
            return "Password is too easy to guess. Try a few unrelated words, or a longer phrase.";
        if (email != null) {
            String local = email.toLowerCase(Locale.ROOT).split("@")[0];
            if (local.length() >= 4 && p.contains(local)) return "Password must not contain your email name.";
        }
        return null;
    }
}
