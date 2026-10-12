package com.example.mustudentsync;

import static org.junit.Assert.*;

import com.example.mustudentsync.share.GroupCount;
import com.example.mustudentsync.share.ShareSummaryBuilder;

import java.util.Arrays;
import org.junit.Test;

public class ShareSummaryBuilderTest {

    @Test
    public void summaryShowsOnlyGroupLabelsAndCounts() {
        String text = ShareSummaryBuilder.build(Arrays.asList(
                new GroupCount("G01", 15, 15),
                new GroupCount("G02", 14, 15)), 2);

        assertTrue(text.contains("G01: 15/15"));
        assertTrue(text.contains("G02: 14/15"));
        assertTrue(text.contains("Unassigned: 2"));
    }

    @Test
    public void summaryContainsNoNineDigitNumbers() {
        String text = ShareSummaryBuilder.build(Arrays.asList(
                new GroupCount("G01", 15, 15)), 0);
        assertFalse(text.matches("(?s).*[0-9]{9}.*"));
    }
}