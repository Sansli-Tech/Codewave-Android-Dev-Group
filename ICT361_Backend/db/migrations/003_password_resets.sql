-- Password reset codes ("Forgot password?"). The server also creates this table by itself
-- on first use, so running this file is optional.
CREATE TABLE IF NOT EXISTS `password_reset_codes` (
  `account_id`  char(36)     NOT NULL,
  `code_hash`   varchar(255) NOT NULL,
  `expires_at`  timestamp    NOT NULL,
  `used_at`     timestamp    NULL DEFAULT NULL,
  `created_by`  char(36)     DEFAULT NULL,
  `created_at`  timestamp    NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`account_id`),
  CONSTRAINT `fk_reset_account` FOREIGN KEY (`account_id`) REFERENCES `accounts` (`account_id`)
);
