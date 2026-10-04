package lccast.voting.system.service.superadmin;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import jakarta.servlet.http.HttpSession;
import lccast.voting.system.model.*;
import lccast.voting.system.repository.*;
import jakarta.servlet.http.HttpServletRequest;
import lccast.voting.system.service.AuditLogService;

import lccast.voting.system.service.CandidatePortalService;
import lccast.voting.system.service.CandidateRoleSyncService;
import lccast.voting.system.service.RealtimeBroadcastService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;


@Service
@Transactional
public class DepartmentService {

    private final DepartmentRepository departmentRepository;
    private final DepartmentMemberRepository departmentMemberRepository;
    private final CampusRepository campusRepository;
    private final VoterRepository voterRepository;
    private final ArchiveRecordRepository archiveRecordRepository;
    private final TrashRecordRepository trashRecordRepository;
    private final ObjectMapper objectMapper;
    private final AuditLogService auditLogService;
    private final CandidateRoleSyncService candidateRoleSyncService;
    private final lccast.voting.system.service.CandidatePortalService candidatePortalService;
    private final ElectionDepartmentRepository electionDepartmentRepository;
    private final RealtimeBroadcastService realtime;

    public DepartmentService(
            DepartmentRepository departmentRepository,
            DepartmentMemberRepository departmentMemberRepository,
            CampusRepository campusRepository,
            VoterRepository voterRepository,
            ArchiveRecordRepository archiveRecordRepository,
            TrashRecordRepository trashRecordRepository,
            ObjectMapper objectMapper,
            AuditLogService auditLogService,
            CandidateRoleSyncService candidateRoleSyncService,
            CandidatePortalService candidatePortalService,
            ElectionDepartmentRepository electionDepartmentRepository,
            RealtimeBroadcastService realtime
    ) {
        this.departmentRepository = departmentRepository;
        this.departmentMemberRepository = departmentMemberRepository;
        this.campusRepository = campusRepository;
        this.voterRepository = voterRepository;
        this.archiveRecordRepository = archiveRecordRepository;
        this.trashRecordRepository = trashRecordRepository;
        this.objectMapper = objectMapper;
        this.auditLogService = auditLogService;
        this.candidateRoleSyncService = candidateRoleSyncService;
        this.candidatePortalService = candidatePortalService;
        this.electionDepartmentRepository = electionDepartmentRepository;
        this.realtime = realtime;
    }

    // =========================================================
    // GET
    // =========================================================

    public List<Department> getActiveDepartments() {
        return departmentRepository.findByStatus(RecordStatus.ACTIVE);
    }

    public List<Department> getArchivedDepartments() {
        return departmentRepository.findByStatus(RecordStatus.ARCHIVED);
    }

    public List<Department> getDeletedDepartments() {
        return departmentRepository.findByStatus(RecordStatus.DELETED);
    }

    public List<Department> getByCampus(UUID campusId) {
        return departmentRepository.findByCampusIdAndStatus(
                campusId,
                RecordStatus.ACTIVE
        );
    }

    public List<Campus> getCampuses() {
        return campusRepository.findAll();
    }

    public Voter getStudentForDepartmentMember(
            String studentId,
            UUID departmentId
    ) {
        Department department = getById(departmentId);

        if (department.getCampus() == null ||
                department.getCampus().getId() == null) {

            throw new IllegalArgumentException(
                    "Department campus is required."
            );
        }

        String departmentName = department.getName();

        if (departmentName == null || departmentName.isBlank()) {
            throw new IllegalArgumentException(
                    "Department name is required."
            );
        }

        Voter voter =
                voterRepository
                        .findByStudentIdAndCampusIdAndProgramCourse(
                                studentId,
                                department.getCampus().getId(),
                                departmentName
                        )
                        .orElseThrow(() ->
                                new IllegalArgumentException(
                                        "Student does not belong to the selected campus and department."
                                )
                        );

        return voter;
    }

