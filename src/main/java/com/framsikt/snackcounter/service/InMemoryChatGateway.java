package com.framsikt.snackcounter.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Stores simulated chat events in memory so workflows can be tested without chat credentials. */
public final class InMemoryChatGateway implements ChatGateway {
    private final List<ChatEvent> events = new ArrayList<>();

    @Override
    public synchronized void publish(ChatEvent event) {
        events.add(Objects.requireNonNull(event, "event"));
    }

    public synchronized List<ChatEvent> events() {
        return List.copyOf(events);
    }
}
