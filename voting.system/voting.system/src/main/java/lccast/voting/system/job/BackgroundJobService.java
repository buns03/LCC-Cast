package lccast.voting.system.job;

import jakarta.annotation.PreDestroy;
import jakarta.persistence.EntityManagerFactory;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.nio.file.Files;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Collectors;

@Service
public class BackgroundJobService {

    @FunctionalInterface
    public interface JobTask { void run(BackgroundJob job) throws Exception; }

    private final Map<UUID, BackgroundJob> jobs = new ConcurrentHashMap<>();
    private final ExecutorService executor = Executors.newFixedThreadPool(2);
    private final TransactionTemplate readOnlyTx;

    public BackgroundJobService(EntityManagerFactory emf) {
        this.readOnlyTx = new TransactionTemplate(new JpaTransactionManager(emf));
        this.readOnlyTx.setReadOnly(true);
    }

    public BackgroundJob start(UUID ownerId, String type, String title, int total, JobTask task) {
        return launch(ownerId, type, title, total, task, false);
    }

    /** For jobs that only read data and touch lazy relations (exports). */
    public BackgroundJob startReadOnly(UUID ownerId, String type, String title, int total, JobTask task) {
        return launch(ownerId, type, title, total, task, true);
    }

    private BackgroundJob launch(UUID ownerId, String type, String title, int total,
                                 JobTask task, boolean readOnly) {
        BackgroundJob job = new BackgroundJob(ownerId, type, title, total);
        jobs.put(job.getId(), job);

        executor.submit(() -> {
            try {
                if (readOnly) {
                    readOnlyTx.executeWithoutResult(status -> {
                        try { task.run(job); }
                        catch (RuntimeException e) { throw e; }
                        catch (Exception e) { throw new RuntimeException(e); }
                    });
                } else {
                    task.run(job);
                }
                job.finish();
            } catch (Exception e) {
                e.printStackTrace();
                Throwable root = e.getCause() != null ? e.getCause() : e;
                job.fail(root.getMessage());
            }
        });
        return job;
    }

    public List<BackgroundJob> activeFor(UUID ownerId) {
        cleanup();
        return jobs.values().stream()
                .filter(j -> j.getOwnerId().equals(ownerId))
                .collect(Collectors.toList());
    }

    public Optional<BackgroundJob> find(UUID id, UUID ownerId) {
        BackgroundJob job = jobs.get(id);
        return job != null && job.getOwnerId().equals(ownerId) ? Optional.of(job) : Optional.empty();
    }

    public void dismiss(BackgroundJob job) {
        jobs.remove(job.getId());
        try { if (job.getResultFile() != null) Files.deleteIfExists(job.getResultFile()); }
        catch (Exception ignored) { }
    }

    private void cleanup() {
        Instant limit = Instant.now().minus(Duration.ofHours(1));
        jobs.values().stream()
                .filter(j -> j.getFinishedAt() != null && j.getFinishedAt().isBefore(limit))
                .forEach(this::dismiss);
    }

    public static <T> List<List<T>> chunks(List<T> list, int size) {
        List<List<T>> out = new ArrayList<>();
        for (int i = 0; i < list.size(); i += size) {
            out.add(list.subList(i, Math.min(i + size, list.size())));
        }
        return out;
    }

    @PreDestroy
    void shutdown() { executor.shutdown(); }
}