package lccast.voting.system.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import lccast.voting.system.dto.AdminRequest;
import lccast.voting.system.dto.AdminResponse;
import lccast.voting.system.model.*;
import lccast.voting.system.repository.*;
import org.springframework.stereotype.Service;
import lccast.voting.system.model.AdminDepartment;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class AdminService {

    private final UserProfileRepository userProfileRepository;
    private final CampusRepository campusRepository;
    private final ArchiveRecordRepository archiveRecordRepository;
    private final TrashRecordRepository trashRecordRepository;
    private final SupabaseAuthService supabaseAuthService;
    private final ObjectMapper objectMapper = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    public AdminService(UserProfileRepository userProfileRepository,
                        CampusRepository campusRepository,
                        ArchiveRecordRepository archiveRecordRepository,
                        TrashRecordRepository trashRecordRepository,
                        SupabaseAuthService supabaseAuthService) {
        this.userProfileRepository = userProfileRepository;
        this.campusRepository = campusRepository;
        this.archiveRecordRepository = archiveRecordRepository;
        this.trashRecordRepository = trashRecordRepository;
        this.supabaseAuthService = supabaseAuthService;
    }

    public List<AdminResponse> listAdmins() {

        return userProfileRepository.findAll().stream()
                .filter(p -> p.getRole() == UserRole.ADMIN)
                .filter(UserProfile::isActive)
                .sorted(
                        Comparator.comparing(
                                UserProfile::getCreatedAt,
                                Comparator.nullsLast(Comparator.reverseOrder())
                        )
                )
                .map(this::toResponse)
                .toList();
    }

    public AdminResponse createAdmin(AdminRequest req) {
        Campus campus = campusRepository.findByNameIgnoreCase(req.getCampus())
                .orElseThrow(() -> new IllegalArgumentException("Unknown campus: " + req.getCampus()));

        String adminDepartment = null;
        if ("DEPARTMENT".equals(req.getElectionType())) {
            if (req.getDepartment() == null || req.getDepartment().isBlank()) {
                throw new IllegalArgumentException("Department is required for Department Election admins.");
            }
            try {
                adminDepartment = AdminDepartment.valueOf(req.getDepartment().toUpperCase()).name();
            } catch (IllegalArgumentException ex) {
                throw new IllegalArgumentException("Unknown department: " + req.getDepartment());
            }
        }

        // ASSUMPTION: SupabaseAdminCreateUserResponse exposes getId() -> String (the auth user's UUID).
        // If the real shape differs (e.g. nested under .getUser()), adjust this one line.
        SupabaseAdminCreateUserResponse authResponse =
                supabaseAuthService.createUser(req.getEmail(), req.getPassword());

        if (authResponse == null || authResponse.getId() == null) {
            throw new IllegalStateException("Failed to create Supabase auth user for " + req.getEmail());
        }

        UUID authUserId = authResponse.getId();

        UserProfile profile = new UserProfile();
        profile.setAuthUserId(authUserId);
        profile.setRole(UserRole.ADMIN);
        profile.setAdminType(req.getElectionType());
        profile.setLastName(req.getLastName());
        profile.setFirstName(req.getFirstName());
        profile.setMiddleName(req.getMiddleName());
        profile.setContactNumber(req.getContact());
        profile.setEmail(req.getEmail());
        profile.setCampusId(campus.getId());
        profile.setAdminDepartment(adminDepartment);
        profile.setActive(true);
        profile.setStatus(RecordStatus.ACTIVE);
        profile.setMustChangePassword(true);

        userProfileRepository.save(profile);

        return toResponse(profile);
    }

    public AdminResponse updateAdmin(UUID id, AdminRequest req) {
        UserProfile profile = userProfileRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Admin not found"));

        Campus campus = campusRepository.findByNameIgnoreCase(req.getCampus())
                .orElseThrow(() -> new IllegalArgumentException("Unknown campus: " + req.getCampus()));

        String adminDepartment = null;
        if ("DEPARTMENT".equals(req.getElectionType())) {
            if (req.getDepartment() == null || req.getDepartment().isBlank()) {
                throw new IllegalArgumentException("Department is required for Department Election admins.");
            }
            try {
                adminDepartment = AdminDepartment.valueOf(req.getDepartment().toUpperCase()).name();
            } catch (IllegalArgumentException ex) {
                throw new IllegalArgumentException("Unknown department: " + req.getDepartment());
            }
        }

        if (req.getEmail() != null && !req.getEmail().equalsIgnoreCase(profile.getEmail())) {
            try {
                supabaseAuthService.updateEmail(profile.getAuthUserId().toString(), req.getEmail());
            } catch (org.springframework.web.client.HttpServerErrorException
                     | org.springframework.web.client.HttpClientErrorException e) {
                throw new IllegalArgumentException(
                        "That email is already in use by another account: " + req.getEmail());
            }
        }

        profile.setLastName(req.getLastName());
        profile.setFirstName(req.getFirstName());
        profile.setMiddleName(req.getMiddleName());
        profile.setContactNumber(req.getContact());
        profile.setEmail(req.getEmail());
        profile.setCampusId(campus.getId());
        profile.setAdminType(req.getElectionType());
        profile.setAdminDepartment(adminDepartment);

        if (req.getPassword() != null && !req.getPassword().isBlank()) {
            supabaseAuthService.updatePassword(profile.getAuthUserId().toString(), req.getPassword());
        }

        userProfileRepository.save(profile);
        return toResponse(profile);
    }

    public void archiveAdmin(UUID id, UUID archivedBy) throws Exception {
        UserProfile profile = userProfileRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Admin not found"));

        if (profile.getStatus() != RecordStatus.ACTIVE) {
            throw new IllegalStateException("Only active admins can be archived.");
        }

        ArchiveRecord record = new ArchiveRecord();
        record.setEntityType("Admins");
        record.setEntityId(profile.getId());
        record.setEntityName(profile.getFirstName() + " " + profile.getLastName());
        record.setData(objectMapper.writeValueAsString(profile));
        record.setArchivedBy(archivedBy);
        record.setArchivedAt(Instant.now());
        record.setRestored(false);
        archiveRecordRepository.save(record);

        if (profile.getAuthUserId() != null) {
            supabaseAuthService.setUserBanStatus(profile.getAuthUserId().toString(), "876000h");
        }

        profile.setStatus(RecordStatus.ARCHIVED);
        profile.setActive(false);
        userProfileRepository.save(profile);
    }

    public void deleteAdmin(UUID id, UUID deletedBy) throws Exception {
        UserProfile profile = userProfileRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Admin not found"));

        if (profile.getStatus() == RecordStatus.DELETED) {
            throw new IllegalStateException("Admin is already deleted.");
        }

        TrashRecord record = new TrashRecord();
        record.setEntityType("Admins");
        record.setEntityId(profile.getId());
        record.setEntityName(profile.getFirstName() + " " + profile.getLastName());
        record.setData(objectMapper.writeValueAsString(profile));
        record.setDeletedBy(deletedBy);
        record.setDeletedAt(Instant.now());
        record.setRestored(false);
        trashRecordRepository.save(record);

        if (profile.getAuthUserId() != null) {
            supabaseAuthService.setUserBanStatus(profile.getAuthUserId().toString(), "876000h");
        }

        profile.setStatus(RecordStatus.DELETED);
        profile.setActive(false);
        userProfileRepository.save(profile);
    }

    public void restoreAdmin(UUID id) {
        UserProfile profile = userProfileRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Admin not found"));

        if (profile.getStatus() != RecordStatus.DELETED
                && profile.getStatus() != RecordStatus.ARCHIVED) {
            throw new IllegalStateException("Admin cannot be restored.");
        }

        if (profile.getAuthUserId() != null) {
            supabaseAuthService.setUserBanStatus(profile.getAuthUserId().toString(), "none");
        }

        profile.setStatus(RecordStatus.ACTIVE);
        profile.setActive(true);
        userProfileRepository.save(profile);

        Instant now = Instant.now();

        trashRecordRepository.findByEntityTypeAndRestoredFalse("Admins").stream()
                .filter(r -> id.equals(r.getEntityId()))
                .forEach(r -> {
                    r.setRestored(true);
                    r.setRestoredAt(now);
                    trashRecordRepository.save(r);
                });

        archiveRecordRepository.findByEntityTypeAndRestoredFalse("Admins").stream()
                .filter(r -> id.equals(r.getEntityId()))
                .forEach(r -> {
                    r.setRestored(true);
                    r.setRestoredAt(now);
                    archiveRecordRepository.save(r);
                });
    }

    public void permanentlyDeleteAdmin(UUID id) {
        UserProfile profile = userProfileRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Admin not found"));

        if (profile.getStatus() != RecordStatus.DELETED) {
            throw new IllegalStateException("Only trashed admins can be permanently deleted.");
        }

        trashRecordRepository.findByEntityTypeAndRestoredFalse("Admins").stream()
                .filter(r -> id.equals(r.getEntityId()))
                .forEach(trashRecordRepository::delete);

        // Free up the email/auth account on Supabase's side too
        if (profile.getAuthUserId() != null) {
            try {
                supabaseAuthService.deleteUser(profile.getAuthUserId().toString());
            } catch (org.springframework.web.client.HttpClientErrorException.NotFound e) {
                // Auth user already gone — fine, continue.
            }
            // Note: any other Supabase error here will propagate and abort the
            // local delete below, which is the safer failure mode — you don't
            // want a local UserProfile deleted while its auth account survives.
        }

        userProfileRepository.delete(profile);
    }

    private AdminResponse toResponse(UserProfile profile) {

        String campusName = campusRepository.findById(profile.getCampusId())
                .map(Campus::getName)
                .orElse("");

        String departmentCode = profile.getAdminDepartment() != null
                ? profile.getAdminDepartment()
                : "";

        return AdminResponse.from(
                profile,
                campusName,
                departmentCode
        );
    }
}