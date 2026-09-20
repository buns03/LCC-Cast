package lccast.voting.system.controller.admin;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import lccast.voting.system.model.Partylist;
import lccast.voting.system.model.PartylistMember;
import lccast.voting.system.model.Voter;
import lccast.voting.system.service.SupabaseStorageService;
import lccast.voting.system.service.admin.AdminSscPartylistService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/admin-ssc/api/partylists")
public class AdminSscPartylistApiController {

    private final AdminSscPartylistService partylistService;
    private final SupabaseStorageService supabaseStorageService;

    public AdminSscPartylistApiController(
            AdminSscPartylistService partylistService,
            SupabaseStorageService supabaseStorageService
    ) {
        this.partylistService = partylistService;
        this.supabaseStorageService = supabaseStorageService;
    }

    private UUID requireCampusId(HttpSession session) {
        Object campusId = session.getAttribute("campusId");
        if (campusId == null) {
            throw new RuntimeException("No campus is associated with this account.");
        }
        return campusId instanceof UUID ? (UUID) campusId : UUID.fromString(campusId.toString());
    }

    @GetMapping
    public ResponseEntity<List<Partylist>> getActive(HttpSession session) {
        return ResponseEntity.ok(partylistService.getActivePartylists(requireCampusId(session)));
    }

    @GetMapping("/file")
    public ResponseEntity<byte[]> getFile(@RequestParam("path") String path) {
        return supabaseStorageService.getFile(path);
    }

    @GetMapping("/{id}")
    public ResponseEntity<Partylist> getById(@PathVariable UUID id, HttpSession session) {
        return ResponseEntity.ok(partylistService.getById(id, requireCampusId(session)));
    }

    @GetMapping("/{id}/members")
    public ResponseEntity<List<PartylistMember>> getMembers(@PathVariable UUID id, HttpSession session) {
        return ResponseEntity.ok(partylistService.getMembers(id, requireCampusId(session)));
    }

    @GetMapping("/voters")
    public ResponseEntity<List<Voter>> getVoters(HttpSession session) {
        return ResponseEntity.ok(partylistService.getVotersForCampus(requireCampusId(session)));
    }

    @GetMapping("/members/student/{studentId}")
    public ResponseEntity<?> lookupStudent(@PathVariable String studentId, HttpSession session) {
        return partylistService.findVoterByStudentId(studentId, requireCampusId(session))
                .<ResponseEntity<?>>map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @PostMapping
    public ResponseEntity<Partylist> create(
            @RequestBody Partylist partylist, HttpServletRequest request, HttpSession session
    ) {
        return ResponseEntity.ok(partylistService.save(partylist, requireCampusId(session), request));
    }

    @PutMapping("/{id}")
    public ResponseEntity<Partylist> update(
            @PathVariable UUID id, @RequestBody Partylist partylist,
            HttpServletRequest request, HttpSession session
    ) {
        return ResponseEntity.ok(partylistService.update(id, partylist, requireCampusId(session), request));
    }

    @PutMapping("/{id}/archive")
    public ResponseEntity<Void> archive(@PathVariable UUID id, HttpServletRequest request, HttpSession session) {
        partylistService.archive(id, requireCampusId(session), request);
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id, HttpServletRequest request, HttpSession session) {
        partylistService.delete(id, requireCampusId(session), request);
        return ResponseEntity.ok().build();
    }

    @PutMapping("/{id}/restore")
    public ResponseEntity<Void> restore(@PathVariable UUID id, HttpServletRequest request, HttpSession session) {
        partylistService.restore(id, requireCampusId(session), request);
        return ResponseEntity.ok().build();
    }

    @GetMapping("/{id}/file/{type}")
    public ResponseEntity<byte[]> getPartylistFile(
            @PathVariable UUID id, @PathVariable String type, HttpSession session
    ) {
        Partylist partylist = partylistService.getById(id, requireCampusId(session));
        String storagePath = switch (type.toLowerCase()) {
            case "poster" -> partylist.getPosterImageUrl();
            case "logo" -> partylist.getPosterLogoUrl();
            default -> throw new IllegalArgumentException("Invalid partylist file type.");
        };
        return supabaseStorageService.downloadFile(storagePath);
    }

    @GetMapping("/{partylistId}/members/{memberId}/file/{type}")
    public ResponseEntity<byte[]> getMemberFile(
            @PathVariable UUID partylistId, @PathVariable UUID memberId,
            @PathVariable String type, HttpSession session
    ) {
        PartylistMember member = partylistService.getMember(partylistId, memberId, requireCampusId(session));
        String storagePath = switch (type.toLowerCase()) {
            case "campaign" -> member.getCampaignImageUrl();
            case "background" -> member.getBackgroundImageUrl();
            case "photo" -> member.getPhotoImageUrl();
            default -> throw new IllegalArgumentException("Invalid member file type.");
        };
        return supabaseStorageService.downloadFile(storagePath);
    }

    @PostMapping("/upload")
    public ResponseEntity<?> uploadFile(
            @RequestParam("file") MultipartFile file, @RequestParam("type") String type
    ) {
        try {
            String path = supabaseStorageService.uploadFile(file, type);
            return ResponseEntity.ok(Map.of("success", true, "path", path));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "error", e.getMessage() != null ? e.getMessage() : "File upload failed."
            ));
        }
    }

    @PostMapping("/validate-members")
    public ResponseEntity<?> validateMembers(@RequestBody Partylist partylist, HttpSession session) {
        try {
            partylistService.validateMembers(
                    partylist.getId(), requireCampusId(session), partylist.getSchoolYear(), partylist.getMembers()
            );
            return ResponseEntity.ok(Map.of("valid", true));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("valid", false, "error", e.getMessage()));
        }
    }
}