-- Recovery e-mail for "Forgot password?" and a wrong-attempt counter for reset codes.
-- The server also adds these by itself on first use, so running this file is optional.
-- (Run it only once: MySQL has no ADD COLUMN IF NOT EXISTS.)
ALTER TABLE `accounts` ADD COLUMN `email` varchar(254) NULL DEFAULT NULL;
ALTER TABLE `password_reset_codes` ADD COLUMN `wrong_attempts` int NOT NULL DEFAULT 0;
