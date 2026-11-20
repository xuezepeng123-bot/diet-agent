package com.diet.learning;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

public final class AgentTrace {
    private final UUID id = UUID.randomUUID();
    private final long startedAt = System.nanoTime();
    private final List<Event> events = new ArrayList<>();

    public UUID id() {
        return id;
    }

    public void record(String name, Object details) {
        events.add(new Event(name, elapsedMs(), details));
    }

    long elapsedMs() {
        return TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAt);
    }

    List<Event> events() {
        return List.copyOf(events);
    }

    public record Event(String name, long elapsedMs, Object details) {}
}
