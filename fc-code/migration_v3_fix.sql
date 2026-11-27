-- FancyU users 表自动补全缺失列
-- 使用存储过程自动检测，只添加不存在的列，不会报错

DELIMITER //

CREATE PROCEDURE IF NOT EXISTS add_column_if_not_exists(
    IN p_table VARCHAR(64),
    IN p_column VARCHAR(64),
    IN p_definition TEXT
)
BEGIN
    DECLARE col_count INT;
    SELECT COUNT(*) INTO col_count
    FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = p_table
      AND column_name = p_column;
    IF col_count = 0 THEN
        SET @sql = CONCAT('ALTER TABLE `', p_table, '` ADD COLUMN `', p_column, '` ', p_definition);
        PREPARE stmt FROM @sql;
        EXECUTE stmt;
        DEALLOCATE PREPARE stmt;
    END IF;
END //

DELIMITER ;

-- 补全 users 表所有可能缺失的列
CALL add_column_if_not_exists('users', 'sex_choice', 'VARCHAR(10) NULL');
CALL add_column_if_not_exists('users', 'sex_prefer', 'VARCHAR(50) NULL');
CALL add_column_if_not_exists('users', 'experience', 'VARCHAR(50) NULL');
CALL add_column_if_not_exists('users', 'ex_prefer', 'TEXT NULL');
CALL add_column_if_not_exists('users', 'future_plan', 'TEXT NULL');
CALL add_column_if_not_exists('users', 'prefer_major', 'TEXT NULL');
CALL add_column_if_not_exists('users', 'campus', 'VARCHAR(50) NULL');
CALL add_column_if_not_exists('users', 'campus_prefer', 'TEXT NULL');
CALL add_column_if_not_exists('users', 'school', 'VARCHAR(100) NULL');
CALL add_column_if_not_exists('users', 'hometown', 'VARCHAR(100) NULL');
CALL add_column_if_not_exists('users', 'constellation', 'VARCHAR(20) NULL');
CALL add_column_if_not_exists('users', 'constellation_prefer', 'TEXT NULL');
CALL add_column_if_not_exists('users', 'mbti', 'VARCHAR(10) NULL');
CALL add_column_if_not_exists('users', 'mbti_prefer', 'TEXT NULL');
CALL add_column_if_not_exists('users', 'interests', 'TEXT NULL');
CALL add_column_if_not_exists('users', 'interest_custom', 'TEXT NULL');
CALL add_column_if_not_exists('users', 'height', 'INT NULL');
CALL add_column_if_not_exists('users', 'height_min', 'INT NULL');
CALL add_column_if_not_exists('users', 'height_max', 'INT NULL');
CALL add_column_if_not_exists('users', 'beauty_self', 'INT NULL');
CALL add_column_if_not_exists('users', 'beauty_prefer_min', 'INT NULL');
CALL add_column_if_not_exists('users', 'body_type', 'VARCHAR(20) NULL');
CALL add_column_if_not_exists('users', 'fitness_level', 'VARCHAR(20) NULL');
CALL add_column_if_not_exists('users', 'body_prefer', 'TEXT NULL');
CALL add_column_if_not_exists('users', 'fitness_prefer', 'TEXT NULL');
CALL add_column_if_not_exists('users', 'q1_co_dependent', 'VARCHAR(20) NULL');
CALL add_column_if_not_exists('users', 'q2_conflict', 'VARCHAR(20) NULL');
CALL add_column_if_not_exists('users', 'q3_ritual', 'VARCHAR(20) NULL');
CALL add_column_if_not_exists('users', 'q4_consumption', 'VARCHAR(20) NULL');
CALL add_column_if_not_exists('users', 'q5_life_focus', 'VARCHAR(20) NULL');
CALL add_column_if_not_exists('users', 'q6_emotion', 'VARCHAR(20) NULL');
CALL add_column_if_not_exists('users', 'agree_topics', 'TEXT NULL');
CALL add_column_if_not_exists('users', 'self_description', 'TEXT NULL');
CALL add_column_if_not_exists('users', 'ideal_description', 'TEXT NULL');
CALL add_column_if_not_exists('users', 'photos', 'TEXT NULL');
CALL add_column_if_not_exists('users', 'display_self', 'TEXT NULL');
CALL add_column_if_not_exists('users', 'feedback', 'TEXT NULL');
CALL add_column_if_not_exists('users', 'avatar', 'VARCHAR(500) NULL');
CALL add_column_if_not_exists('users', 'contact_entries', 'TEXT NULL');
CALL add_column_if_not_exists('users', 'expect_schools', 'TEXT NULL');
CALL add_column_if_not_exists('users', 'expect_school_campuses', 'TEXT NULL');
CALL add_column_if_not_exists('users', 'expect_quick', 'VARCHAR(50) NULL');
CALL add_column_if_not_exists('users', 'major_prefer', 'TEXT NULL');
CALL add_column_if_not_exists('users', 'online_offline', 'VARCHAR(20) NULL');
CALL add_column_if_not_exists('users', 'friend_goal', 'TEXT NULL');
CALL add_column_if_not_exists('users', 'ideal_friend', 'TEXT NULL');
CALL add_column_if_not_exists('users', 'special_request', 'TEXT NULL');

-- 验证
SELECT 'users 表列补全完成' AS status;
