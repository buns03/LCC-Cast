package lccast.voting.system.controller;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import lccast.voting.system.model.Campus;
import lccast.voting.system.model.UserProfile;
import lccast.voting.system.model.UserRole;
import lccast.voting.system.repository.CampusRepository;
import lccast.voting.system.repository.UserProfileRepository;
import lccast.voting.system.repository.VoterRepository;
import lccast.voting.system.security.CustomUserDetails;
import lccast.voting.system.service.SupabaseAuthResponse;
import lccast.voting.system.service.SupabaseAuthService;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.web.authentication.session.SessionAuthenticationException;
import org.springframework.security.web.authentication.session.SessionAuthenticationStrategy;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import lccast.voting.system.model.Department;
import lccast.voting.system.repository.DepartmentRepository;

@Controller
public class LoginController {

    private final SessionAuthenticationStrategy sessionAuthenticationStrategy;
    private final UserProfileRepository userProfileRepository;
    private final SupabaseAuthService supabaseAuthService;
    private final SecurityContextRepository securityContextRepository =
            new HttpSessionSecurityContextRepository();

    private final VoterRepository voterRepository;
    private final CampusRepository campusRepository;
    private final DepartmentRepository departmentRepository;


    public LoginController(UserProfileRepository userProfileRepository,
                           SupabaseAuthService supabaseAuthService,
                           VoterRepository voterRepository,
                           CampusRepository campusRepository,
                           DepartmentRepository departmentRepository,
                           SessionAuthenticationStrategy sessionAuthenticationStrategy) {
        this.userProfileRepository = userProfileRepository;
        this.supabaseAuthService = supabaseAuthService;
        this.voterRepository = voterRepository;
        this.campusRepository = campusRepository;
        this.departmentRepository = departmentRepository;
        this.sessionAuthenticationStrategy = sessionAuthenticationStrategy;
    }

