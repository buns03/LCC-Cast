package lccast.voting.system.controller.superadmin;

import jakarta.servlet.http.HttpServletRequest;
import lccast.voting.system.dto.*;
import lccast.voting.system.model.AuditAction;
import lccast.voting.system.model.UserRole;
import lccast.voting.system.service.HistoryService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.UUID;

@RestController
@RequestMapping("/superadmin/api/history")
public class SuperadminHistoryApiController {

    private final HistoryService historyService;

    public SuperadminHistoryApiController(HistoryService historyService) {
        this.historyService = historyService;
    }

    // ==================================================
    // VOTE LOGS
    // ==================================================

    @GetMapping("/vote-logs")
    public PageResponse<VoteLogDTO> voteLogs(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String program,
            @RequestParam(required = false) String section,
            @RequestParam(required = false) String year,
            @RequestParam(required = false) String category,                    // ADD
            @RequestParam(required = false) UUID campusId,
            @RequestParam(required = false, defaultValue = "time-down") String sort,
            @RequestParam(required = false, defaultValue = "1") int page,
            @RequestParam(required = false, defaultValue = "false") boolean all // ADD
    ) {
        return historyService.getVoteLogs(search, program, section, year, category, campusId, sort, page, all);
    }

    @GetMapping("/vote-logs/filter-options")
    public Map<String, List<String>> voteLogFilterOptions() {
        return historyService.getVoteLogFilterOptions();
    }

    // ==================================================
    // ACTIONS (AUDIT LOGS)
    // ==================================================

    @GetMapping("/actions")
    public PageResponse<AuditLogDTO> actions(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String role,
            @RequestParam(required = false) String action,
            @RequestParam(required = false, defaultValue = "1") int page,
            @RequestParam(required = false, defaultValue = "false") boolean all // ADD
    ) {
        UserRole parsedRole = parseEnumOrNull(UserRole.class, role);
        AuditAction parsedAction = parseEnumOrNull(AuditAction.class, action);
        return historyService.getActions(search, parsedRole, parsedAction, page, all);
    }

    // ==================================================
    // ARCHIVES
    // ==================================================

    @GetMapping("/archives")
    public PageResponse<ArchiveRecordDTO> archives(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String type,
            @RequestParam(required = false, defaultValue = "1") int page
    ) {
        return historyService.getArchives(search, type, page);
    }

    @PostMapping("/archives/{id}/restore")
    public ResponseEntity<Void> restoreArchive(
            @PathVariable UUID id, HttpServletRequest request
    ) {
        historyService.restoreArchive(id, request);
        return ResponseEntity.noContent().build();
    }

    // ==================================================
    // TRASH
    // ==================================================

    @GetMapping("/trash")
    public PageResponse<TrashRecordDTO> trash(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String type,
            @RequestParam(required = false, defaultValue = "1") int page
    ) {
        return historyService.getTrash(search, type, page);
    }

    @PostMapping("/trash/{id}/restore")
    public ResponseEntity<Void> restoreTrash(
            @PathVariable UUID id, HttpServletRequest request
    ) {
        historyService.restoreTrash(id, request);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/trash/{id}")
    public ResponseEntity<Void> deleteTrash(
            @PathVariable UUID id, HttpServletRequest request
    ) {
        historyService.deleteTrashPermanently(id, request);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/trash")
    public ResponseEntity<Void> emptyTrash(HttpServletRequest request) {
        historyService.emptyTrash(request);
        return ResponseEntity.noContent().build();
    }

    // ==================================================
    // ERROR HANDLING
    // ==================================================

    @ExceptionHandler(NoSuchElementException.class)
    public ResponseEntity<String> handleNotFound(NoSuchElementException e) {
        return ResponseEntity.status(404).body(e.getMessage());
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<String> handleConflict(IllegalStateException e) {
        return ResponseEntity.status(409).body(e.getMessage());
    }

    // ==================================================
    // HELPERS
    // ==================================================

    private <T extends Enum<T>> T parseEnumOrNull(Class<T> enumClass, String value) {
        if (value == null || value.isBlank() || value.equalsIgnoreCase("all")) return null;
        try {
            return Enum.valueOf(enumClass, value.toUpperCase());
        } catch (IllegalArgumentException e) {
            return null; // unknown filter value -> treat as "no filter" rather than 400
        }
    }
}