CREATE TABLE IF NOT EXISTS accounts (
    id INT AUTO_INCREMENT PRIMARY KEY,
    email VARCHAR(255) NOT NULL UNIQUE,
    username VARCHAR(50) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    email_verified BOOLEAN NOT NULL DEFAULT FALSE,
    role VARCHAR(20) NOT NULL DEFAULT 'USER',
    token_version INT NOT NULL DEFAULT 0,
    created_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    last_login_at TIMESTAMP(3) NULL
);

CREATE TABLE IF NOT EXISTS users (
    id INT AUTO_INCREMENT PRIMARY KEY,
    account_id INT NULL,
    serial_number VARCHAR(50) NOT NULL UNIQUE,
    email VARCHAR(255) NULL,
    contact_email VARCHAR(255) NULL,
    contact_entries JSON NULL,
    survey_type VARCHAR(20) NOT NULL DEFAULT 'love',
    school VARCHAR(100) NULL,
    nickname VARCHAR(50) NULL,
    sex VARCHAR(20) NULL,
    sex_choice VARCHAR(50) NULL,
    sex_prefer JSON NULL,
    grade VARCHAR(30) NULL,
    grade_prefer JSON NULL,
    relationship_goal VARCHAR(100) NULL,
    experience VARCHAR(100) NULL,
    ex_prefer JSON NULL,
    future_plan TEXT NULL,
    major VARCHAR(100) NULL,
    prefer_major JSON NULL,
    major_prefer JSON NULL,
    campus VARCHAR(100) NULL,
    hometown VARCHAR(100) NULL,
    constellation VARCHAR(30) NULL,
    constellation_prefer JSON NULL,
    mbti VARCHAR(10) NULL,
    mbti_prefer JSON NULL,
    interests JSON NULL,
    interest_custom TEXT NULL,
    height INT NULL,
    height_min INT NULL,
    height_max INT NULL,
    beauty_self INT NULL,
    beauty_prefer_min INT NULL,
    body_type VARCHAR(50) NULL,
    fitness_level VARCHAR(50) NULL,
    body_prefer JSON NULL,
    fitness_prefer JSON NULL,
    q1_co_dependent VARCHAR(100) NULL,
    q2_conflict VARCHAR(100) NULL,
    q3_ritual VARCHAR(100) NULL,
    q4_consumption VARCHAR(100) NULL,
    q5_life_focus VARCHAR(100) NULL,
    q6_emotion VARCHAR(100) NULL,
    agree_topics JSON NULL,
    self_description TEXT NULL,
    ideal_description TEXT NULL,
    photos JSON NULL,
    display_self JSON NULL,
    feedback TEXT NULL,
    avatar VARCHAR(500) NULL,
    online_offline VARCHAR(30) NULL,
    friend_goal TEXT NULL,
    ideal_friend TEXT NULL,
    special_request TEXT NULL,
    personality_self_1 INT NULL,
    personality_self_2 INT NULL,
    personality_self_3 INT NULL,
    personality_self_4 INT NULL,
    personality_self_5 INT NULL,
    personality_expect_1 INT NULL,
    personality_expect_2 INT NULL,
    personality_expect_3 INT NULL,
    personality_expect_4 INT NULL,
    personality_expect_5 INT NULL,
    personality_expect_1_no_pref BOOLEAN NOT NULL DEFAULT FALSE,
    personality_expect_2_no_pref BOOLEAN NOT NULL DEFAULT FALSE,
    personality_expect_3_no_pref BOOLEAN NOT NULL DEFAULT FALSE,
    personality_expect_4_no_pref BOOLEAN NOT NULL DEFAULT FALSE,
    personality_expect_5_no_pref BOOLEAN NOT NULL DEFAULT FALSE,
    expect_schools JSON NULL,
    expect_school_campuses JSON NULL,
    expect_quick VARCHAR(100) NULL,
    created_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3)
        ON UPDATE CURRENT_TIMESTAMP(3),
    INDEX idx_users_account_type (account_id, survey_type),
    INDEX idx_users_created (created_at)
);

CREATE TABLE IF NOT EXISTS friend_survey LIKE users;

