package com.example.mustudentsync.share;

import com.example.mustudentsync.model.StudentEntity;

import java.util.ArrayList;
import java.util.List;

// Turns the full list of local students into group counts and share text.
// Only the counts leave this class, never names or student numbers.
public class GroupSummaryCalculator {

    public static final int CAPACITY = 15;
    private static final String[] GROUPS = {"G01", "G02", "G03", "G04"};
    private static final String UNASSIGNED = "Unassigned";

    public static String buildText(List<StudentEntity> students) {
        List<GroupCount> counts = new ArrayList<>();
        for (String g : GROUPS) {
            counts.add(new GroupCount(g, countIn(students, g), CAPACITY));
        }
        return ShareSummaryBuilder.build(counts, countUnassigned(students));
    }

    private static int countIn(List<StudentEntity> students, String group) {
        int n = 0;
        for (StudentEntity s : students) {
            if (s.isActive && group.equals(s.labGroup)) n++;
        }
        return n;
    }

    private static int countUnassigned(List<StudentEntity> students) {
        int n = 0;
        for (StudentEntity s : students) {
            if (!s.isActive) continue;
            if (s.labGroup == null || UNASSIGNED.equalsIgnoreCase(s.labGroup)) n++;
        }
        return n;
    }
}