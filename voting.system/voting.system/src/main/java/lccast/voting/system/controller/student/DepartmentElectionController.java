package lccast.voting.system.controller.student;

import jakarta.servlet.http.HttpSession;
import lccast.voting.system.model.Department;
import lccast.voting.system.model.Voter;
import lccast.voting.system.repository.VoterRepository;
import lccast.voting.system.service.CandidatePortalService;
import lccast.voting.system.service.student.VoterDepartmentElectionService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

@Controller
@RequestMapping("/voter")
public class DepartmentElectionController {

    private final VoterRepository voterRepository;
    private final VoterDepartmentElectionService voterDepartmentElectionService;
    private final CandidatePortalService candidatePortalService;

    public DepartmentElectionController(
            VoterRepository voterRepository,
            VoterDepartmentElectionService voterDepartmentElectionService,
            CandidatePortalService candidatePortalService
    ) {
        this.voterRepository = voterRepository;
        this.voterDepartmentElectionService = voterDepartmentElectionService;
        this.candidatePortalService = candidatePortalService;
    }

    @GetMapping("/department-election")
    public String departmentElection(HttpSession session, Model model) {

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

        // Resolve the voter's real department — no assumed/default type.
        Department department = resolveDepartmentForSession(session);

        if (department != null) {
            // votingType comes straight from the DB row for this department.
            model.addAttribute("departmentTitle", department.getTitle());
            model.addAttribute("votingType", department.getVotingType().name());
        } else {
            // Explicit "unresolved" state — template must not guess a type.
            model.addAttribute("departmentTitle", "");
            model.addAttribute("votingType", null);
        }

        return "voter/department-election.html";
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

    private Department resolveDepartmentForSession(HttpSession session) {
        Object userIdAttr = session.getAttribute("userId");

        if (userIdAttr == null) {
            return null;
        }

        UUID authUserId = UUID.fromString(userIdAttr.toString());

        Voter voter = voterRepository.findByAuthUserId(authUserId).orElse(null);

        if (voter == null) {
            return null;
        }

        return voterDepartmentElectionService.resolveVoterDepartment(voter);
    }
}