CREATE TABLE IF NOT EXISTS account_preferences (
    account_id INT PRIMARY KEY,
    join_matching BOOLEAN NOT NULL DEFAULT TRUE,
    join_friend_matching BOOLEAN NOT NULL DEFAULT TRUE,
    updated_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3)
        ON UPDATE CURRENT_TIMESTAMP(3)
);

CREATE TABLE IF NOT EXISTS match_history (
    id INT AUTO_INCREMENT PRIMARY KEY,
    pair_key VARCHAR(80) NULL,
    account_id INT NOT NULL,
    match_account_id INT NOT NULL,
    survey_type VARCHAR(20) NOT NULL DEFAULT 'love',
    match_round INT NOT NULL DEFAULT 1,
    match_data JSON NULL,
    user_agreed BOOLEAN NOT NULL DEFAULT FALSE,
    match_agreed BOOLEAN NOT NULL DEFAULT FALSE,
    contact_exchanged BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3)
        ON UPDATE CURRENT_TIMESTAMP(3),
    INDEX idx_match_owner_created (account_id, created_at),
    INDEX idx_match_peer (match_account_id),
    INDEX idx_match_pair (pair_key)
);

CREATE TABLE IF NOT EXISTS complaints (
    id INT AUTO_INCREMENT PRIMARY KEY,
    reporter_account_id INT NULL,
    reporter_email VARCHAR(255) NULL,
    target_type VARCHAR(20) NOT NULL DEFAULT 'other',
    target_id VARCHAR(255) NULL,
    reason TEXT NOT NULL,
    description TEXT NULL,
    contact_email VARCHAR(255) NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'pending',
    admin_reply TEXT NULL,
    created_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3)
        ON UPDATE CURRENT_TIMESTAMP(3),
    INDEX idx_complaint_status_created (status, created_at),
    INDEX idx_complaint_reporter (reporter_account_id)
);

CREATE TABLE IF NOT EXISTS chat_sessions (
    id VARCHAR(64) PRIMARY KEY,
    title VARCHAR(255) NOT NULL DEFAULT 'New chat',
    messages JSON NOT NULL,
    created_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3)
        ON UPDATE CURRENT_TIMESTAMP(3),
    INDEX idx_chat_updated (updated_at)
);

CREATE TABLE IF NOT EXISTS request_idempotency (
    idempotency_key VARCHAR(120) PRIMARY KEY,
    account_id INT NULL,
    operation VARCHAR(80) NOT NULL,
    response_payload JSON NOT NULL,
    created_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    expires_at TIMESTAMP(3) NOT NULL,
    INDEX idx_idempotency_expiry (expires_at)
);

DELIMITER //

DROP PROCEDURE IF EXISTS sysudate_add_column //
CREATE PROCEDURE sysudate_add_column(
    IN target_table VARCHAR(64),
    IN target_column VARCHAR(64),
    IN column_definition TEXT
)
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM information_schema.columns
        WHERE table_schema = DATABASE()
          AND table_name = target_table
          AND column_name = target_column
    ) THEN
        SET @ddl = CONCAT(
            'ALTER TABLE `',
            target_table,
            '` ADD COLUMN `',
            target_column,
            '` ',
            column_definition
        );
        PREPARE statement FROM @ddl;
        EXECUTE statement;
        DEALLOCATE PREPARE statement;
    END IF;
END //

DELIMITER ;

CALL sysudate_add_column('accounts', 'email_verified',
    'BOOLEAN NOT NULL DEFAULT FALSE');
CALL sysudate_add_column('accounts', 'role',
    'VARCHAR(20) NOT NULL DEFAULT ''USER''');
CALL sysudate_add_column('accounts', 'token_version',
    'INT NOT NULL DEFAULT 0');
CALL sysudate_add_column('accounts', 'created_at',
    'TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3)');
CALL sysudate_add_column('accounts', 'last_login_at', 'TIMESTAMP(3) NULL');

CALL sysudate_add_column('users', 'account_id', 'INT NULL');
CALL sysudate_add_column('users', 'contact_email', 'VARCHAR(255) NULL');
CALL sysudate_add_column('users', 'contact_entries', 'JSON NULL');
CALL sysudate_add_column('users', 'survey_type',
    'VARCHAR(20) NOT NULL DEFAULT ''love''');
