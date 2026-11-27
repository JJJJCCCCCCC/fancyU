package com.sysudate.api.notification;

public interface NotificationDeliveryRepository {
    boolean begin(String eventId, String destinationHash);

    void delivered(String eventId);

    void failed(String eventId, String failureCategory);
}
