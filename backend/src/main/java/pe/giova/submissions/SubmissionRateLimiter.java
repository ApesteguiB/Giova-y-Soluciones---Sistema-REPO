package pe.giova.submissions;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.concurrent.ConcurrentHashMap;
import pe.giova.auth.RateLimitExceededException;

public class SubmissionRateLimiter {
    private static final int MAX_REQUESTS = 20;
    private static final Duration WINDOW = Duration.ofMinutes(10);
    private final ConcurrentHashMap<String, Deque<Instant>> requests = new ConcurrentHashMap<>();

    public void check(String key) {
        Deque<Instant> queue = requests.computeIfAbsent(key, ignored -> new ArrayDeque<>());
        synchronized (queue) {
            Instant cutoff = Instant.now().minus(WINDOW);
            while (!queue.isEmpty() && queue.peekFirst().isBefore(cutoff)) queue.removeFirst();
            if (queue.size() >= MAX_REQUESTS) {
                long retry = Math.max(1, Duration.between(Instant.now(), queue.peekFirst().plus(WINDOW)).toSeconds());
                throw new RateLimitExceededException(retry);
            }
            queue.addLast(Instant.now());
        }
    }
}
