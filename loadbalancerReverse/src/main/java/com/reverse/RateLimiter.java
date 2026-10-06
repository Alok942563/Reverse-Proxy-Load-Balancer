package com.reverse;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

public class RateLimiter {

    private final Map<String, AtomicInteger> requestCounts = new ConcurrentHashMap<>();
    private final Map<String, Long> windowStart = new ConcurrentHashMap<>();

    private static final int MAX_REQUESTS_PER_WINDOW = 20;
    private static final long WINDOW_MS = 1000; // 1 second window

    public boolean allowRequest(String clientId) {
        long now = System.currentTimeMillis();

        windowStart.putIfAbsent(clientId, now);
        requestCounts.putIfAbsent(clientId, new AtomicInteger(0));

        long start = windowStart.get(clientId);

        if (now - start > WINDOW_MS) {
            // window expired, reset it
            windowStart.put(clientId, now);
            requestCounts.get(clientId).set(0);
        }

        int currentCount = requestCounts.get(clientId).incrementAndGet();
        boolean allowed = currentCount <= MAX_REQUESTS_PER_WINDOW;

        System.out.println("[RateLimiter] client=" + clientId + " count=" + currentCount + " allowed=" + allowed);

        return allowed;
    }
}