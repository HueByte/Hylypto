package com.hylypto.api.event;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

public class HylyptoEventBus {

    private final Map<Class<?>, List<Consumer<?>>> listeners = new ConcurrentHashMap<>();

    public <T extends HylyptoEvent> void subscribe(Class<T> eventType, Consumer<T> handler) {
        listeners.computeIfAbsent(eventType, k -> new ArrayList<>()).add(handler);
    }

    @SuppressWarnings("unchecked")
    public <T extends HylyptoEvent> void publish(T event) {
        List<Consumer<?>> handlers = listeners.get(event.getClass());
        if (handlers == null) {
            return;
        }
        for (Consumer<?> handler : handlers) {
            ((Consumer<T>) handler).accept(event);
        }
    }

    public <T extends HylyptoEvent> void unsubscribeAll(Class<T> eventType) {
        listeners.remove(eventType);
    }
}
