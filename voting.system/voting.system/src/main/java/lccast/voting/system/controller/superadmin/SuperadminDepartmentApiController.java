package lccast.voting.system.controller.superadmin;

import lccast.voting.system.model.Campus;
import lccast.voting.system.model.Department;
import lccast.voting.system.model.DepartmentMember;
import lccast.voting.system.service.AuditLogService;
import lccast.voting.system.service.superadmin.DepartmentService;
import lccast.voting.system.service.SupabaseStorageService;
import org.springframework.web.multipart.MultipartFile;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/superadmin/api/departments")
public class SuperadminDepartmentApiController {

    private final DepartmentService departmentService;
    private final SupabaseStorageService supabaseStorageService;
    private final AuditLogService auditLogService;

    public SuperadminDepartmentApiController(
            DepartmentService departmentService,
            SupabaseStorageService supabaseStorageService,
            AuditLogService auditLogService
    ) {
        this.departmentService = departmentService;
        this.supabaseStorageService = supabaseStorageService;
        this.auditLogService = auditLogService;
    }

    @GetMapping
    public ResponseEntity<List<Department>> getActive() {
        return ResponseEntity.ok(
                departmentService.getActiveDepartments()
        );
    }

    @GetMapping("/archived")
    public ResponseEntity<List<Department>> getArchived() {
        return ResponseEntity.ok(
                departmentService.getArchivedDepartments()
        );
    }

    @GetMapping("/trash")
    public ResponseEntity<List<Department>> getDeleted() {
        return ResponseEntity.ok(
                departmentService.getDeletedDepartments()
        );
    }

    @GetMapping("/campuses")
    public ResponseEntity<List<Campus>> getCampuses() {
        return ResponseEntity.ok(
                departmentService.getCampuses()
        );
    }

    @GetMapping("/{departmentId}/members/student/{studentId}")
    public ResponseEntity<?> getStudentForDepartmentMember(
            @PathVariable UUID departmentId,
            @PathVariable String studentId
    ) {
        try {

            return ResponseEntity.ok(
                    departmentService.getStudentForDepartmentMember(
                            studentId,
                            departmentId
                    )
            );

        } catch (IllegalArgumentException e) {

            return ResponseEntity
                    .badRequest()
                    .body(
                            Map.of(
                                    "success", false,
                                    "message", e.getMessage()
                            )
                    );
        }
    }

    @GetMapping("/members/student/{studentId}/school-year/{schoolYear}")
    public ResponseEntity<?> getStudentDepartmentMembersBySchoolYear(
            @PathVariable String studentId,
            @PathVariable String schoolYear,
            @RequestParam(required = false) UUID excludeDepartmentId
    ) {
        try {
            return ResponseEntity.ok(
                    departmentService
                            .getStudentDepartmentMembersBySchoolYear(
                                    studentId,
                                    schoolYear,
                                    excludeDepartmentId
                            )
            );
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(
                    Map.of(
                            "success", false,
                            "message", e.getMessage()
                    )
            );
        }
    }

    @GetMapping("/file")
    public ResponseEntity<byte[]> getFile(
            @RequestParam("path") String path
    ) {
        return supabaseStorageService.getFile(path);
    }

    @GetMapping("/members/student/{studentId}/campus/{campusId}/department/{departmentId}")
    public ResponseEntity<?> getStudentForDepartmentMemberByCampus(
            @PathVariable String studentId,
            @PathVariable UUID campusId,
            @PathVariable UUID departmentId
    ) {
        try {

            return ResponseEntity.ok(
                    departmentService
                            .getStudentForDepartmentMemberByCampus(
                                    studentId,
                                    campusId,
                                    departmentId
                            )
            );

        } catch (IllegalArgumentException e) {

            return ResponseEntity
                    .badRequest()
                    .body(
                            Map.of(
                                    "success", false,
                                    "message", e.getMessage()
                            )
                    );
        }
    }

    @GetMapping("/members/student/{studentId}/campus/{campusId}/department-name/{departmentName}")
    public ResponseEntity<?> getStudentForDepartmentMemberByCampusAndName(
            @PathVariable String studentId,
            @PathVariable UUID campusId,
            @PathVariable String departmentName
    ) {
        try {
            return ResponseEntity.ok(
                    departmentService
                            .getStudentForDepartmentMemberByCampusAndName(
                                    studentId,
                                    campusId,
                                    departmentName
                            )
            );
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(
                    Map.of(
                            "success", false,
                            "message", e.getMessage()
                    )
            );
        }
    }

    @GetMapping("/{id}")
    public ResponseEntity<Department> getById(
            @PathVariable UUID id
    ) {
        return ResponseEntity.ok(
                departmentService.getById(id)
        );
    }

    @GetMapping("/{id}/members")
    public ResponseEntity<List<DepartmentMember>> getMembers(
            @PathVariable UUID id
    ) {
        return ResponseEntity.ok(
                departmentService.getMembers(id)
        );
    }

    @PostMapping
    public ResponseEntity<?> create(
            @RequestBody Department department,
            HttpServletRequest request
    ) {
        try {
            return ResponseEntity.ok(
                    departmentService.save(
                            department,
                            request
                    )
            );
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(
                    Map.of(
                            "success", false,
                            "message", e.getMessage()
                    )
            );
        }
    }

    @PutMapping("/{id}")
    public ResponseEntity<Department> update(
            @PathVariable UUID id,
            @RequestBody Department department,
            HttpServletRequest request
    ) {
        return ResponseEntity.ok(
                departmentService.update(
                        id,
                        department,
                        request
                )
        );
    }

    @PutMapping("/{id}/archive")
    public ResponseEntity<Void> archive(
            @PathVariable UUID id,
            HttpServletRequest request
    ) {

        departmentService.archive(
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

        departmentService.delete(
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

        departmentService.restore(
                id,
                request
        );

        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/{id}/permanent")
    public ResponseEntity<Void> permanentlyDelete(
            @PathVariable UUID id
    ) {

        departmentService.permanentlyDelete(id);

        return ResponseEntity.ok().build();
    }

    @GetMapping("/{id}/file/{type}")
    public ResponseEntity<byte[]> getDepartmentFile(
            @PathVariable UUID id,
            @PathVariable String type
    ) {

        Department department =
                departmentService.getById(id);

        String storagePath = switch (type.toLowerCase()) {

            case "poster" ->
                    department.getPosterImageUrl();

            case "logo" ->
                    department.getPosterLogoUrl();

            default ->
                    throw new IllegalArgumentException(
                            "Invalid department file type."
                    );
        };

        return supabaseStorageService.downloadFile(
                storagePath
        );
    }

    @GetMapping("/{departmentId}/members/{memberId}/file/{type}")
    public ResponseEntity<byte[]> getMemberFile(
            @PathVariable UUID departmentId,
            @PathVariable UUID memberId,
            @PathVariable String type
    ) {

        DepartmentMember member =
                departmentService.getMember(
                        departmentId,
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
}