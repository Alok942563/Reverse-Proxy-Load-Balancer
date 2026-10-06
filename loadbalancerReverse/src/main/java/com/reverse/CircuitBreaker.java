package com.reverse;

import java.util.concurrent.atomic.AtomicInteger;

public class CircuitBreaker {

    public enum State { CLOSED, OPEN, HALF_OPEN }

    private volatile State state = State.CLOSED;
    private final AtomicInteger consecutiveFailures = new AtomicInteger(0);
    private volatile long openedAt = 0;

    private static final int FAILURE_THRESHOLD = 3;   // trips after 3 failures in a row
    private static final long COOLDOWN_MS = 10_000;   // stays OPEN for 10s before retrying

    public boolean allowRequest() {
        if (state == State.CLOSED) return true;

        if (state == State.OPEN) {
            if (System.currentTimeMillis() - openedAt > COOLDOWN_MS) {
                state = State.HALF_OPEN;   // cooldown passed, allow one test request
                return true;
            }
            return false;                   // still cooling down, skip this server
        }

        // HALF_OPEN — allow the test request through
        return true;
    }

    public void recordSuccess() {
        consecutiveFailures.set(0);
        state = State.CLOSED;
    }

    public void recordFailure() {
        int failures = consecutiveFailures.incrementAndGet();
        if (failures >= FAILURE_THRESHOLD) {
            state = State.OPEN;
            openedAt = System.currentTimeMillis();
        }
    }

    public State getState() {
        return state;
    }
}