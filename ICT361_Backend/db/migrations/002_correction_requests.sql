-- Migration 002: student-number correction requests (student asks, lecturer approves).
-- Run once on your existing database, e.g. in MySQL Workbench or:
--   node scripts/run_sql.js db/migrations/002_correction_requests.sql
-- Safe to run twice (IF NOT EXISTS).
CREATE TABLE IF NOT EXISTS `change_requests` (
  `request_id`       char(36)   NOT NULL,
  `student_id`       char(36)   NOT NULL,
  `requested_number` char(9)    NOT NULL,
  `status`           enum('PENDING','APPROVED','REJECTED') NOT NULL DEFAULT 'PENDING',
  `note`             varchar(255) DEFAULT NULL,
  `created_at`       timestamp  NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `resolved_at`      timestamp  NULL DEFAULT NULL,
  `resolved_by`      char(36)   DEFAULT NULL,
  PRIMARY KEY (`request_id`),
  KEY `idx_change_requests_status` (`status`, `created_at`),
  KEY `idx_change_requests_student` (`student_id`, `status`),
  CONSTRAINT `fk_change_request_student` FOREIGN KEY (`student_id`) REFERENCES `students` (`student_id`),
  CONSTRAINT `chk_requested_number` CHECK (regexp_like(`requested_number`, '^[0-9]{9}$'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
