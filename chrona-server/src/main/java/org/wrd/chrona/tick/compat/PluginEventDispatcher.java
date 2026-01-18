package org.wrd.chrona.tick.compat;

import org.bukkit.event.Event;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.plugin.EventExecutor;
import org.bukkit.plugin.RegisteredListener;
import org.wrd.chrona.tick.phase.PluginPhaseExecutor;

import java.util.ArrayList;
import java.util.List;

public class PluginEventDispatcher {
    private final List<PendingEvent> pendingEvents = new ArrayList<>();

    public void queueEvent(Event event) {
        if (PluginPhaseExecutor.isPluginThread()) {
            fireEvent(event);
        } else {
            synchronized (pendingEvents) {
                pendingEvents.add(new PendingEvent(event, System.nanoTime()));
            }
        }
    }

    public void dispatchPendingEvents() {
        List<PendingEvent> events;
        synchronized (pendingEvents) {
            if (pendingEvents.isEmpty()) return;
            events = new ArrayList<>(pendingEvents);
            pendingEvents.clear();
        }

        events.sort((a, b) -> Long.compare(a.timestamp, b.timestamp));

        for (PendingEvent pending : events) {
            fireEvent(pending.event);
        }
    }

    private void fireEvent(Event event) {
        if (event == null) {
            return;
        }
        org.bukkit.Bukkit.getPluginManager().callEvent(event);
    }

    private record PendingEvent(Event event, long timestamp) {}
}
