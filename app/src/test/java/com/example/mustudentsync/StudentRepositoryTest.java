package com.example.mustudentsync;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import androidx.arch.core.executor.testing.InstantTaskExecutorRule;

import com.example.mustudentsync.data.FakeStudentRepository;
import com.example.mustudentsync.model.StudentEntity;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

/**
 * Plain JUnit test (no Android framework, no device/emulator needed) — this is
 * the "test with a fake repository" evidence Activity B asks for.
 *
 * InstantTaskExecutorRule makes LiveData.setValue() run synchronously on the
 * calling thread instead of asserting it's the real Android main thread, which
 * JUnit test threads are not. This is the standard Jetpack rule for this exact
 * situation — see androidx.arch.core:core-testing.
 */
public class StudentRepositoryTest {

    @Rule
    public InstantTaskExecutorRule instantExecutorRule = new InstantTaskExecutorRule();

    private FakeStudentRepository repository;

    @Before
    public void setUp() {
        repository = new FakeStudentRepository();
    }

    @Test
    public void newGroup_isNotFull() {
        boolean[] result = new boolean[1];
        repository.isGroupFullAsync("G01", isFull -> result[0] = isFull);
        assertFalse("A group with no students should not be full", result[0]);
    }

    @Test
    public void groupWith15ActiveStudents_isFull() {
        for (int i = 0; i < 15; i++) {
            StudentEntity student = new StudentEntity(
                    "id-" + i, String.format("%09d", i), "Student " + i, "Computer Science", "G01");
            repository.seedStudent(student);
        }

        boolean[] result = new boolean[1];
        repository.isGroupFullAsync("G01", isFull -> result[0] = isFull);
        assertTrue("A group with exactly 15 active students should be reported full", result[0]);
    }

    @Test
    public void groupWith14ActiveStudents_isNotFull() {
        for (int i = 0; i < 14; i++) {
            StudentEntity student = new StudentEntity(
                    "id-" + i, String.format("%09d", i), "Student " + i, "Computer Science", "G02");
            repository.seedStudent(student);
        }

        boolean[] result = new boolean[1];
        repository.isGroupFullAsync("G02", isFull -> result[0] = isFull);
        assertFalse("14 active students should still leave room for one more (max 15)", result[0]);
    }

    @Test
    public void unassignedGroup_isNeverFull() {
        boolean[] result = new boolean[1];
        repository.isGroupFullAsync("Unassigned", isFull -> result[0] = isFull);
        assertFalse(result[0]);
    }

    @Test
    public void savedStudent_isRetrievableByRoster() {
        StudentEntity student = new StudentEntity("id-x", "001234567", "Jane Doe", "Computer Science", "G03");
        repository.saveStudent(student);

        assertEquals(1, repository.getRoster("", "All Programs", "All Groups").getValue().size());
    }

    @Test
    public void registrationDraft_savedThenLoaded_returnsWhatWasSaved() {
        com.example.mustudentsync.model.RegistrationDraftEntity draft =
                new com.example.mustudentsync.model.RegistrationDraftEntity();
        draft.fullName = "Draft Student";
        draft.studentNumber = "000111222";
        draft.program = "Information Technology";
        draft.claimCode = "LAB-TEST";

        repository.saveRegistrationDraft(draft);

        com.example.mustudentsync.model.RegistrationDraftEntity[] loaded =
                new com.example.mustudentsync.model.RegistrationDraftEntity[1];
        repository.loadRegistrationDraft(d -> loaded[0] = d);

        assertEquals("Draft Student", loaded[0].fullName);
        assertEquals("LAB-TEST", loaded[0].claimCode);
    }
}