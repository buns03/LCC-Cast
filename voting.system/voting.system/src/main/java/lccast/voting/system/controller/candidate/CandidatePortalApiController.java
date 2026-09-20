package lccast.voting.system.controller.candidate;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import lccast.voting.system.model.AuditAction;
import lccast.voting.system.service.AuditLogService;
import lccast.voting.system.service.CandidatePortalService;
import lccast.voting.system.service.SupabaseStorageService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/candidate/api")
public class CandidatePortalApiController {

    private final CandidatePortalService candidatePortalService;
    private final AuditLogService auditLogService;
    private final SupabaseStorageService supabaseStorageService;

    public CandidatePortalApiController(CandidatePortalService candidatePortalService,
                                        AuditLogService auditLogService,
                                        SupabaseStorageService supabaseStorageService) {
        this.candidatePortalService = candidatePortalService;
        this.auditLogService = auditLogService;
        this.supabaseStorageService = supabaseStorageService;
    }

    @GetMapping("/file")
    public ResponseEntity<byte[]> getFile(@RequestParam("path") String path) {
        return supabaseStorageService.downloadFile(path);
    }

    // ==================================================
    // PERSONAL INFORMATION
    // ==================================================

    @GetMapping("/personal-info")
    public ResponseEntity<?> getPersonalInfo(HttpSession session) {
        UUID authUserId = requireAuthUserId(session);
        var record = candidatePortalService.findRecordByAuthUserId(authUserId);

        if (record == null) {
            return ResponseEntity.status(404).body(Map.of("message", "No candidate record found."));
        }

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("studentId", record.studentId);
        body.put("lastName", record.lastName);
        body.put("firstName", record.firstName);
        body.put("middleName", record.middleName == null ? "" : record.middleName);
        body.put("position", record.position == null ? "" : record.position);
        body.put("isPresident", record.isPresident());
        body.put("photoUrl", fileUrl(record.photoImageUrl));
        body.put("backgroundUrl", fileUrl(record.backgroundImageUrl));
        body.put("campaignUrl", fileUrl(record.campaignImageUrl));

        return ResponseEntity.ok(body);
    }

    @PostMapping("/personal-info/photo")
    public ResponseEntity<?> uploadPhoto(@RequestParam("file") MultipartFile file, HttpSession session, HttpServletRequest request) {
        return handleUpload("photo", file, session, request);
    }

    @PostMapping("/personal-info/background")
    public ResponseEntity<?> uploadBackground(@RequestParam("file") MultipartFile file, HttpSession session, HttpServletRequest request) {
        return handleUpload("background", file, session, request);
    }

    @PostMapping("/personal-info/campaign")
    public ResponseEntity<?> uploadCampaign(@RequestParam("file") MultipartFile file, HttpSession session, HttpServletRequest request) {
        return handleUpload("campaign", file, session, request);
    }

    private ResponseEntity<?> handleUpload(String type, MultipartFile file, HttpSession session, HttpServletRequest request) {
        UUID authUserId = requireAuthUserId(session);
        try {
            String storagePath = candidatePortalService.uploadPersonalImage(authUserId, type, file);

            var record = candidatePortalService.findRecordByAuthUserId(authUserId);
            if (record != null) {
                auditLogService.log(request, AuditAction.UPDATE,
                        "CANDIDATE_" + type.toUpperCase() + "_IMAGE", record.id,
                        "updated their " + type + " image.",
                        Map.of("imageType", type, "studentId", record.studentId));
            }

            Map<String, Object> body = new LinkedHashMap<>();
            body.put("message", "Image updated successfully.");
            body.put("url", fileUrl(storagePath));
            return ResponseEntity.ok(body);
        } catch (IllegalArgumentException | IllegalStateException e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        } catch (IOException e) {
            return ResponseEntity.internalServerError().body(Map.of("message", "Upload failed."));
        }
    }

    // ==================================================
    // GROUP (PARTYLIST / DEPARTMENT) INFORMATION
    // ==================================================

    @GetMapping("/group-info")
    public ResponseEntity<?> getGroupInfo(HttpSession session) {
        UUID authUserId = requireAuthUserId(session);

        try {
            var info = candidatePortalService.getGroupInfo(authUserId);

            Map<String, Object> body = new LinkedHashMap<>();
            body.put("groupType", info.groupType);
            body.put("groupId", info.groupId);
            body.put("name", info.name);
            body.put("description", info.description == null ? "" : info.description);
            body.put("posterUrl", fileUrl(info.posterImageUrl));
            body.put("logoUrl", fileUrl(info.posterLogoUrl));
            body.put("schoolYear", info.schoolYear == null ? "" : info.schoolYear);
            body.put("canEdit", info.canEdit);
            body.put("members", info.members.stream().map(m -> {
                Map<String, Object> member = new LinkedHashMap<>();
                member.put("fullName", m.fullName);
                member.put("position", m.position == null ? "" : m.position);
                member.put("photoUrl", fileUrl(m.photoImageUrl));
                return member;
            }).toList());

            return ResponseEntity.ok(body);
        } catch (IllegalStateException e) {
            return ResponseEntity.status(404).body(Map.of("message", e.getMessage()));
        }
    }

    @PostMapping(value = "/group-info", consumes = "multipart/form-data")
    public ResponseEntity<?> updateGroupInfo(
            @RequestParam(required = false) String description,
            @RequestParam(required = false) MultipartFile poster,
            @RequestParam(required = false) MultipartFile logo,
            HttpSession session, HttpServletRequest request) {

        UUID authUserId = requireAuthUserId(session);
        try {
            candidatePortalService.updateGroupInfo(authUserId, description, poster, logo);

            var record = candidatePortalService.findRecordByAuthUserId(authUserId);
            UUID groupId = record != null ? (record.partylistId != null ? record.partylistId : record.departmentId) : null;
            String groupType = record != null && record.partylistId != null ? "PARTYLIST" : "DEPARTMENT";

            auditLogService.log(request, AuditAction.UPDATE, groupType, groupId,
                    "updated the " + groupType.toLowerCase() + " information.",
                    Map.of("descriptionChanged", description != null,
                            "posterChanged", poster != null && !poster.isEmpty(),
                            "logoChanged", logo != null && !logo.isEmpty()));

            return ResponseEntity.ok(Map.of("message", "Group information updated successfully."));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(403).body(Map.of("message", e.getMessage()));
        } catch (IllegalStateException e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        } catch (IOException e) {
            return ResponseEntity.internalServerError().body(Map.of("message", "Update failed."));
        }
    }

    // ==================================================
    // HELPERS
    // ==================================================

    private String fileUrl(String storagePath) {
        if (storagePath == null || storagePath.isBlank()) return null;
        return "/candidate/api/file?path=" + URLEncoder.encode(storagePath, StandardCharsets.UTF_8);
    }

    private UUID requireAuthUserId(HttpSession session) {
        String idStr = (String) session.getAttribute("userId");
        if (idStr == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Session expired. Please log in again.");
        }
        return UUID.fromString(idStr);
    }
}