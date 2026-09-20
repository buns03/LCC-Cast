package lccast.voting.system.controller.candidate;

import jakarta.servlet.http.HttpSession;
import lccast.voting.system.service.CandidatePortalService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import java.nio.charset.StandardCharsets;
import java.net.URLEncoder;
import java.util.UUID;

@Controller
@RequestMapping("/candidate")
public class CandidateController {

    private final CandidatePortalService candidatePortalService;

    public CandidateController(CandidatePortalService candidatePortalService) {
        this.candidatePortalService = candidatePortalService;
    }

    @GetMapping("/personal-information")
    public String personalInformation(HttpSession session, Model model) {
        populateCommonAttributes(session, model);
        return "candidate/personal-information.html";
    }

    @GetMapping("/partylist-information")
    public String partylistInformation(HttpSession session, Model model) {
        populateCommonAttributes(session, model);
        return "candidate/partylist-information.html";
    }

    private void populateCommonAttributes(HttpSession session, Model model) {
        String firstName = (String) session.getAttribute("firstName");
        String role = (String) session.getAttribute("role");
        String displayName = (firstName != null && !firstName.isBlank()) ? firstName : role;

        model.addAttribute("displayName", displayName);
        model.addAttribute("role", role);

        String authUserIdStr = (String) session.getAttribute("userId");
        String avatarUrl = null;

        if (authUserIdStr != null) {
            var record = candidatePortalService.findRecordByAuthUserId(UUID.fromString(authUserIdStr));
            if (record != null && record.photoImageUrl != null) {
                avatarUrl = "/api/storage/file?path=" +
                        URLEncoder.encode(record.photoImageUrl, StandardCharsets.UTF_8);
            }
        }

        model.addAttribute("avatarUrl", avatarUrl);
    }
}