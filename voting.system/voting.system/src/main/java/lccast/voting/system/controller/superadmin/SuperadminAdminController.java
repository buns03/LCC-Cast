package lccast.voting.system.controller.superadmin;

import lccast.voting.system.dto.AdminRequest;
import lccast.voting.system.dto.AdminResponse;
import lccast.voting.system.service.AdminService;
import jakarta.servlet.http.HttpSession;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/superadmin/api/admins")
public class SuperadminAdminController {

    private final AdminService adminService;

    public SuperadminAdminController(AdminService adminService) {
        this.adminService = adminService;
    }

    @GetMapping
    public List<AdminResponse> listAdmins() {
        return adminService.listAdmins();
    }

    @PostMapping
    public ResponseEntity<?> createAdmin(@RequestBody AdminRequest req) {
        try {
            AdminResponse created = adminService.createAdmin(req);
            return ResponseEntity.ok(created);
        } catch (IllegalArgumentException | IllegalStateException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @PutMapping("/{id}")
    public ResponseEntity<?> updateAdmin(@PathVariable UUID id, @RequestBody AdminRequest req) {
        try {
            AdminResponse updated = adminService.updateAdmin(id, req);
            return ResponseEntity.ok(updated);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @PatchMapping("/{id}/archive")
    public ResponseEntity<?> archiveAdmin(@PathVariable UUID id, HttpSession session) {
        try {
            UUID archivedBy = (UUID) session.getAttribute("authUserId");
            adminService.archiveAdmin(id, archivedBy);
            return ResponseEntity.ok().build();
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> deleteAdmin(@PathVariable UUID id, HttpSession session) {
        try {
            UUID deletedBy = (UUID) session.getAttribute("authUserId");
            adminService.deleteAdmin(id, deletedBy);
            return ResponseEntity.ok().build();
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @PatchMapping("/{id}/restore")
    public ResponseEntity<?> restoreAdmin(@PathVariable UUID id) {
        try {
            adminService.restoreAdmin(id);
            return ResponseEntity.ok().build();
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @DeleteMapping("/{id}/permanent")
    public ResponseEntity<?> permanentlyDeleteAdmin(@PathVariable UUID id) {
        try {
            adminService.permanentlyDeleteAdmin(id);
            return ResponseEntity.ok().build();
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }
}