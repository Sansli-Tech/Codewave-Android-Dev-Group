package com.example.mustudentsync.util;

import java.util.regex.Pattern;

/**
 * The same name rule the server uses (routes/students.js and routes/me.js):
 * 2-100 characters; letters (any language), spaces, apostrophes, full stops and hyphens.
 * Checking it on the phone means a bad name is caught BEFORE it is saved and queued,
 * instead of coming back later as "Action required".
 */
public final class NameRule {

    private static final Pattern ALLOWED = Pattern.compile("^[\\p{L}\\p{M} .'-]+$");

    private NameRule() { }

    /** True if the server will accept this name. */
    public static boolean isValid(String name) {
        if (name == null) return false;
        String n = name.trim();
        return n.length() >= 2 && n.length() <= 100 && ALLOWED.matcher(n).matches();
    }
}
