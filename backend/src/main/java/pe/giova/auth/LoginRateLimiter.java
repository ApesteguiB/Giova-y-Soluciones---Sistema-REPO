package pe.giova.auth;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.concurrent.ConcurrentHashMap;

public class LoginRateLimiter {
    private static final int MAX_ATTEMPTS = 5;
    private static final Duration WINDOW = Duration.ofMinutes(1);
    private final ConcurrentHashMap<String, Deque<Instant>> failures = new ConcurrentHashMap<>();

    public void check(String key) {
        Deque<Instant> queue = failures.computeIfAbsent(key, ignored -> new ArrayDeque<>());
        synchronized (queue) {
            purge(queue);
            if (queue.size() >= MAX_ATTEMPTS) {
                long retryAfter = Math.max(1, Duration.between(Instant.now(), queue.peekFirst().plus(WINDOW)).toSeconds());
                throw new RateLimitExceededException(retryAfter);
            }
        }
    }

    public void recordFailure(String key) {
        Deque<Instant> queue = failures.computeIfAbsent(key, ignored -> new ArrayDeque<>());
        synchronized (queue) {
            purge(queue);
            queue.addLast(Instant.now());
        }
    }

    public void clear(String key) {
        failures.remove(key);
    }

    private void purge(Deque<Instant> queue) {
        Instant cutoff = Instant.now().minus(WINDOW);
        while (!queue.isEmpty() && queue.peekFirst().isBefore(cutoff)) {
            queue.removeFirst();
        }
    }
}
