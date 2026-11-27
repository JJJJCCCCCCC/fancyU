package com.sysudate.api.notification;

import java.util.Map;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(
    prefix = "sysudate.notification",
    name = "publisher-enabled",
    havingValue = "true")
public class NotificationOutboxPublisher {
    private final NotificationOutboxRepository outbox;
    private final RabbitTemplate rabbit;

    public NotificationOutboxPublisher(
        NotificationOutboxRepository outbox,
        RabbitTemplate rabbit
    ) {
        this.outbox = outbox;
        this.rabbit = rabbit;
    }

    @Scheduled(fixedDelayString = "${sysudate.notification.publish-delay:1000}")
    public void publishPending() {
        for (NotificationEvent event : outbox.lockPending(50)) {
            rabbit.convertAndSend(
                NotificationConfiguration.EXCHANGE,
                NotificationConfiguration.EMAIL_ROUTING_KEY,
                event.payload(),
                message -> {
                    message.getMessageProperties().setMessageId(event.id());
                    message.getMessageProperties().setHeader(
                        "eventType",
                        event.eventType());
                    message.getMessageProperties().setHeader(
                        "aggregateId",
                        event.aggregateId());
                    return message;
                });
            outbox.markPublished(event.id());
        }
    }
}
