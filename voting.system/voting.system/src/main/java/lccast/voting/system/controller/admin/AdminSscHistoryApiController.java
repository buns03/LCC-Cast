package lccast.voting.system.controller.admin;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import lccast.voting.system.dto.*;
import lccast.voting.system.model.AuditAction;
import lccast.voting.system.model.UserRole;
import lccast.voting.system.service.HistoryService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.UUID;

/**
 * History API for the admin-ssc role.
 *
 * Scoping is enforced here, never from the client:
 *   - Vote Logs : pinned to the campus registered on the session account
 *   - Actions   : only audit entries created by the session user
 *   - Archives  : only records archived by the session user
 *   - Trash     : only records deleted by the session user
 */
@RestController
@RequestMapping("/admin-ssc/api/history")
public class AdminSscHistoryApiController {

    private final HistoryService historyService;

    public AdminSscHistoryApiController(HistoryService historyService) {
        this.historyService = historyService;
    }

    // ==================================================
    // VOTE LOGS (CAMPUS-SCOPED)
    // ==================================================

    @GetMapping("/vote-logs")
    public PageResponse<VoteLogDTO> voteLogs(
            HttpSession session,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String program,
            @RequestParam(required = false) String section,
            @RequestParam(required = false) String year,
            @RequestParam(required = false) String category,
            @RequestParam(required = false, defaultValue = "time-down") String sort,
            @RequestParam(required = false, defaultValue = "1") int page,
            @RequestParam(required = false, defaultValue = "false") boolean all
    ) {
        // campusId comes from the session only — a client-supplied one is ignored.
        return historyService.getVoteLogsForAdminSsc(
                sessionCampusId(session), search, program, section, year, category, sort, page, all
        );
    }

    @GetMapping("/vote-logs/filter-options")
    public Map<String, List<String>> voteLogFilterOptions(HttpSession session) {
        return historyService.getVoteLogFilterOptionsForAdminSsc(sessionCampusId(session));
    }

    // ==================================================
    // ACTIONS (OWN USER ONLY — ROLE COLUMN STILL POPULATED)
    // ==================================================

    @GetMapping("/actions")
    public PageResponse<AuditLogDTO> actions(
            HttpSession session,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String role,
            @RequestParam(required = false) String action,
            @RequestParam(required = false, defaultValue = "1") int page,
            @RequestParam(required = false, defaultValue = "false") boolean all
    ) {
        UserRole parsedRole = parseEnumOrNull(UserRole.class, role);
        AuditAction parsedAction = parseEnumOrNull(AuditAction.class, action);

        return historyService.getActionsForAdminSsc(
                sessionUserId(session), search, parsedRole, parsedAction, page, all
        );
    }

    // ==================================================
    // ARCHIVES (OWN USER ONLY)
    // ==================================================

    @GetMapping("/archives")
    public PageResponse<ArchiveRecordDTO> archives(
            HttpSession session,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String type,
            @RequestParam(required = false, defaultValue = "1") int page
    ) {
        return historyService.getArchivesForAdminSsc(sessionUserId(session), search, type, page);
    }

    @PostMapping("/archives/{id}/restore")
    public ResponseEntity<Void> restoreArchive(
            @PathVariable UUID id, HttpSession session, HttpServletRequest request
    ) {
        historyService.restoreArchiveForAdminSsc(id, sessionUserId(session), request);
        return ResponseEntity.noContent().build();
    }

    // ==================================================
    // TRASH (OWN USER ONLY)
    // ==================================================

    @GetMapping("/trash")
    public PageResponse<TrashRecordDTO> trash(
            HttpSession session,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String type,
            @RequestParam(required = false, defaultValue = "1") int page
    ) {
        return historyService.getTrashForAdminSsc(sessionUserId(session), search, type, page);
    }

    @PostMapping("/trash/{id}/restore")
    public ResponseEntity<Void> restoreTrash(
            @PathVariable UUID id, HttpSession session, HttpServletRequest request
    ) {
        historyService.restoreTrashForAdminSsc(id, sessionUserId(session), request);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/trash/{id}")
    public ResponseEntity<Void> deleteTrash(
            @PathVariable UUID id, HttpSession session, HttpServletRequest request
    ) {
        historyService.deleteTrashPermanentlyForAdminSsc(id, sessionUserId(session), request);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/trash")
    public ResponseEntity<Void> emptyTrash(HttpSession session, HttpServletRequest request) {
        historyService.emptyTrashForAdminSsc(sessionUserId(session), request);
        return ResponseEntity.noContent().build();
    }

    // ==================================================
    // ERROR HANDLING
    // ==================================================

    @ExceptionHandler(NoSuchElementException.class)
    public ResponseEntity<String> handleNotFound(NoSuchElementException e) {
        return ResponseEntity.status(404).body(e.getMessage());
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<String> handleForbidden(IllegalArgumentException e) {
        return ResponseEntity.status(403).body(e.getMessage());
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<String> handleConflict(IllegalStateException e) {
        return ResponseEntity.status(409).body(e.getMessage());
    }

    // ==================================================
    // HELPERS
    // ==================================================

    private UUID sessionUserId(HttpSession session) {
        return requiredSessionUuid(session, "userId");
    }

    private UUID sessionCampusId(HttpSession session) {
        return requiredSessionUuid(session, "campusId");
    }

    private UUID requiredSessionUuid(HttpSession session, String attribute) {
        Object value = session == null ? null : session.getAttribute(attribute);

        if (value == null || value.toString().isBlank()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Session expired. Please log in again.");
        }

        if (value instanceof UUID uuid) {
            return uuid;
        }

        try {
            return UUID.fromString(value.toString());
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid session " + attribute + ".");
        }
    }

    private <T extends Enum<T>> T parseEnumOrNull(Class<T> enumClass, String value) {
        if (value == null || value.isBlank() || value.equalsIgnoreCase("all")) return null;
        try {
            return Enum.valueOf(enumClass, value.toUpperCase().replace('-', '_'));
        } catch (IllegalArgumentException e) {
            return null; // unknown filter value -> treat as "no filter" rather than 400
        }
    }
}