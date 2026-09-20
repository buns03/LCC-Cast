package lccast.voting.system.controller.superadmin;

import jakarta.servlet.http.HttpServletRequest;
import lccast.voting.system.model.*;
import lccast.voting.system.repository.*;

import lccast.voting.system.service.superadmin.VoterService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/superadmin/api/voters")
public class SuperadminVoterApiController {

    private final VoterRepository voterRepository;
    private final CampusRepository campusRepository;
    private final VoterService voterService;

    public SuperadminVoterApiController(
            VoterRepository voterRepository,
            CampusRepository campusRepository,
            VoterService voterService
    ) {
        this.voterRepository = voterRepository;
        this.campusRepository = campusRepository;
        this.voterService = voterService;
    }

// =========================================================
// GET ALL ACTIVE VOTERS (batched — fast)
// =========================================================

    @GetMapping
    public ResponseEntity<?> getVoters() {

        List<Voter> voters = voterRepository.findByStatus(RecordStatus.ACTIVE);

        return ResponseEntity.ok(voterService.toResponseList(voters));
    }

// =========================================================
// GET VOTER BY STUDENT ID
// =========================================================

    @GetMapping("/student/{studentId}")
    public ResponseEntity<?> getVoterByStudentId(
            @PathVariable String studentId
    ) {

        String trimmedStudentId = studentId.trim();

        if (trimmedStudentId.isBlank()) {
            return ResponseEntity.notFound().build();
        }

        return voterRepository
                .findByStudentId(trimmedStudentId)
                .filter(voter -> voter.getStatus() == RecordStatus.ACTIVE)
                .map(voter -> ResponseEntity.ok(voterService.toResponse(voter)))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }


// =========================================================
// GET SINGLE VOTER
// =========================================================

