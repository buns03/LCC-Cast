package lccast.voting.system.controller.student;

import jakarta.servlet.http.HttpSession;
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

import java.util.Map;
import java.util.UUID;

@Controller
@RequestMapping("/voter")
public class SscElectionController {

    private final SscElectionService sscElectionService;
    private final VoteSubmissionService voteSubmissionService;
    private final SupabaseStorageService supabaseStorageService;

    public SscElectionController(
            SscElectionService sscElectionService,
            VoteSubmissionService voteSubmissionService,
            SupabaseStorageService supabaseStorageService
    ) {
        this.sscElectionService = sscElectionService;
        this.voteSubmissionService = voteSubmissionService;
        this.supabaseStorageService = supabaseStorageService;
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

        return "voter/ssc-election.html";
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

        return supabaseStorageService.getFile(path);
    }
}