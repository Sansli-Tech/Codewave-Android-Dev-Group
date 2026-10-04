package com.example.mustudentsync.data.local;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Update;

import com.example.mustudentsync.model.StudentEntity;

import java.util.List;

@Dao
public interface StudentDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insert(StudentEntity student);

    @Update
    void update(StudentEntity student);

    @Delete
    void delete(StudentEntity student);

    @Query("SELECT * FROM students WHERE is_active = 1 ORDER BY full_name ASC")
    LiveData<List<StudentEntity>> getAllActiveStudents();

    @Query("SELECT * FROM students WHERE student_id = :studentId LIMIT 1")
    LiveData<StudentEntity> getStudentById(String studentId);

    @Query("SELECT * FROM students WHERE student_number = :studentNumber LIMIT 1")
    StudentEntity findByStudentNumber(String studentNumber); // used for local duplicate pre-check

    @Query("SELECT * FROM students WHERE is_active = 1 AND " +
            "(:program = 'All Programs' OR program = :program) AND " +
            "(:group = 'All Groups' OR lab_group = :group) AND " +
            "(full_name LIKE '%' || :query || '%' OR student_number LIKE '%' || :query || '%') " +
            "ORDER BY full_name ASC")
    LiveData<List<StudentEntity>> searchAndFilter(String query, String program, String group);

    @Query("SELECT COUNT(*) FROM students WHERE lab_group = :group AND is_active = 1")
    int countActiveInGroup(String group); // enforces the 15-member cap locally, server enforces it authoritatively
}