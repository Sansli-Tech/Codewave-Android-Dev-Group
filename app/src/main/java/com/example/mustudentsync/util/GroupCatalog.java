package com.example.mustudentsync.util;

import android.content.Context;
import android.content.SharedPreferences;

import com.example.mustudentsync.network.dto.GroupListResponse;
import com.google.gson.Gson;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The lab groups the app offers, and each group's capacity.
 *
 * The server's lab_groups table is the source of truth. After each successful
 * lecturer sync the list is refreshed from GET /api/groups and cached on the
 * phone, so new groups appear without changing code and the dropdowns still
 * work offline. Until the first refresh, the built-in list below is used
 * (G01–G14 from the lecturer's group list).
 */
public final class GroupCatalog {

    private static final String PREFS = "mu_studentsync_groups";
    private static final String KEY_CACHE = "groups_json";
    public static final int DEFAULT_CAPACITY = 15;

    private GroupCatalog() { }

    /** Built-in fallback: group code -> capacity. */
    private static Map<String, Integer> defaults() {
        Map<String, Integer> m = new LinkedHashMap<>();
        for (int i = 1; i <= 14; i++) {
            String code = String.format(java.util.Locale.US, "G%02d", i);
            m.put(code, DEFAULT_CAPACITY);
        }
        // These groups have 16 members on the lecturer's list.
        m.put("G01", 16);
        m.put("G04", 16);
        m.put("G09", 16);
        m.put("G10", 16);
        m.put("G11", 16);
        return m;
    }

    /** Cached server list if there is one, otherwise the built-in list. */
    private static Map<String, Integer> current(Context context) {
        String json = prefs(context).getString(KEY_CACHE, null);
        if (json != null) {
            try {
                GroupListResponse cached = new Gson().fromJson(json, GroupListResponse.class);
                if (cached != null && cached.groups != null && !cached.groups.isEmpty()) {
                    Map<String, Integer> m = new LinkedHashMap<>();
                    for (GroupListResponse.Group g : cached.groups) {
                        if (g.groupCode != null) {
                            m.put(g.groupCode, g.capacity > 0 ? g.capacity : DEFAULT_CAPACITY);
                        }
                    }
                    if (!m.isEmpty()) return m;
                }
            } catch (Exception ignored) {
                // A damaged cache falls back to the built-in list.
            }
        }
        return defaults();
    }

    /** Group codes in order, e.g. [G01, G02, ... G14]. */
    public static List<String> codes(Context context) {
        return new ArrayList<>(current(context).keySet());
    }

    /** Capacity for a group code (15 if the group is unknown). */
    public static int capacityFor(Context context, String groupCode) {
        Integer cap = current(context).get(groupCode);
        return cap != null ? cap : DEFAULT_CAPACITY;
    }

    /** Stores the list returned by GET /api/groups. */
    public static void saveFromServer(Context context, GroupListResponse response) {
        if (response == null || response.groups == null || response.groups.isEmpty()) return;
        prefs(context).edit().putString(KEY_CACHE, new Gson().toJson(response)).apply();
    }

    private static SharedPreferences prefs(Context context) {
        return context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }
}