package lccast.voting.system.service.superadmin;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import lccast.voting.system.model.*;
import lccast.voting.system.repository.*;
import jakarta.servlet.http.HttpServletRequest;
import lccast.voting.system.service.AuditLogService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import lccast.voting.system.service.CandidatePortalService;
import lccast.voting.system.service.CandidateRoleSyncService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@Transactional
public class PartylistService {

    private final PartylistRepository partylistRepository;
    private final PartylistMemberRepository partylistMemberRepository;
    private final CampusRepository campusRepository;
    private final ArchiveRecordRepository archiveRecordRepository;
    private final TrashRecordRepository trashRecordRepository;
    private final ObjectMapper objectMapper;
    private final AuditLogService auditLogService;
    private final VoterRepository voterRepository;
    private final CandidateRoleSyncService candidateRoleSyncService;
    private final CandidatePortalService candidatePortalService;   // ADD THIS

    public PartylistService(
            PartylistRepository partylistRepository,
            PartylistMemberRepository partylistMemberRepository,
            CampusRepository campusRepository,
            ArchiveRecordRepository archiveRecordRepository,
            TrashRecordRepository trashRecordRepository,
            ObjectMapper objectMapper,
            AuditLogService auditLogService,
            VoterRepository voterRepository,
            CandidateRoleSyncService candidateRoleSyncService,
            CandidatePortalService candidatePortalService
    ) {
        this.partylistRepository = partylistRepository;
        this.partylistMemberRepository = partylistMemberRepository;
        this.campusRepository = campusRepository;
        this.archiveRecordRepository = archiveRecordRepository;
        this.trashRecordRepository = trashRecordRepository;
        this.objectMapper = objectMapper;
        this.auditLogService = auditLogService;
        this.voterRepository = voterRepository;
        this.candidateRoleSyncService = candidateRoleSyncService;
        this.candidatePortalService = candidatePortalService;
    }

    // =========================================================
    // GET
    // =========================================================

    public List<Partylist> getActivePartylists() {
        return partylistRepository.findByStatus(RecordStatus.ACTIVE);
    }

    public List<Partylist> getArchivedPartylists() {
        return partylistRepository.findByStatus(RecordStatus.ARCHIVED);
    }

    public List<Partylist> getDeletedPartylists() {
        return partylistRepository.findByStatus(RecordStatus.DELETED);
    }

    public List<Partylist> getByCampus(UUID campusId) {
        return partylistRepository.findByCampusIdAndStatus(
                campusId,
                RecordStatus.ACTIVE
        );
    }

    public List<Campus> getCampuses() {
        return campusRepository.findAll();
    }

    public Partylist getById(UUID id) {
        return partylistRepository.findById(id)
                .orElseThrow(() ->
                        new RuntimeException("Partylist not found")
                );
    }

    public List<PartylistMember> getMembers(UUID partylistId) {
        return partylistMemberRepository.findByPartylistId(partylistId);
    }

    // =========================================================
// CREATE
// =========================================================

    public Partylist save(
            Partylist partylist,
            HttpServletRequest request
    ) {

        if (partylist == null) {
            throw new IllegalArgumentException(
                    "Partylist data is required"
            );
        }

        Campus campus =
                resolveCampus(partylist.getCampus());

        partylist.setCampus(campus);

        if (partylist.getStatus() == null) {
            partylist.setStatus(
                    RecordStatus.ACTIVE
            );
        }

        if (partylistRepository.existsByNameIgnoreCaseAndStatus(
                partylist.getName().trim(),
                RecordStatus.ACTIVE
        )) {
            throw new IllegalArgumentException(
                    "A partylist with this name already exists."
            );
        }

        validateMembers(
                null,
                partylist.getCampus().getId(),
                partylist.getSchoolYear(),
                partylist.getMembers()
        );

        Partylist saved =
                partylistRepository.save(partylist);

        saveMembers(
                saved,
                partylist.getMembers()
        );

        auditLogService.log(
                request,
                AuditAction.CREATE,
                "Partylists",
                saved.getId(),
                "Created partylist: " + saved.getName(),
                null
        );

        return saved;
    }

