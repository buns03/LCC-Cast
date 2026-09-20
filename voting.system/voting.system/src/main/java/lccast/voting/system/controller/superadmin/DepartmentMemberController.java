package lccast.voting.system.controller.superadmin;

import lccast.voting.system.model.Voter;
import lccast.voting.system.repository.VoterRepository;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/superadmin/departments/members")
public class DepartmentMemberController {

    private final VoterRepository voterRepository;

    public DepartmentMemberController(
            VoterRepository voterRepository
    ) {
        this.voterRepository = voterRepository;
    }

    @GetMapping("/student/{studentId}")
    public ResponseEntity<Voter> getStudentById(
            @PathVariable String studentId
    ) {
        return voterRepository.findByStudentId(studentId.trim())
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }
}
