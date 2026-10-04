package lccast.voting.system.controller.student;

import jakarta.servlet.http.HttpSession;
import lccast.voting.system.dto.VoteSummaryDTO;
import lccast.voting.system.model.Voter;
import lccast.voting.system.repository.VoterRepository;
import lccast.voting.system.service.CandidatePortalService;
import lccast.voting.system.service.VoteSummaryService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

@Controller
@RequestMapping("/voter")
public class VoteSummariesController {

    private final VoteSummaryService voteSummaryService;
    private final VoterRepository voterRepository;
    private final CandidatePortalService candidatePortalService;

    public VoteSummariesController(
            VoteSummaryService voteSummaryService,
            VoterRepository voterRepository,
            CandidatePortalService candidatePortalService) {
        this.voteSummaryService = voteSummaryService;
        this.voterRepository = voterRepository;
        this.candidatePortalService = candidatePortalService;
    }

    @GetMapping("/vote-summaries")
    public String voteSummaries(HttpSession session, Model model) {

        String firstName = (String) session.getAttribute("firstName");
        String role = (String) session.getAttribute("role");
        String programCourse = (String) session.getAttribute("programCourse");
        String campus = (String) session.getAttribute("campus");

        String displayName = (firstName != null && !firstName.trim().isEmpty())
                ? firstName
                : role;

        model.addAttribute("displayName", displayName);
        model.addAttribute("role", role);
        model.addAttribute("programCourse", programCourse);
        model.addAttribute("campus", campus);
        model.addAttribute("avatarUrl", resolveAvatarUrl((String) session.getAttribute("userId"))); // ADD

        return "voter/vote-summaries.html";
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

    // VOTER-ONLY endpoint: resolves the voter strictly from the
    // authenticated session, never from a request parameter. Do not
    // add a voterId path/query param to this method.
    @GetMapping("/vote-summaries/data")
    @ResponseBody
    public List<VoteSummaryDTO> voteSummariesData(HttpSession session) {

        String userIdStr = (String) session.getAttribute("userId");
        String campus = (String) session.getAttribute("campus");

        UUID authUserId;
        try {
            authUserId = UUID.fromString(userIdStr);
        } catch (IllegalArgumentException | NullPointerException e) {
            throw new IllegalStateException("No authenticated user in session.");
        }

        Voter voter = voterRepository.findByAuthUserId(authUserId)
                .orElseThrow(() -> new IllegalStateException(
                        "No voter profile found for authenticated user."));

        List<VoteSummaryDTO> summaries =
                voteSummaryService.getVoteSummariesForVoter(voter.getId());

        summaries.forEach(s -> s.setCampus(campus));

        return summaries;
    }
}