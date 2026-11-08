-- ICT361 StudentSync - database structure (exported from MySQL with SHOW CREATE TABLE).
-- No student data. Create the database first: CREATE DATABASE ict361_registration;
SET FOREIGN_KEY_CHECKS = 0;

CREATE TABLE `accounts` (
  `account_id` char(36) NOT NULL,
  `username` varchar(100) NOT NULL,
  `password_hash` varchar(255) NOT NULL,
  `role` enum('STUDENT','LECTURER') NOT NULL,
  `student_id` char(36) DEFAULT NULL,
  `is_active` tinyint(1) NOT NULL DEFAULT '1',
  `created_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`account_id`),
  UNIQUE KEY `username` (`username`),
  UNIQUE KEY `student_id` (`student_id`),
  CONSTRAINT `fk_account_student` FOREIGN KEY (`student_id`) REFERENCES `students` (`student_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `lab_groups` (
  `group_id` int NOT NULL AUTO_INCREMENT,
  `group_code` varchar(10) NOT NULL,
  `capacity` int NOT NULL DEFAULT '15',
  PRIMARY KEY (`group_id`),
  UNIQUE KEY `group_code` (`group_code`),
  CONSTRAINT `chk_lab_group_capacity` CHECK ((`capacity` between 1 and 16))
) ENGINE=InnoDB AUTO_INCREMENT=15 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `lecturer_profiles` (
  `account_id` char(36) NOT NULL,
  `full_name` varchar(100) NOT NULL,
  `staff_id` varchar(30) NOT NULL,
  `school` varchar(150) NOT NULL,
  `department` varchar(150) NOT NULL,
  `created_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`account_id`),
  UNIQUE KEY `staff_id` (`staff_id`),
  CONSTRAINT `fk_lecturer_profile_account` FOREIGN KEY (`account_id`) REFERENCES `accounts` (`account_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `operation_receipts` (
  `operation_id` char(36) NOT NULL,
  `account_id` char(36) NOT NULL,
  `request_hash` char(64) NOT NULL,
  `response_json` json NOT NULL,
  `created_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`operation_id`),
  KEY `idx_receipt_account` (`account_id`,`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `programmes` (
  `programme_id` int NOT NULL AUTO_INCREMENT,
  `programme_code` varchar(10) NOT NULL,
  `programme_name` varchar(100) NOT NULL,
  PRIMARY KEY (`programme_id`),
  UNIQUE KEY `programme_code` (`programme_code`)
) ENGINE=InnoDB AUTO_INCREMENT=24 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `student_claim_codes` (
  `claim_id` int NOT NULL AUTO_INCREMENT,
  `student_id` char(36) NOT NULL,
  `claim_code_hash` varchar(255) NOT NULL,
  `expires_at` timestamp NULL DEFAULT NULL,
  `claimed_at` timestamp NULL DEFAULT NULL,
  PRIMARY KEY (`claim_id`),
  UNIQUE KEY `student_id` (`student_id`),
  CONSTRAINT `fk_claim_student` FOREIGN KEY (`student_id`) REFERENCES `students` (`student_id`)
) ENGINE=InnoDB AUTO_INCREMENT=3 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `students` (
  `student_id` char(36) NOT NULL,
  `student_number` char(9) NOT NULL,
  `student_name` varchar(100) NOT NULL,
  `programme_id` int NOT NULL,
  `group_id` int DEFAULT NULL,
  `record_version` int unsigned NOT NULL DEFAULT '1',
  `is_deleted` tinyint(1) NOT NULL DEFAULT '0',
  `deleted_at` timestamp NULL DEFAULT NULL,
  `created_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`student_id`),
  UNIQUE KEY `student_number` (`student_number`),
  KEY `idx_student_group` (`group_id`,`is_deleted`),
  KEY `idx_student_programme` (`programme_id`,`is_deleted`),
  CONSTRAINT `fk_student_group` FOREIGN KEY (`group_id`) REFERENCES `lab_groups` (`group_id`),
  CONSTRAINT `fk_student_programme` FOREIGN KEY (`programme_id`) REFERENCES `programmes` (`programme_id`),
  CONSTRAINT `students_chk_1` CHECK ((char_length(`student_number`) = 9)),
  CONSTRAINT `students_chk_2` CHECK (regexp_like(`student_number`,_utf8mb4'^[0-9]{9}$')),
  CONSTRAINT `students_chk_3` CHECK ((char_length(trim(`student_name`)) between 2 and 100))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `sync_changes` (
  `change_id` bigint NOT NULL AUTO_INCREMENT,
  `student_id` char(36) NOT NULL,
  `record_version` int unsigned NOT NULL,
  `change_type` enum('UPSERT','DELETE') NOT NULL,
  `created_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`change_id`),
  KEY `idx_sync_student_version` (`student_id`,`record_version`)
) ENGINE=InnoDB AUTO_INCREMENT=240 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- programmes: AGE, BCH, BIO, BMS, CHM, CIV, CS, DS, EEE, GEO, ICTED, IND, IT, LAB, MBC, MEC, MST, NRM, PHA, PHM, PHY, STA, WTE
-- groups: G01(16), G02(15), G03(15), G04(16), G05(15), G06(15), G07(15), G08(15), G09(16), G10(16), G11(16), G12(15), G13(15), G14(15)

-- Added by migration 002 (student-number correction requests)
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

-- Added by migration 003 (forgot password)
CREATE TABLE IF NOT EXISTS `password_reset_codes` (
  `account_id`  char(36)     NOT NULL,
  `code_hash`   varchar(255) NOT NULL,
  `expires_at`  timestamp    NOT NULL,
  `used_at`     timestamp    NULL DEFAULT NULL,
  `created_by`  char(36)     DEFAULT NULL,
  `created_at`  timestamp    NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `wrong_attempts` int      NOT NULL DEFAULT 0,
  PRIMARY KEY (`account_id`),
  CONSTRAINT `fk_reset_account` FOREIGN KEY (`account_id`) REFERENCES `accounts` (`account_id`)
);

-- Added by migration 004 (recovery e-mail)
ALTER TABLE `accounts` ADD COLUMN `email` varchar(254) NULL DEFAULT NULL;

-- Added by migration 005 (notifications)
CREATE TABLE IF NOT EXISTS `group_requests` (
  `request_id`  char(36)    NOT NULL,
  `student_id`  char(36)    NOT NULL,
  `group_code`  varchar(10) NOT NULL,
  `status`      enum('PENDING','APPROVED','REJECTED','CANCELLED') NOT NULL DEFAULT 'PENDING',
  `note`        varchar(255) DEFAULT NULL,
  `created_at`  timestamp   NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `resolved_at` timestamp   NULL DEFAULT NULL,
  `resolved_by` char(36)    DEFAULT NULL,
  PRIMARY KEY (`request_id`),
  KEY `idx_group_requests_student` (`student_id`,`status`),
  KEY `idx_group_requests_status` (`status`,`created_at`),
  CONSTRAINT `fk_group_request_student` FOREIGN KEY (`student_id`) REFERENCES `students` (`student_id`)
);

CREATE TABLE IF NOT EXISTS `notifications` (
  `notification_id` bigint       NOT NULL AUTO_INCREMENT,
  `account_id`      char(36)     NOT NULL,
  `kind`            varchar(30)  NOT NULL,
  `title`           varchar(120) NOT NULL,
  `body`            varchar(1000) NOT NULL,
  `ref_id`          char(36)     DEFAULT NULL,
  `created_at`      timestamp    NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `read_at`         timestamp    NULL DEFAULT NULL,
  PRIMARY KEY (`notification_id`),
  KEY `idx_notifications_account` (`account_id`,`read_at`,`created_at`),
  CONSTRAINT `fk_notification_account` FOREIGN KEY (`account_id`) REFERENCES `accounts` (`account_id`)
);

CREATE TABLE IF NOT EXISTS `announcements` (
  `announcement_id` char(36)     NOT NULL,
  `author_id`       char(36)     NOT NULL,
  `audience`        varchar(10)  NOT NULL,
  `title`           varchar(120) NOT NULL,
  `body`            varchar(1000) NOT NULL,
  `recipients`      int          NOT NULL DEFAULT 0,
  `created_at`      timestamp    NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`announcement_id`)
);

CREATE TABLE IF NOT EXISTS `device_tokens` (
  `token`      varchar(255) NOT NULL,
  `account_id` char(36)     NOT NULL,
  `signed_in`  tinyint(1)   NOT NULL DEFAULT 1,
  `updated_at` timestamp    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`token`),
  KEY `idx_device_account` (`account_id`)
);

SET FOREIGN_KEY_CHECKS = 1;
