package com.sysudate.api.notification;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Map;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(
    prefix = "sysudate.notification",
    name = "email-enabled",
    havingValue = "true")
public class EmailNotificationConsumer {
    private final ObjectMapper objectMapper;
    private final NotificationDeliveryRepository deliveries;
    private final JavaMailSender mailSender;
    private final String from;

    public EmailNotificationConsumer(
        ObjectMapper objectMapper,
        NotificationDeliveryRepository deliveries,
        JavaMailSender mailSender,
        @Value("${sysudate.notification.from}") String from
    ) {
        this.objectMapper = objectMapper;
        this.deliveries = deliveries;
        this.mailSender = mailSender;
        this.from = from;
    }

    @RabbitListener(queues = NotificationConfiguration.EMAIL_QUEUE)
    public void consume(Message message) {
        String eventId = message.getMessageProperties().getMessageId();
        Payload payload = parse(message.getBody());
        if (!deliveries.begin(eventId, hash(payload.destination()))) {
            return;
        }
        try {
            mailSender.send(email(payload));
            deliveries.delivered(eventId);
        } catch (RuntimeException exception) {
            deliveries.failed(
                eventId,
                exception.getClass().getSimpleName());
            throw exception;
        }
    }

    private SimpleMailMessage email(Payload payload) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(from);
        message.setTo(payload.destination());
        if ("email-verification".equals(payload.template())) {
            message.setSubject("SYSUDate verification code");
            message.setText(
                "Your verification code is "
                    + payload.variables().get("code")
                    + ". It expires in 5 minutes.");
        } else {
            message.setSubject("SYSUDate survey submitted");
            message.setText(
                "Your survey serial number is "
                    + payload.variables().get("serialNumber")
                    + ".");
        }
        return message;
    }

    private Payload parse(byte[] body) {
        try {
            Map<String, Object> payload = objectMapper.readValue(
                body,
                new TypeReference<>() {
                });
            @SuppressWarnings("unchecked")
            Map<String, Object> variables =
                (Map<String, Object>) payload.get("variables");
            return new Payload(
                String.valueOf(payload.get("destination")),
                String.valueOf(payload.get("template")),
                variables);
        } catch (IOException | ClassCastException exception) {
            throw new IllegalArgumentException(
                "Invalid notification payload",
                exception);
        }
    }

    private String hash(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                .digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 unavailable", exception);
        }
    }

    private record Payload(
        String destination,
        String template,
        Map<String, Object> variables
    ) {
    }
}