    // =========================================================
    // UPDATE
    // =========================================================

    public Partylist update(
            UUID id,
            Partylist updated,
            HttpServletRequest request
    ) {

        if (updated == null) {
            throw new IllegalArgumentException("Partylist data is required");
        }

        Partylist existing = getById(id);

        if (partylistRepository.existsByNameIgnoreCaseAndStatusAndIdNot(
                updated.getName().trim(), RecordStatus.ACTIVE, id)) {
            throw new IllegalArgumentException("A partylist with the same name already exists.");
        }

        existing.setName(updated.getName());
        existing.setDescription(updated.getDescription());
        existing.setSchoolYear(updated.getSchoolYear());

        if (updated.getPosterImageUrl() != null) {
            existing.setPosterImageUrl(updated.getPosterImageUrl());
        }
        if (updated.getPosterLogoUrl() != null) {
            existing.setPosterLogoUrl(updated.getPosterLogoUrl());
        }
        if (updated.getCampus() != null) {
            existing.setCampus(resolveCampus(updated.getCampus()));
        }

        validateMembers(id, existing.getCampus().getId(), existing.getSchoolYear(), updated.getMembers());

        Partylist saved = partylistRepository.save(existing);

// snapshot who's currently a member, before we wipe the rows
        List<PartylistMember> oldMembers = partylistMemberRepository.findByPartylistId(id);
        List<String> oldStudentIds = oldMembers.stream()
                .map(PartylistMember::getStudentId)
                .toList();
        Map<String, PartylistMember> previousImagesByStudentId = oldMembers.stream()
                .filter(m -> m.getStudentId() != null && !m.getStudentId().isBlank())
                .collect(Collectors.toMap(m -> m.getStudentId().trim(), m -> m, (a, b) -> a));

        partylistMemberRepository.deleteByPartylistId(id);
        partylistMemberRepository.flush();

        saveMembers(saved, updated.getMembers(), previousImagesByStudentId);

        // demote anyone who was a member before but isn't anymore
        Set<String> newStudentIds = (updated.getMembers() == null ? List.<PartylistMember>of() : updated.getMembers())
                .stream()
                .filter(java.util.Objects::nonNull)
                .map(PartylistMember::getStudentId)
                .filter(java.util.Objects::nonNull)
                .map(String::trim)
                .collect(java.util.stream.Collectors.toSet());

        for (String oldId : oldStudentIds) {
            if (!newStudentIds.contains(oldId)) {
                candidateRoleSyncService.demoteToVoterIfNoLongerCandidate(oldId);
            }
        }

        auditLogService.log(
                request, AuditAction.UPDATE, "Partylists", saved.getId(),
                "Updated partylist: " + saved.getName(), null
        );
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

    // =========================================================
    // MEMBERS
    // =========================================================

    private void saveMembers(Partylist partylist, List<PartylistMember> members) {
        saveMembers(partylist, members, java.util.Collections.emptyMap());
    }

    private void saveMembers(
            Partylist partylist,
            List<PartylistMember> members,
            Map<String, PartylistMember> previousImagesByStudentId
    ) {

        if (members == null || members.isEmpty()) {
            return;
        }

        java.util.Set<String> studentIds = new java.util.HashSet<>();

        for (PartylistMember member : members) {

            if (member == null) {
                continue;
            }

            String studentId = member.getStudentId() == null ? "" : member.getStudentId().trim();

            if (studentId.isEmpty()) {
                throw new IllegalArgumentException("Student ID is required.");
            }

            if (!studentIds.add(studentId.toLowerCase())) {
                throw new IllegalArgumentException(
                        "Student " + studentId + " cannot hold multiple positions in the same partylist."
                );
            }

            Voter voter = voterRepository.findByStudentId(studentId)
                    .orElseThrow(() -> new IllegalArgumentException("Student " + studentId + " was not found."));

            if (partylist.getCampus() == null ||
                    partylist.getCampus().getId() == null ||
                    !partylist.getCampus().getId().equals(voter.getCampusId())) {
                throw new IllegalArgumentException("Student " + studentId + " does not belong to the selected campus.");
            }

            boolean alreadyAssigned = partylistMemberRepository
                    .existsByStudentIdAndPartylistSchoolYearAndPartylistStatus(
                            studentId, partylist.getSchoolYear(), RecordStatus.ACTIVE);

            if (alreadyAssigned && partylist.getId() != null) {
                alreadyAssigned = partylistMemberRepository
                        .existsByStudentIdAndPartylistIdNotAndPartylistSchoolYearAndPartylistStatus(
                                studentId, partylist.getId(), partylist.getSchoolYear(), RecordStatus.ACTIVE);
            }

            if (alreadyAssigned) {
                throw new IllegalArgumentException(
                        "Student " + studentId + " is already assigned to another partylist for school year " +
                                partylist.getSchoolYear() + "."
                );
            }

            member.setId(null);
            member.setStudentId(studentId);
            member.setPartylist(partylist);

            // Preserve candidate-uploaded images across superadmin/admin-ssc re-saves.
            PartylistMember previous = previousImagesByStudentId.get(studentId);
            if (previous != null) {
                if (member.getPhotoImageUrl() == null) member.setPhotoImageUrl(previous.getPhotoImageUrl());
                if (member.getBackgroundImageUrl() == null) member.setBackgroundImageUrl(previous.getBackgroundImageUrl());
                if (member.getCampaignImageUrl() == null) member.setCampaignImageUrl(previous.getCampaignImageUrl());
            }

            if (member.getPhotoImageUrl() == null && member.getBackgroundImageUrl() == null
                    && member.getCampaignImageUrl() == null) {
                var existing = candidatePortalService.findExistingImages(studentId);
                if (existing != null) {
                    member.setPhotoImageUrl(existing.photoImageUrl);
                    member.setBackgroundImageUrl(existing.backgroundImageUrl);
                    member.setCampaignImageUrl(existing.campaignImageUrl);
                }
            }

            partylistMemberRepository.save(member);
            candidateRoleSyncService.promoteToCandidate(member.getStudentId());
        }
    }

    public PartylistMember getMember(
            UUID partylistId,
            UUID memberId
    ) {

        PartylistMember member =
                partylistMemberRepository
                        .findById(memberId)
                        .orElseThrow(() ->
                                new RuntimeException(
                                        "Partylist member not found."
                                )
                        );

        if (
                member.getPartylist() == null ||
                        !partylistId.equals(
                                member.getPartylist().getId()
                        )
        ) {
            throw new RuntimeException(
                    "Member does not belong to this partylist."
            );
        }

        return member;
    }

    // =========================================================
    // ARCHIVE
    // =========================================================

    public void archive(
            UUID id,
            HttpServletRequest request
    ) {

        Partylist partylist = getById(id);

        if (partylist.getStatus() != RecordStatus.ACTIVE) {
            throw new RuntimeException(
                    "Only active partylists can be archived"
            );
        }

        createArchiveRecord(partylist, request);



        partylist.setStatus(
                RecordStatus.ARCHIVED
        );

        partylistRepository.save(partylist);

        auditLogService.log(
                request,
                AuditAction.ARCHIVE,
                "Partylists",
                partylist.getId(),
                "Archived partylist: " +
                        partylist.getName(),
                null
        );
    }

    // =========================================================
    // DELETE / TRASH
    // =========================================================

    public void delete(
            UUID id,
            HttpServletRequest request
    ) {

        Partylist partylist = getById(id);

        if (partylist.getStatus() ==
                RecordStatus.DELETED) {

            throw new RuntimeException(
                    "Partylist is already deleted"
            );
        }

        createTrashRecord(partylist, request);

        partylist.setStatus(
                RecordStatus.DELETED
        );

        partylistRepository.save(partylist);

        auditLogService.log(
                request,
                AuditAction.DELETE,
                "Partylists",
                partylist.getId(),
                "Moved partylist to trash: " +
                        partylist.getName(),
                null
        );
    }

    // =========================================================
    // RESTORE
    // =========================================================

    public void restore(
            UUID id,
            HttpServletRequest request
    ) {

        Partylist partylist = getById(id);

        if (partylist.getStatus() != RecordStatus.DELETED &&
                partylist.getStatus() != RecordStatus.ARCHIVED) {

            throw new RuntimeException(
                    "Partylist cannot be restored"
            );
        }

        if (partylistRepository.existsByNameIgnoreCaseAndStatusAndIdNot(
                partylist.getName().trim(),
                RecordStatus.ACTIVE,
                id
        )) {
            throw new IllegalArgumentException(
                    "A partylist with the same name already exists."
            );
        }

        partylist.setStatus(
                RecordStatus.ACTIVE
        );


        validateMembers(
                partylist.getId(),
                partylist.getCampus().getId(),
                partylist.getSchoolYear(),
                partylistMemberRepository.findByPartylistId(partylist.getId())
        );

        partylistRepository.save(partylist);

        restoreArchiveRecord(id);
        restoreTrashRecord(id);

        auditLogService.log(
                request,
                AuditAction.RESTORE,
                "Partylists",
                partylist.getId(),
                "Restored partylist: " +
                        partylist.getName(),
                null
        );
    }

    // =========================================================
// PERMANENT DELETE
// =========================================================

    public void permanentlyDelete(UUID id) {
        Partylist partylist = getById(id);
        if (partylist.getStatus() != RecordStatus.DELETED) {
            throw new RuntimeException("Only trashed partylists can be permanently deleted.");
        }

        List<String> studentIds = partylistMemberRepository.findByPartylistId(id).stream()
                .map(PartylistMember::getStudentId)
                .toList();

        partylistMemberRepository.deleteByPartylistId(id);
        partylistMemberRepository.flush();

        trashRecordRepository.findByEntityTypeAndRestoredFalse("Partylists").stream()
                .filter(record -> id.equals(record.getEntityId()))
                .forEach(trashRecordRepository::delete);

        partylistRepository.delete(partylist);

        studentIds.forEach(candidateRoleSyncService::demoteToVoterIfNoLongerCandidate);
    }

    // =========================================================
    // ARCHIVE RECORD
    // =========================================================

    private void createArchiveRecord(Partylist partylist, HttpServletRequest request) {

        ArchiveRecord record = new ArchiveRecord();

        record.setEntityType("Partylists");
        record.setEntityId(partylist.getId());
        record.setEntityName(partylist.getName());
        record.setData(toJson(partylist));
        record.setArchivedBy(getCurrentUserId(request));
        record.setArchivedAt(Instant.now());
        record.setRestored(false);

        archiveRecordRepository.save(record);
    }

    // =========================================================
    // TRASH RECORD
    // =========================================================

    private void createTrashRecord(Partylist partylist, HttpServletRequest request) {

        TrashRecord record = new TrashRecord();

        record.setEntityType("Partylists");
        record.setEntityId(partylist.getId());
        record.setEntityName(partylist.getName());
        record.setData(toJson(partylist));
        record.setDeletedBy(getCurrentUserId(request));
        record.setDeletedAt(Instant.now());
        record.setRestored(false);

        trashRecordRepository.save(record);
    }

    // =========================================================
    // RESTORE ARCHIVE RECORD
    // =========================================================

    private void restoreArchiveRecord(UUID partylistId) {

        List<ArchiveRecord> records =
                archiveRecordRepository
                        .findByEntityTypeAndRestoredFalse("Partylists");

        records.stream()
                .filter(record ->
                        partylistId.equals(record.getEntityId())
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

    private void restoreTrashRecord(UUID partylistId) {

        List<TrashRecord> records =
                trashRecordRepository
                        .findByEntityTypeAndRestoredFalse("Partylists");

        records.stream()
                .filter(record ->
                        partylistId.equals(record.getEntityId())
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

    private String toJson(Partylist partylist) {

        try {
            var data = objectMapper.createObjectNode();

            // =========================================================
            // PARTYLIST
            // =========================================================

            if (partylist.getId() != null) {
                data.put(
                        "id",
                        partylist.getId().toString()
                );
            }

            data.put(
                    "name",
                    partylist.getName()
            );

            data.put(
                    "description",
                    partylist.getDescription()
            );

            data.put(
                    "schoolYear",
                    partylist.getSchoolYear()
            );

            if (partylist.getStatus() != null) {
                data.put(
                        "status",
                        partylist.getStatus().name()
                );
            }

            if (partylist.getCreatedBy() != null) {
                data.put(
                        "createdBy",
                        partylist.getCreatedBy().toString()
                );
            }

            data.put(
                    "posterImageUrl",
                    partylist.getPosterImageUrl()
            );

            data.put(
                    "posterLogoUrl",
                    partylist.getPosterLogoUrl()
            );

            // =========================================================
            // CAMPUS
            // =========================================================

            Campus campus = partylist.getCampus();

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

            if (partylist.getMembers() != null) {

                for (
                        PartylistMember member :
                        partylist.getMembers()
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

                    memberData.put(
                            "position",
                            member.getPosition()
                    );

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

    public void validateMembers(
            UUID partylistId,
            UUID campusId,
            String schoolYear,
            List<PartylistMember> members
    ){

        if (members == null || members.isEmpty()) {
            return;
        }

        java.util.Set<String> studentIds =
                new java.util.HashSet<>();

        for (PartylistMember member : members) {

            if (member == null) {
                continue;
            }

            String studentId =
                    member.getStudentId() == null
                            ? ""
                            : member.getStudentId().trim();

            if (studentId.isEmpty()) {
                continue;
            }

            Voter voter = voterRepository.findByStudentId(studentId)
                    .orElseThrow(() ->
                            new IllegalArgumentException(
                                    "Student " + studentId + " was not found."
                            )
                    );

            if (campusId == null || !campusId.equals(voter.getCampusId())) {
                throw new IllegalArgumentException(
                        "Student " + studentId +
                                " does not belong to the selected campus."
                );
            }

            // SAME PARTYLIST DUPLICATE
            if (!studentIds.add(studentId.toLowerCase())) {

                throw new IllegalArgumentException(
                        "Student " + studentId +
                                " cannot hold multiple positions in the same partylist."
                );
            }

            // ANOTHER PARTYLIST / SAME SCHOOL YEAR
            boolean alreadyAssigned =
                    partylistMemberRepository.existsByStudentIdAndPartylistSchoolYearAndPartylistStatus(
                            studentId,
                            schoolYear,
                            RecordStatus.ACTIVE);

            if (alreadyAssigned && partylistId != null) {
                alreadyAssigned =
                        partylistMemberRepository
                                .existsByStudentIdAndPartylistIdNotAndPartylistSchoolYearAndPartylistStatus(
                                        studentId,
                                        partylistId,
                                        schoolYear,
                                        RecordStatus.ACTIVE);
            }

            if (alreadyAssigned) {

                throw new IllegalArgumentException(
                        "Student " + studentId +
                                " is already assigned to another partylist for school year " +
                                schoolYear + "."
                );
            }
        }
    }

    private UUID getCurrentUserId(HttpServletRequest request) {

        if (request == null) {
            return null;
        }

        jakarta.servlet.http.HttpSession session = request.getSession(false);

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
}