    public Voter getStudentForDepartmentMemberByCampus(
            String studentId,
            UUID campusId,
            UUID departmentId
    ) {
        if (campusId == null) {
            throw new IllegalArgumentException(
                    "Campus is required."
            );
        }

        if (departmentId == null) {
            throw new IllegalArgumentException(
                    "Department is required."
            );
        }

        Department department = getById(departmentId);

        if (department.getCampus() == null ||
                !campusId.equals(department.getCampus().getId())) {

            throw new IllegalArgumentException(
                    "Department does not belong to the selected campus."
            );
        }

        String departmentName = department.getName();

        if (departmentName == null || departmentName.isBlank()) {
            throw new IllegalArgumentException(
                    "Department name is required."
            );
        }

        return voterRepository
                .findByStudentIdAndCampusIdAndProgramCourse(
                        studentId,
                        campusId,
                        departmentName
                )
                .orElseThrow(() ->
                        new IllegalArgumentException(
                                "Student does not belong to the selected campus and department."
                        )
                );
    }

    private void assertNotLinkedToActiveElection(UUID departmentId) {
        Instant now = Instant.now();

        boolean linkedToActiveElection = electionDepartmentRepository.findByDepartmentId(departmentId).stream()
                .map(ElectionDepartment::getElection)
                .filter(Objects::nonNull)
                .filter(election -> election.getStatus() == RecordStatus.ACTIVE)
                .anyMatch(election -> election.getEndAt() == null || !now.isAfter(election.getEndAt()));

        if (linkedToActiveElection) {
            throw new IllegalStateException(
                    "This department is part of an active or upcoming election and cannot be archived or deleted. " +
                            "Archive or delete that election first, or wait until it concludes."
            );
        }
    }


    public Voter getStudentForDepartmentMemberByCampusAndName(
            String studentId,
            UUID campusId,
            String departmentName
    ) {
        if (campusId == null) {
            throw new IllegalArgumentException("Campus is required.");
        }

        if (departmentName == null || departmentName.isBlank()) {
            throw new IllegalArgumentException("Department is required.");
        }

        return voterRepository
                .findByStudentIdAndCampusIdAndProgramCourse(
                        studentId,
                        campusId,
                        departmentName
                )
                .orElseThrow(() ->
                        new IllegalArgumentException(
                                "Student does not belong to the selected campus and department."
                        )
                );
    }

    public Department getById(UUID id) {
        return departmentRepository.findById(id)
                .orElseThrow(() ->
                        new RuntimeException("Department not found")
                );
    }

    public List<DepartmentMember> getMembers(UUID departmentId) {
        return departmentMemberRepository.findByDepartmentId(departmentId);
    }


    public Department save(
            Department department,
            HttpServletRequest request
    ) {

        if (department == null) {
            throw new IllegalArgumentException(
                    "Department data is required"
            );
        }

        Campus campus =
                resolveCampus(department.getCampus());

        department.setCampus(campus);

        String name = department.getName();

        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException(
                    "Department name is required."
            );
        }

        name = name.trim();
        department.setName(name);

        String title = department.getTitle();

        if (title == null || title.isBlank()) {
            throw new IllegalArgumentException(
                    "Department title is required."
            );
        }

        title = title.trim();

        String prefix = name + " - ";

        if (!title.startsWith(prefix)) {
            title = prefix + title;
        }

        if (departmentRepository.existsByTitleIgnoreCase(title)) {
            throw new IllegalArgumentException(
                    "A department with the title \"" +
                            title +
                            "\" already exists."
            );
        }

        department.setTitle(title);

        if (department.getStatus() == null)
            department.setStatus(RecordStatus.ACTIVE);

        if (department.getVotingType() == null)
            throw new IllegalArgumentException("Voting type is required.");

        if (department.getVotingType() == VotingType.REPRESENTATIVE &&
                (department.getPositions() == null || department.getPositions().isEmpty())) {
            throw new IllegalArgumentException("At least one department position is required.");
        }

        Set<String> positions = new HashSet<>();

        if (department.getPositions() != null) {
            for (String position : department.getPositions()) {
                if (position == null || position.isBlank())
                    throw new IllegalArgumentException("Department positions cannot be blank.");

                if (!positions.add(position.trim()))
                    throw new IllegalArgumentException(
                            "A department position cannot be added more than once."
                    );
            }
        }

        if (department.getVotingType() == VotingType.REPRESENTATIVE) {
            int positionCount = department.getPositions().size();

            int memberCount = department.getMembers() == null ? 0 :
                    (int) department.getMembers().stream()
                            .filter(Objects::nonNull)
                            .filter(member -> member.getStudentId() != null && !member.getStudentId().isBlank())
                            .count();

            if (memberCount < positionCount)
                throw new IllegalArgumentException(
                        "The department must have at least " + positionCount +
                                " member(s) for the selected positions."
                );
        }


