package lccast.voting.system.controller.student;

import jakarta.servlet.http.HttpSession;
import lccast.voting.system.service.CandidatePortalService;
import lccast.voting.system.service.SupabaseStorageService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import lccast.voting.system.dto.student.SscElectionResponse;
import lccast.voting.system.dto.student.VoteSubmissionRequest;
import lccast.voting.system.dto.student.VoteSubmissionResponse;
import lccast.voting.system.model.Voter;
import lccast.voting.system.service.student.SscElectionService;
import lccast.voting.system.service.student.VoteSubmissionService;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import java.time.Duration;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.UUID;

@Controller
@RequestMapping("/voter")
public class SscElectionController {

    private final SscElectionService sscElectionService;
    private final VoteSubmissionService voteSubmissionService;
    private final SupabaseStorageService supabaseStorageService;
    private CandidatePortalService candidatePortalService;
    private final Cache<String, ResponseEntity<byte[]>> fileCache = Caffeine.newBuilder()
            .maximumWeight(100L * 1024 * 1024) // ~100 MB of image bytes
            .weigher((String k, ResponseEntity<byte[]> v) -> v.getBody() == null ? 1 : v.getBody().length)
            .expireAfterWrite(Duration.ofMinutes(30))
            .build();

    public SscElectionController(
            SscElectionService sscElectionService,
            VoteSubmissionService voteSubmissionService,
            SupabaseStorageService supabaseStorageService,
            CandidatePortalService candidatePortalService
    ) {
        this.sscElectionService = sscElectionService;
        this.voteSubmissionService = voteSubmissionService;
        this.supabaseStorageService = supabaseStorageService;
        this.candidatePortalService = candidatePortalService;
    }

    @GetMapping("/ssc-election")
    public String sscelection(
            HttpSession session,
            Model model) {

        // ... unchanged, exactly as you have it ...
        String firstName = (String) session.getAttribute("firstName");
        String role = (String) session.getAttribute("role");
        String programCourse = (String) session.getAttribute("programCourse");
        String campus = (String) session.getAttribute("campus");

        String displayName;
        if (firstName != null && !firstName.trim().isEmpty()) {
            displayName = firstName;
        } else {
            displayName = role;
        }

        model.addAttribute("displayName", displayName);
        model.addAttribute("role", role);
        model.addAttribute("programCourse", programCourse);
        model.addAttribute("campus", campus);
        model.addAttribute("avatarUrl", resolveAvatarUrl((String) session.getAttribute("userId"))); // ADD

        return "voter/ssc-election.html";
    }

    private String resolveAvatarUrl(String authUserIdStr) {
        if (authUserIdStr == null) {
            return null;
        }
        String storagePath = candidatePortalService.findAvatarStoragePath(UUID.fromString(authUserIdStr));
        if (storagePath == null) {
            return null;
        }
        return "/api/storage/file?path=" + URLEncoder.encode(storagePath, StandardCharsets.UTF_8);
    }

    @GetMapping("/api/elections/ssc")
    @ResponseBody
    public ResponseEntity<?> getSscElection(HttpSession session) {

        Object userIdAttr = session.getAttribute("userId");

        if (userIdAttr == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        UUID authUserId = UUID.fromString(userIdAttr.toString());

        Voter voter = sscElectionService.resolveVoter(authUserId);

        SscElectionResponse response = sscElectionService.buildResponse(voter);

        return ResponseEntity.ok(response);
    }

    @PostMapping("/api/elections/vote")
    @ResponseBody
    public ResponseEntity<?> submitVote(
            HttpSession session,
            @RequestBody VoteSubmissionRequest request
    ) {
        Object userIdAttr = session.getAttribute("userId");

        if (userIdAttr == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        UUID authUserId = UUID.fromString(userIdAttr.toString());

        Voter voter = sscElectionService.resolveVoter(authUserId);

        try {
            VoteSubmissionResponse response =
                    voteSubmissionService.submitSscVote(voter, request);

            return ResponseEntity.ok(response);

        } catch (IllegalArgumentException e) {
            return ResponseEntity
                    .status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("message", e.getMessage()));

        } catch (IllegalStateException e) {
            return ResponseEntity
                    .status(HttpStatus.CONFLICT)
                    .body(Map.of("message", e.getMessage()));
        }
    }

    @GetMapping("/api/elections/file")
    @ResponseBody
    public ResponseEntity<byte[]> getElectionFile(
            @RequestParam("path") String path,
            HttpSession session
    ) {
        Object userIdAttr = session.getAttribute("userId");

        if (userIdAttr == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        ResponseEntity<byte[]> cached = fileCache.getIfPresent(path);
        if (cached != null) {
            return cached;
        }

        ResponseEntity<byte[]> file = supabaseStorageService.getFile(path);

        if (!file.getStatusCode().is2xxSuccessful() || file.getBody() == null) {
            return file;
        }

        HttpHeaders headers = new HttpHeaders();
        headers.putAll(file.getHeaders());
        headers.setCacheControl(CacheControl.maxAge(Duration.ofHours(1)).cachePrivate());

        ResponseEntity<byte[]> out = new ResponseEntity<>(file.getBody(), headers, file.getStatusCode());
        fileCache.put(path, out);
        return out;
    }
}