package com.twenty9ine.frauddetection.application.port.out;

import com.twenty9ine.frauddetection.domain.event.DomainEvent;

import java.util.List;

public interface EventPublisherPort {
    void publish(DomainEvent event);
    void publishAll(List<DomainEvent> events);
}
