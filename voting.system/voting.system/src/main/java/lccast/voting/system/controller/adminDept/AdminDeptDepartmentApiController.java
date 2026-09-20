package lccast.voting.system.controller.adminDept;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import lccast.voting.system.model.*;
import lccast.voting.system.repository.DepartmentRepository;
import lccast.voting.system.service.SupabaseStorageService;
import lccast.voting.system.service.superadmin.DepartmentService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/admin-dept/api/departments")
public class AdminDeptDepartmentApiController {

    private final DepartmentService departmentService;
    private final DepartmentRepository departmentRepository;
    private final SupabaseStorageService supabaseStorageService;

    public AdminDeptDepartmentApiController(
            DepartmentService departmentService,
            DepartmentRepository departmentRepository,
            SupabaseStorageService supabaseStorageService
    ) {
        this.departmentService = departmentService;
        this.departmentRepository = departmentRepository;
        this.supabaseStorageService = supabaseStorageService;
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

    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<Map<String, String>> handleRuntime(RuntimeException ex) {
        return ResponseEntity.badRequest().body(Map.of("message", ex.getMessage()));
    }

    // =====================================================
    // SCOPE — session is the ONLY source of truth
    // =====================================================

    private record Scope(UUID campusId, String programCourse) {}


    private Scope requireScope(HttpSession session) {
        UUID campusId = (UUID) session.getAttribute("campusId");
        String programCourse = (String) session.getAttribute("programCourse");
        if (campusId == null || programCourse == null || programCourse.isBlank()) {
            throw new IllegalStateException(
                    "Missing campusId/programCourse in session — admin-dept user not properly scoped.");
        }
        return new Scope(campusId, programCourse);
    }

    private boolean inScope(Department department, Scope scope) {
        return department.getCampus() != null
                && scope.campusId().equals(department.getCampus().getId())
                && department.getName() != null
                && department.getName().trim().equalsIgnoreCase(scope.programCourse().trim());
    }

    // =====================================================
    // GET — only this admin's own campus + department
    // =====================================================

    @GetMapping
    public ResponseEntity<List<Department>> getActive(HttpSession session) {
        Scope scope = requireScope(session);
        return ResponseEntity.ok(departmentRepository.findByCampusIdAndNameIgnoreCaseAndStatus(
                scope.campusId(), scope.programCourse(), RecordStatus.ACTIVE));
    }

    @GetMapping("/{id}")
    public ResponseEntity<?> getById(@PathVariable UUID id, HttpSession session) {
        Scope scope = requireScope(session);
        Department department = departmentService.getById(id);
        if (!inScope(department, scope)) return ResponseEntity.notFound().build();
        return ResponseEntity.ok(department);
    }

    @GetMapping("/{id}/members")
    public ResponseEntity<?> getMembers(@PathVariable UUID id, HttpSession session) {
        Scope scope = requireScope(session);
        Department department = departmentService.getById(id);
        if (!inScope(department, scope)) return ResponseEntity.notFound().build();
        return ResponseEntity.ok(departmentService.getMembers(id));
    }

    // Student lookup — campus & department name are ALWAYS the session's.
    // No campus/department path segments at all: nothing for the client to spoof.
    @GetMapping("/members/student/{studentId}")
    public ResponseEntity<?> lookupStudent(@PathVariable String studentId, HttpSession session) {
        Scope scope = requireScope(session);
        try {
            return ResponseEntity.ok(departmentService.getStudentForDepartmentMemberByCampusAndName(
                    studentId, scope.campusId(), scope.programCourse()));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", e.getMessage()));
        }
    }

    @GetMapping("/members/student/{studentId}/school-year/{schoolYear}")
    public ResponseEntity<?> getStudentDepartmentMembersBySchoolYear(
            @PathVariable String studentId,
            @PathVariable String schoolYear,
            @RequestParam(required = false) UUID excludeDepartmentId,
            HttpSession session
    ) {
        requireScope(session);
        try {
            return ResponseEntity.ok(departmentService.getStudentDepartmentMembersBySchoolYear(
                    studentId, schoolYear, excludeDepartmentId));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", e.getMessage()));
        }
    }

    @GetMapping("/file")
    public ResponseEntity<byte[]> getFile(@RequestParam("path") String path) {
        return supabaseStorageService.getFile(path);
    }

    // =====================================================
    // CREATE — campus & name are forced from session, never
    // from the request body, regardless of what's sent.
    // =====================================================

    @PostMapping
    public ResponseEntity<?> create(
            @RequestBody Department department, HttpSession session, HttpServletRequest request
    ) {
        Scope scope = requireScope(session);

        Campus campus = new Campus();
        campus.setId(scope.campusId());
        department.setCampus(campus);
        department.setName(scope.programCourse());

        try {
            return ResponseEntity.ok(departmentService.save(department, request));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", e.getMessage()));
        }
    }

    @PutMapping("/{id}")
    public ResponseEntity<?> update(
            @PathVariable UUID id, @RequestBody Department department,
            HttpSession session, HttpServletRequest request
    ) {
        Scope scope = requireScope(session);

        Department existing = departmentService.getById(id);
        if (!inScope(existing, scope)) return ResponseEntity.notFound().build();

        Campus campus = new Campus();
        campus.setId(scope.campusId());
        department.setCampus(campus);
        department.setName(scope.programCourse());

        try {
            return ResponseEntity.ok(departmentService.update(id, department, request));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", e.getMessage()));
        }
    }

    @PutMapping("/{id}/archive")
    public ResponseEntity<?> archive(@PathVariable UUID id, HttpSession session, HttpServletRequest request) {
        Scope scope = requireScope(session);
        Department existing = departmentService.getById(id);
        if (!inScope(existing, scope)) return ResponseEntity.notFound().build();
        departmentService.archive(id, request);
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> delete(@PathVariable UUID id, HttpSession session, HttpServletRequest request) {
        Scope scope = requireScope(session);
        Department existing = departmentService.getById(id);
        if (!inScope(existing, scope)) return ResponseEntity.notFound().build();
        departmentService.delete(id, request);
        return ResponseEntity.ok().build();
    }

    @PutMapping("/{id}/restore")
    public ResponseEntity<?> restore(@PathVariable UUID id, HttpSession session, HttpServletRequest request) {
        Scope scope = requireScope(session);
        Department existing = departmentService.getById(id);
        if (!inScope(existing, scope)) return ResponseEntity.notFound().build();
        departmentService.restore(id, request);
        return ResponseEntity.ok().build();
    }

    // Permanent delete intentionally NOT exposed here — superadmin only.

    @GetMapping("/{id}/file/{type}")
    public ResponseEntity<byte[]> getDepartmentFile(
            @PathVariable UUID id, @PathVariable String type, HttpSession session
    ) {
        Scope scope = requireScope(session);
        Department department = departmentService.getById(id);
        if (!inScope(department, scope)) return ResponseEntity.notFound().build();

        String storagePath = switch (type.toLowerCase()) {
            case "poster" -> department.getPosterImageUrl();
            case "logo" -> department.getPosterLogoUrl();
            default -> throw new IllegalArgumentException("Invalid department file type.");
        };
        return supabaseStorageService.downloadFile(storagePath);
    }

    @GetMapping("/{departmentId}/members/{memberId}/file/{type}")
    public ResponseEntity<byte[]> getMemberFile(
            @PathVariable UUID departmentId, @PathVariable UUID memberId,
            @PathVariable String type, HttpSession session
    ) {
        Scope scope = requireScope(session);
        Department department = departmentService.getById(departmentId);
        if (!inScope(department, scope)) return ResponseEntity.notFound().build();

        DepartmentMember member = departmentService.getMember(departmentId, memberId);
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
            @RequestParam("file") MultipartFile file, @RequestParam("type") String type, HttpSession session
    ) {
        requireScope(session);
        try {
            String path = supabaseStorageService.uploadFile(file, type);
            return ResponseEntity.ok(Map.of("success", true, "path", path));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "error", e.getMessage() != null ? e.getMessage() : "File upload failed."));
        }
    }
}