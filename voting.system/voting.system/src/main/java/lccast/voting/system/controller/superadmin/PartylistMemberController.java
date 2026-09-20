package lccast.voting.system.controller.superadmin;

import lccast.voting.system.model.Voter;
import lccast.voting.system.repository.VoterRepository;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/superadmin/partylists/members")
public class PartylistMemberController {

    private final VoterRepository voterRepository;

    public PartylistMemberController(
            VoterRepository voterRepository
    ) {
        this.voterRepository = voterRepository;
    }

    @GetMapping("/student/{studentId}")
    public ResponseEntity<Voter> getStudentById(
            @PathVariable String studentId,
            @RequestParam UUID campusId
    ) {
        return voterRepository
                .findByStudentId(studentId.trim())
                .filter(voter -> campusId.equals(voter.getCampusId()))
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }
}
