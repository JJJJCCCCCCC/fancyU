package com.sysudate.api.support;

import javax.sql.DataSource;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
public abstract class MySqlContainerTest {
    @Container
    protected static final MySQLContainer<?> mysql =
        new MySQLContainer<>("mysql:8.4")
            .withDatabaseName("sysudate_test");

    protected DataSource dataSource;
    protected JdbcTemplate jdbc;

    @BeforeEach
    void configureDatabaseAccess() {
        dataSource = new DriverManagerDataSource(
            mysql.getJdbcUrl(),
            mysql.getUsername(),
            mysql.getPassword());
        jdbc = new JdbcTemplate(dataSource);
    }
}