        Department saved =
                departmentRepository.save(department);



        saveMembers(
                saved,
                department.getMembers()
        );

        auditLogService.log(
                request,
                AuditAction.CREATE,
                "Departments",
                saved.getId(),
                "Created department: " + saved.getTitle(),
                null
        );

        realtime.departmentsChanged(saved.getCampus().getId());

        return saved;
    }

    // =========================================================
    // UPDATE
    // =========================================================

    public Department update(
            UUID id,
            Department updated,
            HttpServletRequest request
    ) {

        if (updated == null) {
            throw new IllegalArgumentException("Department data is required");
        }

        Department existing = getById(id);

        Campus campus = resolveCampus(updated.getCampus());
        existing.setCampus(campus);

        existing.setName(updated.getName());

        String name = updated.getName();
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Department name is required.");
        }
        name = name.trim();
        existing.setName(name);

        String title = updated.getTitle();
        if (title == null || title.isBlank()) {
            throw new IllegalArgumentException("Department title is required.");
        }
        title = title.trim();

        String prefix = name + " - ";
        if (!title.startsWith(prefix)) {
            title = prefix + title;
        }

        if (departmentRepository.existsByTitleIgnoreCaseAndIdNot(title, id)) {
            throw new IllegalArgumentException(
                    "A department with the title \"" + title + "\" already exists."
            );
        }

        existing.setTitle(title);
        existing.setDescription(updated.getDescription());
        existing.setSchoolYear(updated.getSchoolYear());
        existing.setPositions(updated.getPositions());

        existing.setPosterImageUrl(updated.getPosterImageUrl());
        existing.setPosterLogoUrl(updated.getPosterLogoUrl());

        if (updated.getVotingType() == null) {
            throw new IllegalArgumentException("Voting type is required.");
        }
        existing.setVotingType(updated.getVotingType());

        if (updated.getVotingType() == VotingType.REPRESENTATIVE) {
            if (updated.getPositions() == null || updated.getPositions().isEmpty()) {
                throw new IllegalArgumentException("At least one department position is required.");
            }
            int positionCount = updated.getPositions().size();
            int memberCount = updated.getMembers() == null ? 0 :
                    (int) updated.getMembers().stream()
                            .filter(Objects::nonNull)
                            .filter(member -> member.getStudentId() != null && !member.getStudentId().isBlank())
                            .count();
            if (memberCount < positionCount) {
                throw new IllegalArgumentException(
                        "The department must have at least " + positionCount + " member(s) for the selected positions."
                );
            }
        }

        Department saved = departmentRepository.save(existing);

