package lccast.voting.system.job;

import jakarta.servlet.http.HttpSession;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/jobs")
public class JobsApiController {

    private final BackgroundJobService jobs;

    public JobsApiController(BackgroundJobService jobs) { this.jobs = jobs; }

    private UUID userId(HttpSession session) {
        Object attr = session.getAttribute("userId");
        if (attr == null) throw new IllegalStateException("Session expired.");
        return attr instanceof UUID u ? u : UUID.fromString(attr.toString());
    }

    @GetMapping("/active")
    public List<Map<String, Object>> active(HttpSession session) {
        return jobs.activeFor(userId(session)).stream().map(BackgroundJob::toMap).toList();
    }

    @GetMapping("/{id}/download")
    public ResponseEntity<Resource> download(@PathVariable UUID id, HttpSession session) {
        BackgroundJob job = jobs.find(id, userId(session)).orElse(null);
        if (job == null || job.getResultFile() == null) return ResponseEntity.notFound().build();

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + job.getResultName() + "\"")
                .contentType(MediaType.parseMediaType("text/csv;charset=UTF-8"))
                .body(new FileSystemResource(job.getResultFile()));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> dismiss(@PathVariable UUID id, HttpSession session) {
        jobs.find(id, userId(session)).ifPresent(jobs::dismiss);
        return ResponseEntity.noContent().build();
    }
}