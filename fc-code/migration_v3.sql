-- ============================================
-- FancyU 多学校支持数据库迁移脚本 v3.0.0
-- 创建时间: 2026-05-01
-- 说明: 添加多学校支持、交友问卷、匹配结果
-- ============================================

-- 1. accounts 表添加 school 字段
ALTER TABLE `accounts`
ADD COLUMN `school` VARCHAR(100) NULL AFTER `email_verified`,
ADD COLUMN `join_matching` BOOLEAN DEFAULT TRUE AFTER `school`;

-- 2. users 表添加新字段
ALTER TABLE `users`
ADD COLUMN `user_id` VARCHAR(100) NULL AFTER `account_id`,
ADD COLUMN `my_school` VARCHAR(100) NULL AFTER `hometown`,
ADD COLUMN `school_prefer` TEXT NULL AFTER `my_school`,
ADD COLUMN `contact_info` TEXT NULL AFTER `contact_email`,
ADD COLUMN `survey_type` VARCHAR(20) DEFAULT 'love' AFTER `contact_info`,
ADD INDEX `idx_user_id` (`user_id`),
ADD INDEX `idx_account_id_survey` (`account_id`, `survey_type`);

-- 3. 创建匹配结果表
CREATE TABLE IF NOT EXISTS `matches` (
    `id` INT AUTO_INCREMENT PRIMARY KEY,
    `account_id` INT NOT NULL,
    `matched_account_id` INT NOT NULL,
    `survey_type` VARCHAR(20) DEFAULT 'love',
    `match_data` JSON NULL,
    `match_score` DECIMAL(5,2) NULL,
    `contact_exchanged` BOOLEAN DEFAULT FALSE,
    `created_at` TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    INDEX `idx_account_id` (`account_id`),
    INDEX `idx_matched_account_id` (`matched_account_id`),
    INDEX `idx_account_survey` (`account_id`, `survey_type`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 4. 为已有账号填充 school 字段
UPDATE `accounts` SET `school` = '中山大学' WHERE `email` LIKE '%@mail.sysu.edu.cn' OR `email` LIKE '%@mail2.sysu.edu.cn';
UPDATE `accounts` SET `school` = '华南理工大学' WHERE `email` LIKE '%@scut.edu.cn';

-- 5. 为已有用户填充 my_school 字段
UPDATE `users` SET `my_school` = '中山大学' WHERE `email` LIKE '%@mail.sysu.edu.cn' OR `email` LIKE '%@mail2.sysu.edu.cn';
UPDATE `users` SET `my_school` = '华南理工大学' WHERE `email` LIKE '%@scut.edu.cn';

-- 6. 为已有用户设置 survey_type 默认值
UPDATE `users` SET `survey_type` = 'love' WHERE `survey_type` IS NULL;

-- 7. 验证迁移结果
SELECT 'Migration v3.0.0 completed successfully' AS status;
SELECT COUNT(*) AS total_accounts FROM accounts;
SELECT COUNT(*) AS accounts_with_school FROM accounts WHERE school IS NOT NULL;
SELECT COUNT(*) AS total_users FROM users;
SELECT COUNT(*) AS users_with_my_school FROM users WHERE my_school IS NOT NULL;
SELECT COUNT(*) AS love_surveys FROM users WHERE survey_type = 'love' OR survey_type IS NULL;
SELECT COUNT(*) AS friend_surveys FROM users WHERE survey_type = 'friend';
