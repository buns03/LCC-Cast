package lccast.voting.system.controller.superadmin;

import lccast.voting.system.model.Campus;
import lccast.voting.system.model.Partylist;
import lccast.voting.system.model.PartylistMember;
import lccast.voting.system.service.AuditLogService;
import lccast.voting.system.service.superadmin.PartylistService;
import lccast.voting.system.service.SupabaseStorageService;
import org.springframework.web.multipart.MultipartFile;
import jakarta.servlet.http.HttpServletRequest;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/superadmin/api/partylists")
public class SuperadminPartylistApiController {

    private final PartylistService partylistService;
    private final SupabaseStorageService supabaseStorageService;
    private final AuditLogService auditLogService;

    public SuperadminPartylistApiController(
            PartylistService partylistService,
            SupabaseStorageService supabaseStorageService,
            AuditLogService auditLogService
    ) {
        this.partylistService = partylistService;
        this.supabaseStorageService = supabaseStorageService;
        this.auditLogService = auditLogService;
    }

    @GetMapping
    public ResponseEntity<List<Partylist>> getActive() {
        return ResponseEntity.ok(
                partylistService.getActivePartylists()
        );
    }

    @GetMapping("/archived")
    public ResponseEntity<List<Partylist>> getArchived() {
        return ResponseEntity.ok(
                partylistService.getArchivedPartylists()
        );
    }

    @GetMapping("/trash")
    public ResponseEntity<List<Partylist>> getDeleted() {
        return ResponseEntity.ok(
                partylistService.getDeletedPartylists()
        );
    }

    @GetMapping("/campuses")
    public ResponseEntity<List<Campus>> getCampuses() {
        return ResponseEntity.ok(
                partylistService.getCampuses()
        );
    }

    @GetMapping("/file")
    public ResponseEntity<byte[]> getFile(
            @RequestParam("path") String path
    ) {
        return supabaseStorageService.getFile(path);
    }

    @GetMapping("/{id}")
    public ResponseEntity<Partylist> getById(
            @PathVariable UUID id
    ) {
        return ResponseEntity.ok(
                partylistService.getById(id)
        );
    }

    @GetMapping("/{id}/members")
    public ResponseEntity<List<PartylistMember>> getMembers(
            @PathVariable UUID id
    ) {
        return ResponseEntity.ok(
                partylistService.getMembers(id)
        );
    }

    @PostMapping
    public ResponseEntity<Partylist> create(
            @RequestBody Partylist partylist,
            HttpServletRequest request
    ) {
        return ResponseEntity.ok(
                partylistService.save(
                        partylist,
                        request
                )
        );
    }

    @PutMapping("/{id}")
    public ResponseEntity<Partylist> update(
            @PathVariable UUID id,
            @RequestBody Partylist partylist,
            HttpServletRequest request
    ) {
        return ResponseEntity.ok(
                partylistService.update(
                        id,
                        partylist,
                        request
                )
        );
    }

    @PutMapping("/{id}/archive")
    public ResponseEntity<Void> archive(
            @PathVariable UUID id,
            HttpServletRequest request
    ) {

        partylistService.archive(
                id,
                request
        );

        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(
            @PathVariable UUID id,
            HttpServletRequest request
    ) {

        partylistService.delete(
                id,
                request
        );

        return ResponseEntity.ok().build();
    }

    @PutMapping("/{id}/restore")
    public ResponseEntity<Void> restore(
            @PathVariable UUID id,
            HttpServletRequest request
    ) {

        partylistService.restore(
                id,
                request
        );

        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/{id}/permanent")
    public ResponseEntity<Void> permanentlyDelete(
            @PathVariable UUID id
    ) {

        partylistService.permanentlyDelete(id);

        return ResponseEntity.ok().build();
    }

    @GetMapping("/{id}/file/{type}")
    public ResponseEntity<byte[]> getPartylistFile(
            @PathVariable UUID id,
            @PathVariable String type
    ) {

        Partylist partylist =
                partylistService.getById(id);

        String storagePath = switch (type.toLowerCase()) {

            case "poster" ->
                    partylist.getPosterImageUrl();

            case "logo" ->
                    partylist.getPosterLogoUrl();

            default ->
                    throw new IllegalArgumentException(
                            "Invalid partylist file type."
                    );
        };

        return supabaseStorageService.downloadFile(
                storagePath
        );
    }

    @GetMapping("/{partylistId}/members/{memberId}/file/{type}")
    public ResponseEntity<byte[]> getMemberFile(
            @PathVariable UUID partylistId,
            @PathVariable UUID memberId,
            @PathVariable String type
    ) {

        PartylistMember member =
                partylistService.getMember(
                        partylistId,
                        memberId
                );

        String storagePath = switch (type.toLowerCase()) {

            case "campaign" ->
                    member.getCampaignImageUrl();

            case "background" ->
                    member.getBackgroundImageUrl();

            case "photo" ->
                    member.getPhotoImageUrl();

            default ->
                    throw new IllegalArgumentException(
                            "Invalid member file type."
                    );
        };

        return supabaseStorageService.downloadFile(
                storagePath
        );
    }


    @PostMapping("/upload")
    public ResponseEntity<?> uploadFile(
            @RequestParam("file") MultipartFile file,
            @RequestParam("type") String type
    ) {
        try {

            String path =
                    supabaseStorageService.uploadFile(
                            file,
                            type
                    );

            return ResponseEntity.ok(
                    java.util.Map.of(
                            "success", true,
                            "path", path
                    )
            );

        } catch (Exception e) {

            return ResponseEntity.badRequest().body(
                    java.util.Map.of(
                            "success", false,
                            "error",
                            e.getMessage() != null
                                    ? e.getMessage()
                                    : "File upload failed."
                    )
            );
        }
    }

    @PostMapping("/validate-members")
    public ResponseEntity<?> validateMembers(
            @RequestBody Partylist partylist
    ) {
        try {
            partylistService.validateMembers(
                    partylist.getId(),
                    partylist.getCampus() != null
                            ? partylist.getCampus().getId()
                            : null,
                    partylist.getSchoolYear(),
                    partylist.getMembers()
            );

            return ResponseEntity.ok(
                    java.util.Map.of(
                            "valid", true
                    )
            );

        } catch (IllegalArgumentException e) {

            return ResponseEntity.badRequest().body(
                    java.util.Map.of(
                            "valid", false,
                            "error", e.getMessage()
                    )
            );
        }
    }
}