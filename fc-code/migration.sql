-- ============================================
-- SYSU Date 账号系统数据库迁移脚本 v2.0.0
-- 创建时间: 2026-04-29
-- 说明: 添加账号系统支持，保留原有数据
-- ============================================

-- 1. 创建账号表
CREATE TABLE IF NOT EXISTS `accounts` (
    `id` INT AUTO_INCREMENT PRIMARY KEY,
    `email` VARCHAR(255) NOT NULL UNIQUE,
    `username` VARCHAR(50) NOT NULL UNIQUE,
    `password_hash` VARCHAR(255) NOT NULL,
    `email_verified` BOOLEAN DEFAULT FALSE,
    `created_at` TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    `last_login_at` TIMESTAMP NULL,
    INDEX `idx_email` (`email`),
    INDEX `idx_username` (`username`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 2. 修改 users 表，添加 account_id 关联字段
ALTER TABLE `users` 
ADD COLUMN `account_id` INT NULL AFTER `id`,
ADD INDEX `idx_account_id` (`account_id`);

-- 3. 添加外键约束（可选，如果 users 表已存在数据可能失败）
-- ALTER TABLE `users` 
-- ADD CONSTRAINT `fk_account` FOREIGN KEY (`account_id`) REFERENCES `accounts`(`id`) ON DELETE SET NULL;

-- 4. 验证迁移结果
SELECT 'Migration completed successfully' AS status;
SELECT COUNT(*) AS total_accounts FROM accounts;
SELECT COUNT(*) AS total_users FROM users;
SELECT COUNT(*) AS linked_users FROM users WHERE account_id IS NOT NULL;
