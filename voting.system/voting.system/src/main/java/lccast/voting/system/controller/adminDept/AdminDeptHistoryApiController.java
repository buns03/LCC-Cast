package lccast.voting.system.controller.adminDept;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import lccast.voting.system.dto.*;
import lccast.voting.system.model.AuditAction;
import lccast.voting.system.service.HistoryService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.UUID;

@RestController
@RequestMapping("/api/admin-dept/history")
public class AdminDeptHistoryApiController {

    private final HistoryService historyService;

    public AdminDeptHistoryApiController(HistoryService historyService) {
        this.historyService = historyService;
    }

    private UUID sessionCampusId(HttpSession session) {
        Object attr = session.getAttribute("campusId");
        if (attr == null) return null;
        return attr instanceof UUID ? (UUID) attr : UUID.fromString(attr.toString());
    }

    private UUID sessionUserId(HttpSession session) {
        Object attr = session.getAttribute("userId");
        if (attr == null) return null;
        return attr instanceof UUID ? (UUID) attr : UUID.fromString(attr.toString());
    }

    // ==================================================
    // VOTE LOGS
    // ==================================================

    @GetMapping("/vote-logs")
    public PageResponse<VoteLogDTO> voteLogs(
            HttpSession session,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String section,
            @RequestParam(required = false) String year,
            @RequestParam(required = false, defaultValue = "time-down") String sort,
            @RequestParam(required = false, defaultValue = "1") int page,
            @RequestParam(required = false, defaultValue = "false") boolean all
    ) {
        UUID campusId = sessionCampusId(session);
        String programCourse = (String) session.getAttribute("programCourse");

        if (campusId == null || programCourse == null) {
            return new PageResponse<>(List.of(), 1, 1, 0);
        }

        return historyService.getVoteLogsForAdminDept(campusId, programCourse, search, section, year, sort, page, all);
    }

    @GetMapping("/vote-logs/filter-options")
    public Map<String, List<String>> voteLogFilterOptions(HttpSession session) {
        UUID campusId = sessionCampusId(session);
        String programCourse = (String) session.getAttribute("programCourse");

        if (campusId == null || programCourse == null) {
            return Map.of("sections", List.of(), "yearLevels", List.of());
        }

        return historyService.getVoteLogFilterOptionsForAdminDept(campusId, programCourse);
    }

    // ==================================================
    // ACTIONS
    // ==================================================

    @GetMapping("/actions")
    public PageResponse<AuditLogDTO> actions(
            HttpSession session,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String action,
            @RequestParam(required = false, defaultValue = "1") int page,
            @RequestParam(required = false, defaultValue = "false") boolean all
    ) {
        UUID userId = sessionUserId(session);
        if (userId == null) return new PageResponse<>(List.of(), 1, 1, 0);

        return historyService.getActionsForAdminDept(userId, search, parseEnumOrNull(AuditAction.class, action), page, all);
    }

    // ==================================================
    // ARCHIVES
    // ==================================================

    @GetMapping("/archives")
    public PageResponse<ArchiveRecordDTO> archives(
            HttpSession session,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String type,
            @RequestParam(required = false, defaultValue = "1") int page
    ) {
        UUID userId = sessionUserId(session);
        if (userId == null) return new PageResponse<>(List.of(), 1, 1, 0);

        return historyService.getArchivesForAdminDept(userId, search, type, page);
    }

    @PostMapping("/archives/{id}/restore")
    public ResponseEntity<Void> restoreArchive(@PathVariable UUID id, HttpSession session, HttpServletRequest request) {
        UUID userId = sessionUserId(session);
        if (userId == null) return ResponseEntity.status(403).build();

        historyService.restoreArchiveForAdminDept(id, userId, request);
        return ResponseEntity.noContent().build();
    }

    // ==================================================
    // TRASH
    // ==================================================

    @GetMapping("/trash")
    public PageResponse<TrashRecordDTO> trash(
            HttpSession session,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String type,
            @RequestParam(required = false, defaultValue = "1") int page
    ) {
        UUID userId = sessionUserId(session);
        if (userId == null) return new PageResponse<>(List.of(), 1, 1, 0);

        return historyService.getTrashForAdminDept(userId, search, type, page);
    }

    @PostMapping("/trash/{id}/restore")
    public ResponseEntity<Void> restoreTrash(@PathVariable UUID id, HttpSession session, HttpServletRequest request) {
        UUID userId = sessionUserId(session);
        if (userId == null) return ResponseEntity.status(403).build();

        historyService.restoreTrashForAdminDept(id, userId, request);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/trash/{id}")
    public ResponseEntity<Void> deleteTrash(@PathVariable UUID id, HttpSession session, HttpServletRequest request) {
        UUID userId = sessionUserId(session);
        if (userId == null) return ResponseEntity.status(403).build();

        historyService.deleteTrashPermanentlyForAdminDept(id, userId, request);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/trash")
    public ResponseEntity<Void> emptyTrash(HttpSession session, HttpServletRequest request) {
        UUID userId = sessionUserId(session);
        if (userId == null) return ResponseEntity.status(403).build();

        historyService.emptyTrashForAdminDept(userId, request);
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

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<String> handleForbidden(IllegalArgumentException e) {
        return ResponseEntity.status(403).body(e.getMessage());
    }

    private <T extends Enum<T>> T parseEnumOrNull(Class<T> enumClass, String value) {
        if (value == null || value.isBlank() || value.equalsIgnoreCase("all")) return null;
        try {
            return Enum.valueOf(enumClass, value.toUpperCase());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}