// snapshot who's currently a member, before we wipe the rows
        List<DepartmentMember> oldMembers = departmentMemberRepository.findByDepartmentId(id);
        List<String> oldStudentIds = oldMembers.stream()
                .map(DepartmentMember::getStudentId)
                .toList();
        Map<String, DepartmentMember> previousImagesByStudentId = oldMembers.stream()
                .filter(m -> m.getStudentId() != null && !m.getStudentId().isBlank())
                .collect(Collectors.toMap(m -> m.getStudentId().trim(), m -> m, (a, b) -> a));

        List<DepartmentMember> updatedMembers = updated.getMembers();

        java.util.Set<String> studentIds = new java.util.HashSet<>();

        if (updatedMembers != null) {
            for (DepartmentMember member : updatedMembers) {
                if (member == null || member.getStudentId() == null || member.getStudentId().isBlank()) {
                    continue;
                }
                String studentId = member.getStudentId().trim();
                if (!studentIds.add(studentId)) {
                    throw new IllegalArgumentException(
                            "A student cannot be added more than once to the same department."
                    );
                }
            }
        }

        if (updatedMembers != null) {
            for (DepartmentMember member : updatedMembers) {
                if (member == null || member.getStudentId() == null || member.getStudentId().isBlank()) {
                    continue;
                }

                if (departmentMemberRepository
                        .existsByStudentIdAndDepartmentSchoolYearAndDepartmentStatus(
                                member.getStudentId(), saved.getSchoolYear(), RecordStatus.ACTIVE)) {

                    List<DepartmentMember> existingMembers =
                            departmentMemberRepository
                                    .findByStudentIdAndDepartmentSchoolYearAndDepartmentStatus(
                                            member.getStudentId(), saved.getSchoolYear(), RecordStatus.ACTIVE);

                    boolean belongsToSameDepartment = existingMembers.stream()
                            .anyMatch(existingMember ->
                                    existingMember.getDepartment() != null &&
                                            id.equals(existingMember.getDepartment().getId()));

                    if (!belongsToSameDepartment) {
                        throw new IllegalArgumentException(
                                "This student is already a member of another department for the selected school year."
                        );
                    }
                }
            }
        }

        departmentMemberRepository.deleteByDepartmentId(id);
        departmentMemberRepository.flush();

        saveMembers(saved, updatedMembers, previousImagesByStudentId);

        // demote anyone who was a member before but isn't anymore
        Set<String> newStudentIds = (updatedMembers == null ? List.<DepartmentMember>of() : updatedMembers)
                .stream()
                .filter(Objects::nonNull)
                .map(DepartmentMember::getStudentId)
                .filter(Objects::nonNull)
                .map(String::trim)
                .collect(java.util.stream.Collectors.toSet());

        for (String oldId : oldStudentIds) {
            if (!newStudentIds.contains(oldId)) {
                candidateRoleSyncService.demoteDepartmentMember(oldId);
            }
        }

        auditLogService.log(
                request, AuditAction.UPDATE, "Departments", saved.getId(),
                "Updated department: " + saved.getTitle(), null
        );

        realtime.departmentsChanged(saved.getCampus().getId());

        return saved;
    }

    // =========================================================
    // CAMPUS
    // =========================================================

    private Campus resolveCampus(Campus campus) {

        if (campus == null || campus.getId() == null) {
            throw new IllegalArgumentException("Campus is required");
        }

        UUID campusId = campus.getId();

        return campusRepository.findById(campusId)
                .orElseThrow(() ->
                        new RuntimeException(
                                "Selected campus was not found."
                        )
                );
    }

    public List<DepartmentMember> getStudentDepartmentMembersBySchoolYear(
            String studentId,
            String schoolYear,
            UUID excludeDepartmentId
    ) {
        if (studentId == null || studentId.isBlank()) {
            throw new IllegalArgumentException(
                    "Student ID is required."
            );
        }

        if (schoolYear == null || schoolYear.isBlank()) {
            throw new IllegalArgumentException(
                    "School year is required."
            );
        }

        List<DepartmentMember> members =
                departmentMemberRepository
                        .findByStudentIdAndDepartmentSchoolYearAndDepartmentStatus(
                                studentId.trim(),
                                schoolYear.trim(),
                                RecordStatus.ACTIVE
                        );

        if (excludeDepartmentId == null) {
            return members;
        }

        return members.stream()
                .filter(member ->
                        member.getDepartment() == null ||
                                !excludeDepartmentId.equals(
                                        member.getDepartment().getId()
                                )
                )
                .toList();
    }



    // =========================================================
    // MEMBERS
    // =========================================================

    private void saveMembers(Department department, List<DepartmentMember> members) {
        saveMembers(department, members, java.util.Collections.emptyMap());
    }

    private void saveMembers(
            Department department,
            List<DepartmentMember> members,
            Map<String, DepartmentMember> previousImagesByStudentId
    ) {

        if (members == null || members.isEmpty()) {
            return;
        }

        if (department.getCampus() == null || department.getCampus().getId() == null) {
            throw new IllegalArgumentException("Department campus is required.");
        }

        java.util.Set<String> studentIds = new java.util.HashSet<>();

        for (DepartmentMember member : members) {

            if (member == null) {
                continue;
            }

            if (member.getStudentId() == null || member.getStudentId().isBlank()) {
                throw new IllegalArgumentException("Student ID is required.");
            }

            String studentId = member.getStudentId().trim();

            if (!studentIds.add(studentId)) {
                throw new IllegalArgumentException("A student cannot be added more than once to the same department.");
            }

            if (department.getVotingType() == VotingType.PARTYLIST) {
                if (member.getPosition() == null || member.getPosition().isBlank())
                    throw new IllegalArgumentException("Each partylist member must have a position.");
                member.setPosition(member.getPosition().trim());
            }

            member.setStudentId(studentId);

            Voter voter = getStudentForDepartmentMember(member.getStudentId(), department.getId());

            List<DepartmentMember> existingMembers = departmentMemberRepository
                    .findByStudentIdAndDepartmentSchoolYearAndDepartmentStatus(
                            member.getStudentId(), department.getSchoolYear(), RecordStatus.ACTIVE);

            if (!existingMembers.isEmpty()) {
                boolean belongsToSameDepartment = existingMembers.stream()
                        .anyMatch(existingMember ->
                                existingMember.getDepartment() != null &&
                                        department.getId().equals(existingMember.getDepartment().getId()));

                if (belongsToSameDepartment) {
                    throw new IllegalArgumentException(
                            "This student is already a member of this department for the selected school year.");
                }

                throw new IllegalArgumentException(
                        "This student is already a member of another department for the selected school year.");
            }

            member.setId(null);
            member.setStudentId(voter.getStudentId());
            member.setDepartment(department);

            // Preserve candidate-uploaded images across superadmin/admin-ssc re-saves.
            DepartmentMember previous = previousImagesByStudentId.get(voter.getStudentId());
            if (previous != null) {
                if (member.getPhotoImageUrl() == null) member.setPhotoImageUrl(previous.getPhotoImageUrl());
                if (member.getBackgroundImageUrl() == null) member.setBackgroundImageUrl(previous.getBackgroundImageUrl());
                if (member.getCampaignImageUrl() == null) member.setCampaignImageUrl(previous.getCampaignImageUrl());
            }

            if (member.getPhotoImageUrl() == null && member.getBackgroundImageUrl() == null
                    && member.getCampaignImageUrl() == null) {
                var existing = candidatePortalService.findExistingImages(voter.getStudentId(), CandidatePortalService.CandidateType.DEPARTMENT);
                if (existing != null) {
                    member.setPhotoImageUrl(existing.photoImageUrl);
                    member.setBackgroundImageUrl(existing.backgroundImageUrl);
                    member.setCampaignImageUrl(existing.campaignImageUrl);
                }
            }

            departmentMemberRepository.save(member);
            candidateRoleSyncService.promoteToCandidate(voter.getStudentId());
        }
    }

    public DepartmentMember getMember(
            UUID departmentId,
            UUID memberId
    ) {

        DepartmentMember member =
                departmentMemberRepository
                        .findById(memberId)
                        .orElseThrow(() ->
                                new RuntimeException(
                                        "Department member not found."
                                )
                        );

        if (
                member.getDepartment() == null ||
                        !departmentId.equals(
                                member.getDepartment().getId()
                        )
        ) {
            throw new RuntimeException(
                    "Member does not belong to this department."
            );
        }

        return member;
    }

    private UUID getCurrentUserId(HttpServletRequest request) {

        if (request == null) {
            return null;
        }

        HttpSession session = request.getSession(false);

        if (session == null) {
            return null;
        }

        Object userId = session.getAttribute("userId");

        if (userId == null) {
            return null;
        }

        try {
            return UUID.fromString(userId.toString());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    // =========================================================
    // ARCHIVE
    // =========================================================

    public void archive(UUID id, HttpServletRequest request) {
        Department department = getById(id);
        if (department.getStatus() != RecordStatus.ACTIVE) {
            throw new RuntimeException("Only active departments can be archived");
        }

        assertNotLinkedToActiveElection(id); // ADD

        createArchiveRecord(department, request);

        List<String> memberStudentIds = departmentMemberRepository.findByDepartmentId(id).stream()
                .map(DepartmentMember::getStudentId)
                .toList();

        department.setStatus(RecordStatus.ARCHIVED);
        departmentRepository.save(department);

        candidateRoleSyncService.demoteDepartmentMembers(memberStudentIds);

        auditLogService.log(request, AuditAction.ARCHIVE, "Departments", department.getId(),
                "Archived department: " + department.getTitle(), null);

        realtime.departmentsChanged(department.getCampus().getId());
        realtime.historyChanged("archives");
    }

    public void delete(UUID id, HttpServletRequest request) {
        Department department = getById(id);
        if (department.getStatus() == RecordStatus.DELETED) {
            throw new RuntimeException("Department is already deleted");
        }
        assertNotLinkedToActiveElection(id);

        createTrashRecord(department, request);

        List<String> memberStudentIds = departmentMemberRepository.findByDepartmentId(id).stream()
                .map(DepartmentMember::getStudentId)
                .toList();

        department.setStatus(RecordStatus.DELETED);
        departmentRepository.save(department);

        candidateRoleSyncService.demoteDepartmentMembers(memberStudentIds);

        auditLogService.log(request, AuditAction.DELETE, "Departments", department.getId(),
                "Moved department to trash: " + department.getTitle(), null);

        realtime.departmentsChanged(department.getCampus().getId());
        realtime.historyChanged("trash");
    }

    public void restore(UUID id, HttpServletRequest request) {
        Department department = getById(id);
        assertMembersRestorable(department);

        if (department.getStatus() != RecordStatus.DELETED && department.getStatus() != RecordStatus.ARCHIVED) {
            throw new RuntimeException("Department cannot be restored");
        }

        department.setStatus(RecordStatus.ACTIVE);
        departmentRepository.save(department);

        List<String> memberStudentIds = departmentMemberRepository.findByDepartmentId(id).stream()
                .map(DepartmentMember::getStudentId)
                .toList();
        candidateRoleSyncService.promoteAll(memberStudentIds);

        restoreArchiveRecord(id);
        restoreTrashRecord(id);

        auditLogService.log(request, AuditAction.RESTORE, "Departments", department.getId(),
                "Restored department: " + department.getTitle(), null);

        realtime.departmentsChanged(department.getCampus().getId());
    }

    // =========================================================
// PERMANENT DELETE
// =========================================================

    public void permanentlyDelete(UUID id) {
        Department department = getById(id);
        if (department.getStatus() != RecordStatus.DELETED) {
            throw new RuntimeException("Only trashed departments can be permanently deleted.");
        }

        List<String> studentIds = departmentMemberRepository.findByDepartmentId(id).stream()
                .map(DepartmentMember::getStudentId)
                .toList();

        departmentMemberRepository.deleteByDepartmentId(id);
        departmentMemberRepository.flush();

        trashRecordRepository.findByEntityTypeAndRestoredFalse("Departments").stream()
                .filter(record -> id.equals(record.getEntityId()))
                .forEach(trashRecordRepository::delete);

        departmentRepository.delete(department);

        candidateRoleSyncService.demoteDepartmentMembers(studentIds);
    }

    // =========================================================
    // ARCHIVE RECORD
    // =========================================================

    private void createArchiveRecord(
            Department department,
            HttpServletRequest request
    ) {
        ArchiveRecord record = new ArchiveRecord();

        record.setEntityType("Departments");
        record.setEntityId(department.getId());
        record.setEntityName(department.getTitle());
        record.setData(toJson(department));
        record.setArchivedBy(getCurrentUserId(request));
        record.setArchivedAt(Instant.now());
        record.setRestored(false);

        archiveRecordRepository.save(record);
    }

    // =========================================================
    // TRASH RECORD
    // =========================================================

    private void createTrashRecord(
            Department department,
            HttpServletRequest request
    ) {
        TrashRecord record = new TrashRecord();

        record.setEntityType("Departments");
        record.setEntityId(department.getId());
        record.setEntityName(department.getTitle());
        record.setData(toJson(department));
        record.setDeletedBy(getCurrentUserId(request));
        record.setDeletedAt(Instant.now());
        record.setRestored(false);

        trashRecordRepository.save(record);
    }

    // =========================================================
    // RESTORE ARCHIVE RECORD
    // =========================================================

    private void restoreArchiveRecord(UUID departmentId) {

        List<ArchiveRecord> records =
                archiveRecordRepository
                        .findByEntityTypeAndRestoredFalse("Departments");

        records.stream()
                .filter(record ->
                        departmentId.equals(record.getEntityId())
                )
                .forEach(record -> {

                    record.setRestored(true);
                    record.setRestoredAt(Instant.now());

                    archiveRecordRepository.save(record);
                });
    }

    // =========================================================
    // RESTORE TRASH RECORD
    // =========================================================

    private void restoreTrashRecord(UUID departmentId) {

        List<TrashRecord> records =
                trashRecordRepository
                        .findByEntityTypeAndRestoredFalse("Departments");

        records.stream()
                .filter(record ->
                        departmentId.equals(record.getEntityId())
                )
                .forEach(record -> {

                    record.setRestored(true);
                    record.setRestoredAt(Instant.now());

                    trashRecordRepository.save(record);
                });
    }

    // =========================================================
    // JSON
    // =========================================================

    private String toJson(Department department) {

        try {
            var data = objectMapper.createObjectNode();

            // =========================================================
            // DEPARTMENT
            // =========================================================

            if (department.getId() != null) {
                data.put(
                        "id",
                        department.getId().toString()
                );
            }

            data.put(
                    "name",
                    department.getName()
            );

            data.put(
                    "title",
                    department.getTitle()
            );

            data.put(
                    "description",
                    department.getDescription()
            );

            data.put(
                    "schoolYear",
                    department.getSchoolYear()
            );

            if (department.getVotingType() != null) {
                data.put(
                        "votingType",
                        department.getVotingType().name()
                );
            }

            if (department.getStatus() != null) {
                data.put(
                        "status",
                        department.getStatus().name()
                );
            }

            if (department.getCreatedBy() != null) {
                data.put(
                        "createdBy",
                        department.getCreatedBy().toString()
                );
            }

            data.put(
                    "posterImageUrl",
                    department.getPosterImageUrl()
            );

            data.put(
                    "posterLogoUrl",
                    department.getPosterLogoUrl()
            );

            // =========================================================
            // CAMPUS
            // =========================================================

            Campus campus = department.getCampus();

            if (campus != null) {

                var campusData =
                        objectMapper.createObjectNode();

                if (campus.getId() != null) {
                    campusData.put(
                            "id",
                            campus.getId().toString()
                    );
                }

                campusData.put(
                        "name",
                        campus.getName()
                );

                campusData.put(
                        "description",
                        campus.getDescription()
                );

                if (campus.getStatus() != null) {
                    campusData.put(
                            "status",
                            campus.getStatus().name()
                    );
                }

                data.set(
                        "campus",
                        campusData
                );
            }

            // =========================================================
            // MEMBERS
            // =========================================================

            var members =
                    objectMapper.createArrayNode();

            if (department.getMembers() != null) {

                for (
                        DepartmentMember member :
                        department.getMembers()
                ) {

                    if (member == null) {
                        continue;
                    }

                    var memberData =
                            objectMapper.createObjectNode();

                    if (member.getId() != null) {
                        memberData.put(
                                "id",
                                member.getId().toString()
                        );
                    }

                    memberData.put(
                            "studentId",
                            member.getStudentId()
                    );

                    memberData.put(
                            "lastName",
                            member.getLastName()
                    );

                    memberData.put(
                            "firstName",
                            member.getFirstName()
                    );

                    memberData.put(
                            "middleName",
                            member.getMiddleName()
                    );

                    memberData.put("position", member.getPosition());


                    memberData.put(
                            "campaignImageUrl",
                            member.getCampaignImageUrl()
                    );

                    memberData.put(
                            "backgroundImageUrl",
                            member.getBackgroundImageUrl()
                    );

                    memberData.put(
                            "photoImageUrl",
                            member.getPhotoImageUrl()
                    );

                    members.add(memberData);
                }
            }

            data.set(
                    "members",
                    members
            );

            // =========================================================
            // RETURN JSON
            // =========================================================

            return objectMapper.writeValueAsString(data);

        } catch (JsonProcessingException e) {

            throw new RuntimeException(
                    "Failed to create archive/trash record",
                    e
            );
        }
    }

    // DepartmentService.java — new public method
    public void assertMembersRestorable(Department department) {
        List<DepartmentMember> members =
                departmentMemberRepository.findByDepartmentId(department.getId());

        for (DepartmentMember member : members) {
            if (member == null || member.getStudentId() == null || member.getStudentId().isBlank()) {
                continue;
            }

            List<DepartmentMember> activeMembers =
                    departmentMemberRepository
                            .findByStudentIdAndDepartmentSchoolYearAndDepartmentStatus(
                                    member.getStudentId(),
                                    department.getSchoolYear(),
                                    RecordStatus.ACTIVE
                            );

            boolean conflict = activeMembers.stream()
                    .anyMatch(activeMember ->
                            activeMember.getDepartment() != null &&
                                    !department.getId().equals(activeMember.getDepartment().getId())
                    );

            if (conflict) {
                throw new IllegalArgumentException(
                        "This department cannot be restored because student " +
                                member.getStudentId() +
                                " is already a member of an active department for the selected school year."
                );
            }
        }
    }
}