package com.example.mustudentsync;

import static org.junit.Assert.*;

import com.example.mustudentsync.model.StudentEntity;
import com.example.mustudentsync.share.GroupSummaryCalculator;

import java.util.ArrayList;
import java.util.List;
import org.junit.Test;

public class GroupSummaryCalculatorTest {

    private StudentEntity student(String id, String name, String group, boolean active) {
        StudentEntity s = new StudentEntity(id, "20240000" + id, name, "Computer Science", group);
        s.isActive = active;
        return s;
    }

    @Test
    public void countsActiveStudentsPerGroupAndUnassigned() {
        List<StudentEntity> list = new ArrayList<>();
        list.add(student("1", "Chanda Phiri", "G01", true));
        list.add(student("2", "Mwila Banda", "G01", true));
        list.add(student("3", "Thandiwe Mwansa", "G02", false)); // deleted, not counted
        list.add(student("4", "Bwalya Zulu", "Unassigned", true));
        list.add(student("5", "Natasha Lungu", null, true));

        String text = GroupSummaryCalculator.buildText(list);

        assertTrue(text.contains("G01: 2/15"));
        assertTrue(text.contains("G02: 0/15"));
        assertTrue(text.contains("G03: 0/15"));
        assertTrue(text.contains("Unassigned: 2"));
    }

    @Test
    public void sharedTextContainsNoNamesOrStudentNumbers() {
        List<StudentEntity> list = new ArrayList<>();
        list.add(student("1", "Chanda Phiri", "G01", true));

        String text = GroupSummaryCalculator.buildText(list);

        assertFalse(text.contains("Chanda"));
        assertFalse(text.contains("Phiri"));
        assertFalse(text.matches("(?s).*[0-9]{9}.*"));
    }
}