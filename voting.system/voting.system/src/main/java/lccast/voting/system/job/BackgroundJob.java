package lccast.voting.system.job;

import java.nio.file.Path;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

public class BackgroundJob {
    private final UUID id = UUID.randomUUID();
    private final UUID ownerId;
    private final String type;   // VOTER_IMPORT, VOTER_EXPORT, VOTER_DELETE, VOTER_ARCHIVE
    private final String title;

    private volatile int total;
    private final AtomicInteger processed = new AtomicInteger();
    private final AtomicInteger succeeded = new AtomicInteger();
    private final AtomicInteger failed = new AtomicInteger();

    private volatile String status = "RUNNING"; // RUNNING | DONE | FAILED
    private volatile String message = "";
    private volatile Path resultFile;
    private volatile String resultName;
    private volatile Instant finishedAt;

    public BackgroundJob(UUID ownerId, String type, String title, int total) {
        this.ownerId = ownerId;
        this.type = type;
        this.title = title;
        this.total = total;
    }

    public UUID getId() { return id; }
    public UUID getOwnerId() { return ownerId; }
    public Instant getFinishedAt() { return finishedAt; }
    public Path getResultFile() { return resultFile; }
    public String getResultName() { return resultName; }

    public void setTotal(int total) { this.total = total; }
    public void setMessage(String message) { this.message = message; }

    public void advance(int ok, int bad) {
        succeeded.addAndGet(ok);
        failed.addAndGet(bad);
        processed.addAndGet(ok + bad);
    }

    public void setResult(Path file, String name) {
        this.resultFile = file;
        this.resultName = name;
    }

    void finish() { this.status = "DONE"; this.finishedAt = Instant.now(); }

    void fail(String reason) {
        this.status = "FAILED";
        this.message = reason == null ? "Unexpected error." : reason;
        this.finishedAt = Instant.now();
    }

    public Map<String, Object> toMap() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", id.toString());
        m.put("type", type);
        m.put("title", title);
        m.put("status", status);
        m.put("total", total);
        m.put("processed", processed.get());
        m.put("succeeded", succeeded.get());
        m.put("failed", failed.get());
        m.put("message", message);
        m.put("downloadable", resultFile != null && "DONE".equals(status));
        return m;
    }
}