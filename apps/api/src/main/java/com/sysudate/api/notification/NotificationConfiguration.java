package com.sysudate.api.notification;

import java.time.Duration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.transaction.PlatformTransactionManager;

@Configuration
public class NotificationConfiguration {
    public static final String EXCHANGE = "sysudate.notification";
    public static final String EMAIL_QUEUE = "sysudate.notification.email";
    public static final String EMAIL_ROUTING_KEY = "email";

    @Bean
    @ConditionalOnProperty("spring.datasource.url")
    NotificationOutboxRepository notificationOutboxRepository(
        JdbcTemplate jdbc,
        PlatformTransactionManager transactionManager
    ) {
        return new JdbcNotificationOutboxRepository(
            jdbc,
            transactionManager,
            Duration.ofMinutes(1));
    }

    @Bean
    @ConditionalOnProperty("spring.rabbitmq.host")
    DirectExchange notificationExchange() {
        return new DirectExchange(EXCHANGE, true, false);
    }

    @Bean
    @ConditionalOnProperty("spring.rabbitmq.host")
    Queue notificationEmailQueue() {
        return new Queue(EMAIL_QUEUE, true);
    }

    @Bean
    @ConditionalOnProperty("spring.rabbitmq.host")
    Binding notificationEmailBinding(
        Queue notificationEmailQueue,
        DirectExchange notificationExchange
    ) {
        return BindingBuilder.bind(notificationEmailQueue)
            .to(notificationExchange)
            .with(EMAIL_ROUTING_KEY);
    }
}
