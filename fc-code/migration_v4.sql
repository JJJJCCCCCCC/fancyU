-- ============================================
-- FancyU 交友问卷独立表迁移脚本 v4.0.0
-- 创建时间: 2026-05-20
-- 说明: 创建交友问卷独立表 friend_survey
-- ============================================

-- 1. 创建交友问卷独立表
CREATE TABLE IF NOT EXISTS `friend_survey` (
    `id` INT AUTO_INCREMENT PRIMARY KEY,
    `account_id` INT NULL,
    `serial_number` VARCHAR(50) NOT NULL UNIQUE,
    `email` VARCHAR(255) NOT NULL,
    `contact_email` VARCHAR(255) NULL,
    `survey_type` VARCHAR(20) DEFAULT 'friend',

    -- 基本信息
    `nickname` VARCHAR(50) NULL,
    `sex` VARCHAR(10) NULL,
    `grade` VARCHAR(20) NULL,
    `major` VARCHAR(50) NULL,
    `campus` VARCHAR(50) NULL,
    `hometown` VARCHAR(100) NULL,
    `mbti` VARCHAR(10) NULL,

    -- 交友偏好
    `sex_prefer` VARCHAR(50) NULL,
    `grade_prefer` TEXT NULL,
    `major_prefer` TEXT NULL,
    `online_offline` VARCHAR(20) NULL,
    `friend_goal` TEXT NULL,

    -- 兴趣与描述
    `interests` TEXT NULL,
    `interest_custom` TEXT NULL,
    `self_description` TEXT NULL,
    `ideal_friend` TEXT NULL,
    `special_request` TEXT NULL,

    -- 性格自评滑块
    `personality_self_1` INT NULL,
    `personality_self_2` INT NULL,
    `personality_self_3` INT NULL,
    `personality_self_4` INT NULL,
    `personality_self_5` INT NULL,

    -- 性格期望滑块
    `personality_expect_1` INT NULL,
    `personality_expect_2` INT NULL,
    `personality_expect_3` INT NULL,
    `personality_expect_4` INT NULL,
    `personality_expect_5` INT NULL,
    `personality_expect_1_no_pref` BOOLEAN DEFAULT FALSE,
    `personality_expect_2_no_pref` BOOLEAN DEFAULT FALSE,
    `personality_expect_3_no_pref` BOOLEAN DEFAULT FALSE,
    `personality_expect_4_no_pref` BOOLEAN DEFAULT FALSE,
    `personality_expect_5_no_pref` BOOLEAN DEFAULT FALSE,

    -- 期望学校/校区
    `expect_schools` TEXT NULL,
    `expect_school_campuses` TEXT NULL,
    `expect_quick` VARCHAR(50) NULL,

    -- 联系方式
    `contact_entries` TEXT NULL,

    -- 展示与反馈
    `photos` TEXT NULL,
    `display_self` TEXT NULL,
    `feedback` TEXT NULL,
    `avatar` VARCHAR(500) NULL,

    -- 时间戳
    `created_at` TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    `updated_at` TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

    INDEX `idx_serial_number` (`serial_number`),
    INDEX `idx_account_id` (`account_id`),
    INDEX `idx_email` (`email`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 2. 验证
SELECT 'friend_survey 表创建成功' AS status;