    @GetMapping("/{id}")
    public ResponseEntity<?> getVoter(
            @PathVariable UUID id
    ) {

        return voterRepository
                .findByIdAndStatus(id, RecordStatus.ACTIVE)
                .map(voter -> ResponseEntity.ok(voterService.toResponse(voter)))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

// =========================================================
// UPDATE VOTER
// =========================================================

    @PutMapping("/{id}")
    public ResponseEntity<?> updateVoter(
            @PathVariable UUID id,
            @RequestBody Map<String, Object> request,
            HttpServletRequest httpRequest
    ){

        Voter voter = voterRepository
                .findByIdAndStatus(id, RecordStatus.ACTIVE)
                .orElse(null);

        if (voter == null) {
            return ResponseEntity.notFound().build();
        }

        String studentId = stringValue(request.get("studentId"));

        if (studentId.isBlank()) {
            return ResponseEntity.badRequest().body(
                    Map.of("message", "Student ID is required.")
            );
        }

        Optional<Voter> existingVoter =
                voterRepository.findByStudentId(studentId);

        if (existingVoter.isPresent()
                && !existingVoter.get().getId().equals(id)) {

            return ResponseEntity.badRequest().body(
                    Map.of(
                            "message",
                            "This Student ID is already registered."
                    )
            );
        }

        voter.setStudentId(studentId);
        voter.setLastName(stringValue(request.get("lastName")));
        voter.setFirstName(stringValue(request.get("firstName")));
        voter.setMiddleName(stringValue(request.get("middleName")));
        voter.setFullName(stringValue(request.get("fullName")));
        voter.setEmail(stringValue(request.get("email")));
        voter.setProgramCourse(stringValue(request.get("programCourse")));
        voter.setYearLevel(stringValue(request.get("yearLevel")));
        voter.setSection(stringValue(request.get("section")));

        String campusId = stringValue(request.get("campusId"));

        if (campusId.isBlank()) {
            return ResponseEntity.badRequest().body(
                    Map.of("message", "Campus is required.")
            );
        }

        UUID campusUUID;

        try {
            campusUUID = UUID.fromString(campusId);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(
                    Map.of("message", "Invalid campus ID.")
            );
        }

        if (!campusRepository.existsById(campusUUID)) {
            return ResponseEntity.badRequest().body(
                    Map.of("message", "Selected campus was not found.")
            );
        }

        voter.setCampusId(campusUUID);

        Voter saved = voterRepository.save(voter);

        voterService.auditUpdate(
                httpRequest,
                saved
        );

        return ResponseEntity.ok(voterService.toResponse(saved));
    }


// =========================================================
// ARCHIVE VOTER
// =========================================================

    @PatchMapping("/{id}/archive")
    public ResponseEntity<?> archiveVoter(
            @PathVariable UUID id,
            HttpServletRequest request
    ) {
        try {
            voterService.archiveVoter(id, request);

            return ResponseEntity.ok(
                    Map.of("message", "Voter archived successfully.")
            );

        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(
                    Map.of("message", e.getMessage())
            );
        }
    }


// =========================================================
// MOVE VOTER TO TRASH
// =========================================================

    @DeleteMapping("/{id}")
    public ResponseEntity<?> deleteVoter(
            @PathVariable UUID id,
            HttpServletRequest request
    ) {
        try {
            voterService.deleteVoter(id, request);

            return ResponseEntity.ok(
                    Map.of("message", "Voter moved to trash successfully.")
            );

        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(
                    Map.of("message", e.getMessage())
            );
        }
    }

// =========================================================
// BULK ARCHIVE VOTERS
// =========================================================

    @PatchMapping("/archive")
    public ResponseEntity<?> archiveVoters(
            @RequestBody List<UUID> ids,
            HttpServletRequest request
    ) {
        try {
            if (ids == null || ids.isEmpty()) {
                return ResponseEntity.badRequest().body(
                        Map.of("message", "No voters selected.")
                );
            }

            int count = voterService.archiveVoters(ids, request);

            return ResponseEntity.ok(
                    Map.of(
                            "message", "Voters archived successfully.",
                            "count", count
                    )
            );

        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(
                    Map.of("message", e.getMessage())
            );
        }
    }

// =========================================================
// BULK DELETE / TRASH VOTERS
// =========================================================

    @DeleteMapping("/bulk")
    public ResponseEntity<?> deleteVoters(
            @RequestBody List<UUID> ids,
            HttpServletRequest request
    ) {
        try {
            if (ids == null || ids.isEmpty()) {
                return ResponseEntity.badRequest().body(
                        Map.of("message", "No voters selected.")
                );
            }

            int count = voterService.deleteVoters(ids, request);

            return ResponseEntity.ok(
                    Map.of(
                            "message", "Voters moved to trash successfully.",
                            "count", count
                    )
            );

        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(
                    Map.of("message", e.getMessage())
            );
        }
    }

// =========================================================
// RESTORE VOTER FROM TRASH
// =========================================================

    @PatchMapping("/{id}/restore")
    public ResponseEntity<?> restoreVoter(
            @PathVariable UUID id,
            HttpServletRequest request
    ) {

        Voter voter = voterRepository
                .findById(id)
                .orElse(null);

        if (voter == null) {
            return ResponseEntity.notFound().build();
        }

        if (voter.getStatus() != RecordStatus.DELETED
                && voter.getStatus() != RecordStatus.ARCHIVED) {
            return ResponseEntity.badRequest().body(
                    Map.of("message", "Only deleted or archived voters can be restored.")
            );
        }

        voter.setStatus(RecordStatus.ACTIVE);

        Voter restored = voterRepository.save(voter);

        voterService.restoreVoterRecords(restored.getId());

        voterService.auditRestore(
                request,
                restored
        );

        return ResponseEntity.ok(voterService.toResponse(restored));
    }

// =========================================================
// PERMANENTLY DELETE VOTER
// =========================================================

    @DeleteMapping("/{id}/permanent")
    public ResponseEntity<?> permanentlyDeleteVoter(
            @PathVariable UUID id
    ) {
        try {
            voterService.permanentlyDeleteVoter(id);

            return ResponseEntity.ok(
                    Map.of("message", "Voter permanently deleted.")
            );

        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(
                    Map.of("message", e.getMessage())
            );
        }
    }

// =========================================================
// STRING HELPER
// =========================================================

    private String stringValue(Object value) {
        return value == null
                ? ""
                : String.valueOf(value).trim();
    }

    @PostMapping("/import")
    public ResponseEntity<?> importVoters(
            @RequestBody List<Map<String, Object>> rows,
            HttpServletRequest request
    ){

        if (rows == null || rows.isEmpty()) {
            return ResponseEntity.badRequest().body(
                    Map.of(
                            "message",
                            "The import file contains no voter records."
                    )
            );
        }

        VoterService.ImportResult result =
                voterService.importVoters(rows, request);

        return ResponseEntity.ok(
                Map.of(
                        "success", true,
                        "added", result.added(),
                        "updated", result.updated(),
                        "skipped", result.skipped(),
                        "errors", result.errors()
                )
        );
    }

}