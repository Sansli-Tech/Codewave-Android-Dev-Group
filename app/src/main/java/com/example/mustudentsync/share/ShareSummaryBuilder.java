package com.example.mustudentsync.share;

import java.util.List;

// Builds the Sharesheet text. It only receives group codes and counts,
// so it cannot leak names or student numbers.
public class ShareSummaryBuilder {

    public static String build(List<GroupCount> groups, int unassigned) {
        StringBuilder sb = new StringBuilder("Lab group summary\n");
        for (GroupCount g : groups) {
            sb.append(g.groupCode).append(": ")
              .append(g.active).append("/").append(g.capacity).append("\n");
        }
        sb.append("Unassigned: ").append(unassigned);
        return sb.toString();
    }
}