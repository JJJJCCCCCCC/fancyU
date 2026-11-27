package com.sysudate.api.notification;

import java.util.List;

public interface NotificationOutboxRepository {
    void enqueue(NotificationEvent event);

    List<NotificationEvent> lockPending(int batchSize);

    void markPublished(String eventId);
}
