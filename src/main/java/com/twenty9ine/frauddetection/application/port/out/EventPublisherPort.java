package com.twenty9ine.frauddetection.application.port.out;

import com.twenty9ine.frauddetection.domain.event.DomainEvent;
import com.twenty9ine.frauddetection.domain.valueobject.TransactionId;

import java.util.List;

public interface EventPublisherPort {
    void publish(DomainEvent<TransactionId> event);
    void publishAll(List<DomainEvent<TransactionId>> events);
}
