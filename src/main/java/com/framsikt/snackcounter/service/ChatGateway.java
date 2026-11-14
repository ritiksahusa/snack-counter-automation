package com.framsikt.snackcounter.service;

/** Boundary for publishing a simulated refill event to chat. */
public interface ChatGateway {
    void publish(ChatEvent event);
}
