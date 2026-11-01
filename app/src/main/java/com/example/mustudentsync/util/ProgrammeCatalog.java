package com.example.mustudentsync.util;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Programme code <-> name. The app's dropdowns and Room use the NAME ("Computer Science");
 * the server uses the CODE ("CS"). Kept in the app (not fetched) so registration also works
 * offline. Must match programmes in the backend's db/schema.sql, in the same order.
 */
public final class ProgrammeCatalog {

    private static final Map<String, String> NAME_BY_CODE = new LinkedHashMap<>();
    private static final Map<String, String> CODE_BY_NAME = new LinkedHashMap<>();

    static {
        // School of Engineering and Technology
        add("CS", "Computer Science");
        add("IT", "Information Technology");
        add("DS", "Data Science");
        add("ICTED", "ICT with Education");
        add("CIV", "Civil Engineering");
        add("MEC", "Mechanical Engineering");
        add("EEE", "Electrical and Electronics Engineering");
        add("AGE", "Agricultural Engineering");
        add("WTE", "Water Engineering");
        add("IND", "Industrial Engineering");
        add("GEO", "Geomatics Engineering");
        // School of Natural and Applied Sciences
        add("MST", "Mathematics and Statistics");
        add("STA", "Statistics");
        add("PHY", "Physics");
        add("PHM", "Physics and Mathematics");
        add("CHM", "Chemistry");
        add("BIO", "Biological Sciences");
        add("BCH", "Bio-Chemistry");
        add("LAB", "Laboratory Technology");
        add("NRM", "Natural Resources Management");
        // School of Medicine and Health Sciences
        add("MBC", "Medicine and Surgery (MBChB)");
        add("BMS", "Biomedical Sciences");
        add("PHA", "Pharmacy");
    }

    private ProgrammeCatalog() { }

    private static void add(String code, String name) {
        NAME_BY_CODE.put(code, name);
        CODE_BY_NAME.put(name, code);
    }

    /** Name for a server code. An unknown code is returned as-is so the screen still shows something. */
    public static String nameFor(String code) {
        if (code == null) return "";
        String name = NAME_BY_CODE.get(code);
        return name != null ? name : code;
    }

    /** Code to send to the server for a dropdown name, or null if the name is not a known programme. */
    public static String codeFor(String name) {
        if (name == null) return null;
        String trimmed = name.trim();
        String code = CODE_BY_NAME.get(trimmed);
        // Records synced before a programme was added here may hold the raw code (e.g. "ICTED").
        if (code == null && NAME_BY_CODE.containsKey(trimmed)) return trimmed;
        return code;
    }
}
