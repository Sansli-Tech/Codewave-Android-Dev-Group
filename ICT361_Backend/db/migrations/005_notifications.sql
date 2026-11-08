-- Group requests that wait for a lecturer, in-app notifications, announcements and phone
-- push tokens. The server also creates these tables by itself at start-up.
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
