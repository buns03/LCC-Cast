package lccast.voting.system.controller.superadmin;
import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

@Controller
@RequestMapping("/superadmin")
public class SuperadminSettingsController {

    @GetMapping("/settings")
    public String settings(
            HttpSession session,
            Model model) {

        String role =
                (String) session.getAttribute("role");

        if (role == null || !role.equals("SUPERADMIN")) {
            return "redirect:/login";
        }

        String firstName =
                (String) session.getAttribute("firstName");

        String displayName;

        if (firstName != null &&
                !firstName.trim().isEmpty()) {

            displayName = firstName;

        } else {

            displayName = role;
        }

        model.addAttribute(
                "displayName",
                displayName
        );

        model.addAttribute(
                "role",
                role
        );

        return "superadmin/settings.html";
    }
}
