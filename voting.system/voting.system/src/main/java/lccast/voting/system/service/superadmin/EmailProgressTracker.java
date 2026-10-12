package lccast.voting.system.service.superadmin;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class EmailProgressTracker {

    public record Progress(String status, int sent, int total, String message) {}

    private record Entry(Progress progress, Instant at) {}

    private final Map<String, Entry> jobs = new ConcurrentHashMap<>();

    public void start(String jobId, int total) {
        put(jobId, new Progress("RUNNING", 0, total, null));
    }

    public void advance(String jobId, int sent, int total) {
        put(jobId, new Progress("RUNNING", sent, total, null));
    }

    public void complete(String jobId, int total) {
        put(jobId, new Progress("COMPLETED", total, total, null));
    }

    public void fail(String jobId, String message) {
        if (jobId == null) return;
        Progress last = get(jobId).orElse(new Progress("RUNNING", 0, 0, null));
        put(jobId, new Progress("FAILED", last.sent(), last.total(), message));
    }

    public Optional<Progress> get(String jobId) {
        if (jobId == null) return Optional.empty();
        return Optional.ofNullable(jobs.get(jobId)).map(Entry::progress);
    }

    private void put(String jobId, Progress progress) {
        if (jobId != null) jobs.put(jobId, new Entry(progress, Instant.now()));
    }

    // Drop finished/stale jobs so the map never grows forever
    @Scheduled(fixedRate = 600_000)
    public void purge() {
        Instant cutoff = Instant.now().minus(Duration.ofHours(1));
        jobs.values().removeIf(e -> e.at().isBefore(cutoff));
    }
}