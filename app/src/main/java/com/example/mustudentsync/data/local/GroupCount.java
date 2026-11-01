package com.example.mustudentsync.data.local;

/** Result row of StudentDao.getGroupCounts(): one lab group and how many active students it has. */
public class GroupCount {
    public String labGroup;   // e.g. "G01", or "Unassigned"
    public int total;
}
