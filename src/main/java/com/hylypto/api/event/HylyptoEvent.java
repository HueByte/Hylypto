package com.hylypto.api.event;

public abstract class HylyptoEvent {

    private final long timestamp;

    protected HylyptoEvent() {
        this.timestamp = System.currentTimeMillis();
    }

    public long getTimestamp() {
        return timestamp;
    }
}