    @GetMapping("/login")
    public String loginPage() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.isAuthenticated() && !(auth instanceof AnonymousAuthenticationToken)) {
            CustomUserDetails userDetails = (CustomUserDetails) auth.getPrincipal();
            UserProfile profile = userDetails.getProfile(); // adjust to however you expose it

            return switch (profile.getRole()) {
                case SUPERADMIN -> "redirect:/superadmin/dashboard";
                case ADMIN -> "DEPARTMENT".equals(profile.getAdminType())
                        ? "redirect:/admin-dept/dashboard"
                        : "redirect:/admin-ssc/dashboard";
                case STUDENT -> "redirect:/voter/ssc-election";
                case CANDIDATE -> "redirect:/candidate/personal-information";
                default -> "login";
            };
        }
        return "login";
    }

    @PostMapping("/login")
    public String login(@RequestParam String schoolId,
                        @RequestParam String password,
                        HttpServletRequest request,
                        HttpServletResponse response,
                        HttpSession session,
                        Model model) {

        UserProfile profile = userProfileRepository.findBySchoolId(schoolId)
                .or(() -> userProfileRepository.findByEmail(schoolId))
                .orElse(null);

        if (profile == null || profile.getEmail() == null || !profile.isActive()) {
            model.addAttribute("loginError", true);
            model.addAttribute("schoolId", schoolId);
            return "login";
        }

        // NOTE: candidates are former students, so this still-active check
        // applies to them too — a CANDIDATE profile is the same underlying
        // Voter row, just with the role flipped.
        if (profile.getRole() == UserRole.STUDENT || profile.getRole() == UserRole.CANDIDATE) {
            boolean voterActive = voterRepository.findByAuthUserId(profile.getAuthUserId())
                    .map(v -> v.getStatus() == lccast.voting.system.model.RecordStatus.ACTIVE)
                    .orElse(false);

            if (!voterActive) {
                model.addAttribute("loginError", true);
                model.addAttribute("schoolId", schoolId);
                return "login";
            }
        }

        SupabaseAuthResponse authResponse;
        try {
            authResponse = supabaseAuthService.login(profile.getEmail(), password);
        } catch (Exception e) {
            model.addAttribute("loginError", true);
            model.addAttribute("schoolId", schoolId);
            return "login";
        }

        if (authResponse == null || authResponse.getAccessToken() == null) {
            model.addAttribute("loginError", true);
            model.addAttribute("schoolId", schoolId);
            return "login";
        }

        // ---- Authenticate with Spring Security ----
        CustomUserDetails userDetails = new CustomUserDetails(profile);
        Authentication authentication = new UsernamePasswordAuthenticationToken(
                userDetails, null, userDetails.getAuthorities());

        // ---- Block concurrent login for students/candidates only ----
        if (profile.getRole() == UserRole.STUDENT || profile.getRole() == UserRole.CANDIDATE) {
            try {
                sessionAuthenticationStrategy.onAuthentication(authentication, request, response);
            } catch (SessionAuthenticationException e) {
                model.addAttribute("concurrentLoginError",
                        "This account is already logged in on another device or browser.");
                model.addAttribute("schoolId", schoolId);
                return "login";
            }
        }

        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authentication);
        SecurityContextHolder.setContext(context);
        securityContextRepository.saveContext(context, request, response);

        // ---- App-level session attributes (used by controllers/JS) ----
        session.setAttribute("userId", profile.getAuthUserId() != null ? profile.getAuthUserId().toString() : null);
        session.setAttribute("authUserId", profile.getAuthUserId());
        session.setAttribute("schoolId", profile.getSchoolId());
        session.setAttribute("firstName", profile.getFirstName());
        session.setAttribute("role", profile.getRole().name());
        session.setAttribute("adminType", profile.getAdminType());

        // Candidates are still Voter rows underneath, so they get the same
        // campus/programCourse session attributes a STUDENT gets.
        if (profile.getRole() == UserRole.STUDENT || profile.getRole() == UserRole.CANDIDATE) {
            voterRepository.findByAuthUserId(profile.getAuthUserId()).ifPresent(voter -> {
                session.setAttribute("campus", voter.getCampusId() != null
                        ? campusRepository.findById(voter.getCampusId()).map(Campus::getName).orElse(null)
                        : null);
                session.setAttribute("programCourse", voter.getProgramCourse());
            });
            session.setAttribute("mustChangePassword", profile.isMustChangePassword());

        } else if (profile.getRole() == UserRole.ADMIN) {
            session.setAttribute("campus", profile.getCampusId() != null
                    ? campusRepository.findById(profile.getCampusId()).map(Campus::getName).orElse(null)
                    : null);

            session.setAttribute("campusId", profile.getCampusId());

            if ("DEPARTMENT".equals(profile.getAdminType()) && profile.getAdminDepartment() != null) {
                session.setAttribute("programCourse", profile.getAdminDepartment());
            } else {
                session.setAttribute("programCourse", "Supreme Student Council");
            }
        }

        profile.setLastLoginAt(java.time.Instant.now());
        userProfileRepository.save(profile);

        // ---- Force password change on first login ----
        // ---- Force password change on first login (students and candidates only) ----
        if ((profile.getRole() == UserRole.STUDENT || profile.getRole() == UserRole.CANDIDATE)
                && profile.isMustChangePassword()) {
            return "redirect:/voter/password-handler";
        }

        return switch (profile.getRole()) {
            case SUPERADMIN -> "redirect:/superadmin/dashboard";
            case ADMIN -> "DEPARTMENT".equals(profile.getAdminType())
                    ? "redirect:/admin-dept/dashboard"
                    : "redirect:/admin-ssc/dashboard";
            case STUDENT -> "redirect:/voter/ssc-election";
            case CANDIDATE -> "redirect:/candidate/personal-information";
            default -> "redirect:/login";
        };
    }
}