CALL sysudate_add_column('users', 'school', 'VARCHAR(100) NULL');
CALL sysudate_add_column('users', 'sex', 'VARCHAR(20) NULL');
CALL sysudate_add_column('users', 'sex_choice', 'VARCHAR(50) NULL');
CALL sysudate_add_column('users', 'sex_prefer', 'JSON NULL');
CALL sysudate_add_column('users', 'grade', 'VARCHAR(30) NULL');
CALL sysudate_add_column('users', 'grade_prefer', 'JSON NULL');
CALL sysudate_add_column('users', 'relationship_goal', 'VARCHAR(100) NULL');
CALL sysudate_add_column('users', 'experience', 'VARCHAR(100) NULL');
CALL sysudate_add_column('users', 'ex_prefer', 'JSON NULL');
CALL sysudate_add_column('users', 'future_plan', 'TEXT NULL');
CALL sysudate_add_column('users', 'major', 'VARCHAR(100) NULL');
CALL sysudate_add_column('users', 'prefer_major', 'JSON NULL');
CALL sysudate_add_column('users', 'major_prefer', 'JSON NULL');
CALL sysudate_add_column('users', 'campus', 'VARCHAR(100) NULL');
CALL sysudate_add_column('users', 'hometown', 'VARCHAR(100) NULL');
CALL sysudate_add_column('users', 'constellation', 'VARCHAR(30) NULL');
CALL sysudate_add_column('users', 'constellation_prefer', 'JSON NULL');
CALL sysudate_add_column('users', 'mbti', 'VARCHAR(10) NULL');
CALL sysudate_add_column('users', 'mbti_prefer', 'JSON NULL');
CALL sysudate_add_column('users', 'interests', 'JSON NULL');
CALL sysudate_add_column('users', 'interest_custom', 'TEXT NULL');
CALL sysudate_add_column('users', 'height', 'INT NULL');
CALL sysudate_add_column('users', 'height_min', 'INT NULL');
CALL sysudate_add_column('users', 'height_max', 'INT NULL');
CALL sysudate_add_column('users', 'beauty_self', 'INT NULL');
CALL sysudate_add_column('users', 'beauty_prefer_min', 'INT NULL');
CALL sysudate_add_column('users', 'body_type', 'VARCHAR(50) NULL');
CALL sysudate_add_column('users', 'fitness_level', 'VARCHAR(50) NULL');
CALL sysudate_add_column('users', 'body_prefer', 'JSON NULL');
CALL sysudate_add_column('users', 'fitness_prefer', 'JSON NULL');
CALL sysudate_add_column('users', 'q1_co_dependent', 'VARCHAR(100) NULL');
CALL sysudate_add_column('users', 'q2_conflict', 'VARCHAR(100) NULL');
CALL sysudate_add_column('users', 'q3_ritual', 'VARCHAR(100) NULL');
CALL sysudate_add_column('users', 'q4_consumption', 'VARCHAR(100) NULL');
CALL sysudate_add_column('users', 'q5_life_focus', 'VARCHAR(100) NULL');
CALL sysudate_add_column('users', 'q6_emotion', 'VARCHAR(100) NULL');
CALL sysudate_add_column('users', 'agree_topics', 'JSON NULL');
CALL sysudate_add_column('users', 'self_description', 'TEXT NULL');
CALL sysudate_add_column('users', 'ideal_description', 'TEXT NULL');
CALL sysudate_add_column('users', 'photos', 'JSON NULL');
CALL sysudate_add_column('users', 'display_self', 'JSON NULL');
CALL sysudate_add_column('users', 'feedback', 'TEXT NULL');
CALL sysudate_add_column('users', 'avatar', 'VARCHAR(500) NULL');
CALL sysudate_add_column('users', 'online_offline', 'VARCHAR(30) NULL');
CALL sysudate_add_column('users', 'friend_goal', 'TEXT NULL');
CALL sysudate_add_column('users', 'ideal_friend', 'TEXT NULL');
CALL sysudate_add_column('users', 'special_request', 'TEXT NULL');
CALL sysudate_add_column('users', 'created_at',
    'TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3)');
CALL sysudate_add_column('users', 'updated_at',
    'TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) '
    'ON UPDATE CURRENT_TIMESTAMP(3)');

DROP PROCEDURE sysudate_add_column;
