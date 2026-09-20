package lccast.voting.system.controller.superadmin;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import lccast.voting.system.dto.superadmin.ElectionCreateRequest;
import lccast.voting.system.dto.superadmin.ElectionResponse;
import lccast.voting.system.model.Election;
import lccast.voting.system.service.superadmin.ElectionService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/superadmin/api/elections")
public class SuperadminElectionApiController {

    private final ElectionService electionService;

    public SuperadminElectionApiController(ElectionService electionService) {
        this.electionService = electionService;
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, String>> handleBadRequest(IllegalArgumentException ex) {
        return ResponseEntity.badRequest().body(Map.of("message", ex.getMessage()));
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<Map<String, String>> handleConflict(IllegalStateException ex) {
        return ResponseEntity.status(org.springframework.http.HttpStatus.CONFLICT)
                .body(Map.of("message", ex.getMessage()));
    }

    @GetMapping
    public ResponseEntity<List<ElectionResponse>> getElections(
            @RequestParam(required = false) UUID campusId
    ) {
        List<Election> elections =
                campusId != null
                        ? electionService.getByCampus(campusId)
                        : electionService.getAllActive();

        return ResponseEntity.ok(
                elections.stream()
                        .map(electionService::toResponse)
                        .toList()
        );
    }

    @GetMapping("/{id}")
    public ResponseEntity<ElectionResponse> getElection(
            @PathVariable UUID id
    ) {
        return ResponseEntity.ok(
                electionService.toResponse(
                        electionService.getById(id)
                )
        );
    }

    @PostMapping
    public ResponseEntity<?> createElection(
            @RequestBody ElectionCreateRequest request,
            HttpSession session,
            HttpServletRequest httpRequest
    ){
        Object userIdAttribute = session.getAttribute("userId");

        if (userIdAttribute == null) {
            return ResponseEntity.status(401).build();
        }

        UUID createdBy;

        if (userIdAttribute instanceof UUID) {
            createdBy = (UUID) userIdAttribute;
        } else {
            createdBy = UUID.fromString(userIdAttribute.toString());
        }

        if (request.getPartylistIds() != null && request.getPartylistIds().size() < 2
                && "SSC".equals(request.getCategory())) {
            return ResponseEntity.badRequest().body(
                    Map.of("message", "SSC election requires at least two partylists.")
            );
        }

        if (request.getDepartmentIds() != null && request.getDepartmentIds().size() < 1
                && "DEPARTMENT".equals(request.getCategory())) {
            return ResponseEntity.badRequest().body(
                    Map.of("message", "Department election requires at least one department.")
            );
        }

        Election election = electionService.create(
                request.getTitle(),
                request.getCategory(),
                request.getCampusId(),
                request.getSchoolYear(),
                request.getStartAt(),
                request.getEndAt(),
                createdBy,
                request.getPartylistIds(),
                request.getDepartmentIds(),
                httpRequest
        );

        return ResponseEntity.ok(
                electionService.toResponse(election)
        );
    }

    @PutMapping("/{id}")
    public ResponseEntity<?> updateElection(
            @PathVariable UUID id,
            @RequestBody ElectionCreateRequest request,
            HttpServletRequest httpRequest
    ){

        if (request.getPartylistIds() != null && request.getPartylistIds().size() < 2
                && "SSC".equals(request.getCategory())) {
            return ResponseEntity.badRequest().body(
                    Map.of("message", "SSC election requires at least two partylists.")
            );
        }

        if (request.getDepartmentIds() != null && request.getDepartmentIds().size() < 1
                && "DEPARTMENT".equals(request.getCategory())) {
            return ResponseEntity.badRequest().body(
                    Map.of("message", "Department election requires at least one department.")
            );
        }

        Election election = electionService.update(
                id,
                request.getTitle(),
                request.getCategory(),
                request.getCampusId(),
                request.getSchoolYear(),
                request.getStartAt(),
                request.getEndAt(),
                request.getPartylistIds(),
                request.getDepartmentIds(),
                httpRequest
        );

        return ResponseEntity.ok(
                electionService.toResponse(election)
        );
    }

    @PutMapping("/{id}/archive")
    public ResponseEntity<Void> archiveElection(
            @PathVariable UUID id,
            HttpServletRequest httpRequest
    ) {
        electionService.archive(id, httpRequest);

        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteElection(
            @PathVariable UUID id,
            HttpServletRequest httpRequest
    ) {
        electionService.delete(id, httpRequest);

        return ResponseEntity.ok().build();
    }

    @PostMapping("/superadmin/elections/{id}/resync-images")
    public ResponseEntity<?> resyncImages(@PathVariable UUID id) {
        electionService.resyncCandidateImages(id);
        return ResponseEntity.ok().build();
    }
}

