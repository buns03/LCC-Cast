package lccast.voting.system.controller.candidate;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import lccast.voting.system.model.AuditAction;
import lccast.voting.system.service.AuditLogService;
import lccast.voting.system.service.CandidatePortalService;
import lccast.voting.system.service.CandidatePortalService.CandidateType;
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
    // CANDIDACY STATUS — drives which tabs the UI shows
    // ==================================================

    @GetMapping("/candidacy-status")
    public ResponseEntity<?> getCandidacyStatus(HttpSession session) {
        UUID authUserId = requireAuthUserId(session);
        var status = candidatePortalService.getCandidacyStatus(authUserId);
        return ResponseEntity.ok(Map.of("ssc", status.ssc, "department", status.department));
    }

    // ==================================================
    // PERSONAL INFORMATION (per candidate type)
    // ==================================================

    @GetMapping("/{type}/personal-info")
    public ResponseEntity<?> getPersonalInfo(@PathVariable String type, HttpSession session) {
        UUID authUserId = requireAuthUserId(session);
        CandidateType candidateType = parseType(type);

        var record = candidatePortalService.findRecordByAuthUserId(authUserId, candidateType);
        if (record == null) {
            return ResponseEntity.status(404).body(Map.of(
                    "message", candidateType == CandidateType.SSC
                            ? "You are not currently an SSC candidate."
                            : "You are not currently a department candidate."));
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

    @PostMapping("/{type}/personal-info/photo")
    public ResponseEntity<?> uploadPhoto(@PathVariable String type, @RequestParam("file") MultipartFile file,
                                         HttpSession session, HttpServletRequest request) {
        return handleUpload(parseType(type), "photo", file, session, request);
    }

    @PostMapping("/{type}/personal-info/background")
    public ResponseEntity<?> uploadBackground(@PathVariable String type, @RequestParam("file") MultipartFile file,
                                              HttpSession session, HttpServletRequest request) {
        return handleUpload(parseType(type), "background", file, session, request);
    }

    @PostMapping("/{type}/personal-info/campaign")
    public ResponseEntity<?> uploadCampaign(@PathVariable String type, @RequestParam("file") MultipartFile file,
                                            HttpSession session, HttpServletRequest request) {
        return handleUpload(parseType(type), "campaign", file, session, request);
    }

    private ResponseEntity<?> handleUpload(CandidateType candidateType, String imageType, MultipartFile file,
                                           HttpSession session, HttpServletRequest request) {
        UUID authUserId = requireAuthUserId(session);
        try {
            String storagePath = candidatePortalService.uploadPersonalImage(authUserId, candidateType, imageType, file);

            var record = candidatePortalService.findRecordByAuthUserId(authUserId, candidateType);
            if (record != null) {
                auditLogService.log(request, AuditAction.UPDATE,
                        "CANDIDATE_" + candidateType.name() + "_" + imageType.toUpperCase() + "_IMAGE", record.id,
                        "updated their " + candidateType.name().toLowerCase() + " " + imageType + " image.",
                        Map.of("imageType", imageType, "candidateType", candidateType.name(), "studentId", record.studentId));
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

    @GetMapping("/{type}/group-info")
    public ResponseEntity<?> getGroupInfo(@PathVariable String type, HttpSession session) {
        UUID authUserId = requireAuthUserId(session);
        CandidateType candidateType = parseType(type);

        try {
            var info = candidatePortalService.getGroupInfo(authUserId, candidateType);

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

    @PostMapping(value = "/{type}/group-info", consumes = "multipart/form-data")
    public ResponseEntity<?> updateGroupInfo(
            @PathVariable String type,
            @RequestParam(required = false) String description,
            @RequestParam(required = false) MultipartFile poster,
            @RequestParam(required = false) MultipartFile logo,
            HttpSession session, HttpServletRequest request) {

        UUID authUserId = requireAuthUserId(session);
        CandidateType candidateType = parseType(type);

        try {
            candidatePortalService.updateGroupInfo(authUserId, candidateType, description, poster, logo);

            var record = candidatePortalService.findRecordByAuthUserId(authUserId, candidateType);
            UUID groupId = record != null
                    ? (candidateType == CandidateType.SSC ? record.partylistId : record.departmentId)
                    : null;
            String groupType = candidateType == CandidateType.SSC ? "PARTYLIST" : "DEPARTMENT";

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

    private CandidateType parseType(String type) {
        if ("ssc".equalsIgnoreCase(type)) return CandidateType.SSC;
        if ("department".equalsIgnoreCase(type)) return CandidateType.DEPARTMENT;
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid candidate type. Use 'ssc' or 'department'.");
    }

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