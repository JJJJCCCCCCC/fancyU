package com.sysudate.api.matching;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sysudate.api.matching.MatchingRepository.ExchangeState;
import com.sysudate.api.support.MySqlContainerTest;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class JdbcMatchingRepositoryTest extends MySqlContainerTest {
    private JdbcMatchingRepository repository;

    @BeforeEach
    void migrateAndSeed() {
        Flyway.configure()
            .dataSource(dataSource)
            .locations("classpath:db/migration")
            .load()
            .migrate();
        jdbc.update(
            """
                INSERT INTO accounts (
                    id, email, username, password_hash, email_verified
                ) VALUES
                    (1, 'one@sysu.edu.cn', 'one', 'hash', TRUE),
                    (2, 'two@sysu.edu.cn', 'two', 'hash', TRUE),
                    (3, 'three@sysu.edu.cn', 'three', 'hash', TRUE)
                """);
        jdbc.update(
            """
                INSERT INTO users (
                    account_id, serial_number, email, contact_email,
                    nickname, survey_type
                ) VALUES
                    (
                        1,
                        'SYSU-550E8400-E29B-41D4-A716-446655440001',
                        'one@sysu.edu.cn',
                        'one-contact@example.com',
                        'one',
                        'love'
                    ),
                    (
                        2,
                        'SYSU-550E8400-E29B-41D4-A716-446655440002',
                        'two@sysu.edu.cn',
                        'two-contact@example.com',
                        'two',
                        'love'
                    )
                """);
        jdbc.update(
            """
                INSERT INTO match_history (
                    id, pair_key, account_id, match_account_id, survey_type
                ) VALUES
                    (10, 'pair-1', 1, 2, 'love'),
                    (11, 'pair-1', 2, 1, 'love')
                """);
        repository = new JdbcMatchingRepository(
            jdbc,
            new ObjectMapper());
    }

    @Test
    void exchangesContactOnlyAfterBothOwnersAgree() {
        ExchangeState first = repository.agree(10, 1);

        assertThat(first.contactExchanged()).isFalse();
        assertThat(repository.findOwned(10, 3)).isEmpty();
        assertThat(repository.findForAccount(1).getFirst()
            .contactExchanged()).isFalse();

        ExchangeState second = repository.agree(11, 2);

        assertThat(second.contactExchanged()).isTrue();
        assertThat(repository.findForAccount(1).getFirst()
            .contactExchanged()).isTrue();
        assertThat(repository.findForAccount(2).getFirst()
            .contactExchanged()).isTrue();
    }
}
