package lccast.voting.system.service.superadmin;

import lccast.voting.system.dto.superadmin.ElectionResponse;
import lccast.voting.system.model.*;
import lccast.voting.system.repository.*;

import lccast.voting.system.service.AnalyticsService;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import jakarta.servlet.http.HttpServletRequest;
import lccast.voting.system.service.AuditLogService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@Transactional
public class ElectionService {

    private final ElectionRepository electionRepository;
    private final ElectionPartylistRepository electionPartylistRepository;
    private final ElectionDepartmentRepository electionDepartmentRepository;
    private final PartylistRepository partylistRepository;
    private final DepartmentRepository departmentRepository;
    private final CampusRepository campusRepository;
    private final AuditLogService auditLogService;
    private final ArchiveRecordRepository archiveRecordRepository;
    private final TrashRecordRepository trashRecordRepository;
    private final ObjectMapper objectMapper;
    private final PartylistMemberRepository partylistMemberRepository;
    private final CandidateRepository candidateRepository;
    private final DepartmentMemberRepository departmentMemberRepository;
    private final org.springframework.messaging.simp.SimpMessagingTemplate messagingTemplate;
    private final AnalyticsService analyticsService;

    public ElectionService(
            ElectionRepository electionRepository,
            ElectionPartylistRepository electionPartylistRepository,
            ElectionDepartmentRepository electionDepartmentRepository,
            PartylistRepository partylistRepository,
            DepartmentRepository departmentRepository,
            CampusRepository campusRepository,
            AuditLogService auditLogService,
            ArchiveRecordRepository archiveRecordRepository,
            TrashRecordRepository trashRecordRepository,
            ObjectMapper objectMapper,
            PartylistMemberRepository partylistMemberRepository,
            CandidateRepository candidateRepository,
            DepartmentMemberRepository departmentMemberRepository,
            SimpMessagingTemplate messagingTemplate,
            AnalyticsService analyticsService
    ) {
        this.electionRepository = electionRepository;
        this.electionPartylistRepository = electionPartylistRepository;
        this.electionDepartmentRepository = electionDepartmentRepository;
        this.partylistRepository = partylistRepository;
        this.departmentRepository = departmentRepository;
        this.campusRepository = campusRepository;
        this.auditLogService = auditLogService;
        this.archiveRecordRepository = archiveRecordRepository;
        this.trashRecordRepository = trashRecordRepository;
        this.objectMapper = objectMapper;
        this.partylistMemberRepository = partylistMemberRepository;
        this.candidateRepository = candidateRepository;
        this.departmentMemberRepository = departmentMemberRepository;
        this.messagingTemplate = messagingTemplate;
        this.analyticsService = analyticsService;
    }


    public List<Election> getAllActive() {
        return electionRepository.findByStatus(RecordStatus.ACTIVE);
    }

    public List<Election> getByCampus(UUID campusId) {
        return electionRepository.findByCampusIdAndStatus(
                campusId,
                RecordStatus.ACTIVE
        );
    }

    public Election getById(UUID id) {
        return electionRepository.findByIdAndStatus(
                id,
                RecordStatus.ACTIVE
        ).orElseThrow(() ->
                new RuntimeException("Election not found.")
        );
    }

    public Election create(
            String title,
            ElectionCategory category,
            UUID campusId,
            String schoolYear,
            Instant startAt,
            Instant endAt,
            UUID createdBy,
            List<UUID> partylistIds,
            List<UUID> departmentIds,
            HttpServletRequest request
    ) {

        if (title == null || title.isBlank())
            throw new IllegalArgumentException("Election title is required.");

        if (category == null)
            throw new IllegalArgumentException("Election category is required.");

        if (campusId == null)
            throw new IllegalArgumentException("Campus is required.");

        if (schoolYear == null || schoolYear.isBlank())
            throw new IllegalArgumentException("School year is required.");

        if (startAt == null || endAt == null)
            throw new IllegalArgumentException("Election schedule is required.");

        if (!endAt.isAfter(startAt))
            throw new IllegalArgumentException(
                    "End date must be after start date."
            );

        Election election = new Election();

        election.setTitle(title.trim());
        election.setCategory(category);
        election.setCampusId(campusId);
        election.setSchoolYear(schoolYear.trim());
        election.setStartAt(startAt);
        election.setEndAt(endAt);
        election.setStatus(RecordStatus.ACTIVE);
        election.setActive(true);
        election.setCreatedBy(createdBy);

        Election saved = electionRepository.save(election);

        auditLogService.log(
                request,
                AuditAction.CREATE,
                "Elections",
                saved.getId(),
                "created election: " + saved.getTitle(),
                Map.of(
                        "title", saved.getTitle(),
                        "category", saved.getCategory().name(),
                        "campusId", saved.getCampusId(),
                        "schoolYear", saved.getSchoolYear()
                )
        );


        // =========================
        // SSC → CAMPUS PARTYLISTS
        // =========================

        if (category == ElectionCategory.SSC) {

            if (partylistIds == null || partylistIds.isEmpty()) {
                throw new IllegalArgumentException(
                        "SSC election requires at least one partylist."
                );
            }

            for (UUID partylistId : partylistIds) {

                Partylist partylist = partylistRepository.findById(partylistId)
                        .orElseThrow(() ->
                                new IllegalArgumentException("Partylist not found.")
                        );

                if (!partylist.getCampus().getId().equals(campusId)) {
                    throw new IllegalArgumentException(
                            "Partylist does not belong to the selected campus."
                    );
                }

                ElectionPartylist link = new ElectionPartylist();
                link.setElection(saved);
                link.setPartylist(partylist);

                electionPartylistRepository.save(link);

                List<PartylistMember> members =
                        partylistMemberRepository.findByPartylistId(partylistId);

                for (PartylistMember member : members) {
                    Candidate candidate = new Candidate();
                    candidate.setElectionId(election.getId());
                    candidate.setPartylistId(partylistId);
                    candidate.setStudentId(member.getStudentId());
                    candidate.setLastName(member.getLastName());
                    candidate.setFirstName(member.getFirstName());
                    candidate.setMiddleName(member.getMiddleName());
                    candidate.setPosition(member.getPosition());
                    candidate.setCampaignImageUrl(member.getCampaignImageUrl());
                    candidate.setBackgroundImageUrl(member.getBackgroundImageUrl());
                    candidate.setPhotoImageUrl(member.getPhotoImageUrl());
                    candidateRepository.save(candidate);
                }
            }
        }

        // =========================
        // DEPARTMENT → CAMPUS DEPARTMENTS
        // =========================

        if (category == ElectionCategory.DEPARTMENT) {

            VotingType selectedVotingType = null;

            if (departmentIds == null || departmentIds.isEmpty()) {
                throw new IllegalArgumentException(
                        "Department election requires at least one department."
                );
            }

            VotingType firstVotingType = getDepartmentVotingType(departmentIds.get(0));

            if (firstVotingType == VotingType.PARTYLIST && departmentIds.size() < 2) {
                throw new IllegalArgumentException(
                        "Partylist voting requires at least two departments."
                );
            }

            if (firstVotingType == VotingType.REPRESENTATIVE && departmentIds.size() > 1) {
                throw new IllegalArgumentException(
                        "Representative voting allows only one department."
                );
            }

            for (UUID departmentId : departmentIds) {

                VotingType votingType = getDepartmentVotingType(departmentId);

                if (selectedVotingType == null) {
                    selectedVotingType = votingType;
                } else if (votingType != selectedVotingType) {
                    throw new IllegalArgumentException(
                            "All selected departments must have the same voting type."
                    );
                }

                Department department = departmentRepository.findById(departmentId)
                        .orElseThrow(() ->
                                new IllegalArgumentException(
                                        "Department not found."
                                )
                        );

                if (!department.getCampus().getId().equals(campusId)) {
                    throw new IllegalArgumentException(
                            "Department does not belong to the selected campus."
                    );
                }

                ElectionDepartment link = new ElectionDepartment();
                link.setElection(saved);
                link.setDepartment(department);

                electionDepartmentRepository.save(link);

                List<DepartmentMember> members =
                        departmentMemberRepository.findByDepartmentId(departmentId);

                for (DepartmentMember member : members) {
                    Candidate candidate = new Candidate();
                    candidate.setElectionId(saved.getId());
                    candidate.setDepartmentId(departmentId);
                    candidate.setStudentId(member.getStudentId());
                    candidate.setLastName(member.getLastName());
                    candidate.setFirstName(member.getFirstName());
                    candidate.setMiddleName(member.getMiddleName());
                    candidate.setPosition(
                            votingType == VotingType.PARTYLIST ? member.getPosition() : null
                    );
                    candidate.setCampaignImageUrl(member.getCampaignImageUrl());
                    candidate.setBackgroundImageUrl(member.getBackgroundImageUrl());
                    candidate.setPhotoImageUrl(member.getPhotoImageUrl());
                    candidateRepository.save(candidate);
                }
            }
        }

        return saved;
    }

    public Election update(
            UUID id,
            String title,
            ElectionCategory category,
            UUID campusId,
            String schoolYear,
            Instant startAt,
            Instant endAt,
            List<UUID> partylistIds,
            List<UUID> departmentIds,
            HttpServletRequest request
    ) {

        Election election = getById(id);

        RecordStatus currentStatus = calculateElectionStatus(election);

        if (currentStatus == RecordStatus.ONGOING || currentStatus == RecordStatus.CONCLUDED) {
            throw new IllegalStateException(
                    "This election cannot be edited because voting has already started or ended."
            );
        }

        String oldTitle = election.getTitle();
        ElectionCategory oldCategory = election.getCategory();
        UUID oldCampusId = election.getCampusId();
        String oldSchoolYear = election.getSchoolYear();
        Instant oldStartAt = election.getStartAt();
        Instant oldEndAt = election.getEndAt();

        List<UUID> oldPartylistIds =
                electionPartylistRepository.findByElectionId(id)
                        .stream()
                        .map(link -> link.getPartylist().getId())
                        .toList();

        List<UUID> oldDepartmentIds =
                electionDepartmentRepository.findByElectionId(id)
                        .stream()
                        .map(link -> link.getDepartment().getId())
                        .toList();

        if (title == null || title.isBlank())
            throw new IllegalArgumentException("Election title is required.");

        if (category == null)
            throw new IllegalArgumentException("Election category is required.");

        if (campusId == null)
            throw new IllegalArgumentException("Campus is required.");

        if (schoolYear == null || schoolYear.isBlank())
            throw new IllegalArgumentException("School year is required.");

        if (startAt == null || endAt == null)
            throw new IllegalArgumentException("Election schedule is required.");

        if (!endAt.isAfter(startAt))
            throw new IllegalArgumentException(
                    "End date must be after start date."
            );

        election.setTitle(title.trim());
        election.setCategory(category);
        election.setCampusId(campusId);
        election.setSchoolYear(schoolYear.trim());
        election.setStartAt(startAt);
        election.setEndAt(endAt);

        // Remove old relationships
        electionPartylistRepository.deleteByElectionId(id);
        electionPartylistRepository.flush();

        electionDepartmentRepository.deleteByElectionId(id);
        electionDepartmentRepository.flush();

        // =========================
        // SSC → CAMPUS PARTYLISTS
        // =========================

        if (category == ElectionCategory.SSC) {

            if (partylistIds == null || partylistIds.isEmpty()) {
                throw new IllegalArgumentException(
                        "SSC election requires at least one partylist."
                );
            }

            candidateRepository.deleteByElectionId(election.getId());
            candidateRepository.flush();

            for (UUID partylistId : partylistIds) {

                Partylist partylist = partylistRepository.findById(partylistId)
                        .orElseThrow(() ->
                                new IllegalArgumentException(
                                        "Partylist not found."
                                )
                        );

                if (!partylist.getCampus().getId().equals(campusId)) {
                    throw new IllegalArgumentException(
                            "Partylist does not belong to the selected campus."
                    );
                }

                ElectionPartylist link = new ElectionPartylist();
                link.setElection(election);
                link.setPartylist(partylist);

                electionPartylistRepository.save(link);

                List<PartylistMember> members =
                        partylistMemberRepository.findByPartylistId(partylistId);

                for (PartylistMember member : members) {
                    Candidate candidate = new Candidate();
                    candidate.setElectionId(election.getId());
                    candidate.setPartylistId(partylistId);
                    candidate.setStudentId(member.getStudentId());
                    candidate.setLastName(member.getLastName());
                    candidate.setFirstName(member.getFirstName());
                    candidate.setMiddleName(member.getMiddleName());
                    candidate.setPosition(member.getPosition());
                    candidate.setCampaignImageUrl(member.getCampaignImageUrl());
                    candidate.setBackgroundImageUrl(member.getBackgroundImageUrl());
                    candidate.setPhotoImageUrl(member.getPhotoImageUrl());
                    candidateRepository.save(candidate);
                }
            }
        }

        // =========================
        // DEPARTMENT → CAMPUS DEPARTMENTS
        // =========================

        if (category == ElectionCategory.DEPARTMENT) {

            VotingType selectedVotingType = null;

            if (departmentIds == null || departmentIds.isEmpty()) {
                throw new IllegalArgumentException(
                        "Department election requires at least one department."
                );
            }

            VotingType firstVotingType = getDepartmentVotingType(departmentIds.get(0));

            if (firstVotingType == VotingType.PARTYLIST && departmentIds.size() < 2) {
                throw new IllegalArgumentException(
                        "Partylist voting requires at least two departments."
                );
            }

            if (firstVotingType == VotingType.REPRESENTATIVE && departmentIds.size() > 1) {
                throw new IllegalArgumentException(
                        "Representative voting allows only one department."
                );
            }

            candidateRepository.deleteByElectionId(election.getId());
            candidateRepository.flush();

            for (UUID departmentId : departmentIds) {

                VotingType votingType = getDepartmentVotingType(departmentId);

                if (selectedVotingType == null) {
                    selectedVotingType = votingType;
                } else if (votingType != selectedVotingType) {
                    throw new IllegalArgumentException(
                            "All selected departments must have the same voting type."
                    );
                }

                Department department = departmentRepository.findById(departmentId)
                        .orElseThrow(() ->
                                new IllegalArgumentException(
                                        "Department not found."
                                )
                        );

                if (!department.getCampus().getId().equals(campusId)) {
                    throw new IllegalArgumentException(
                            "Department does not belong to the selected campus."
                    );
                }

                ElectionDepartment link = new ElectionDepartment();
                link.setElection(election);
                link.setDepartment(department);

                electionDepartmentRepository.save(link);

                List<DepartmentMember> members =
                        departmentMemberRepository.findByDepartmentId(departmentId);

                for (DepartmentMember member : members) {
                    Candidate candidate = new Candidate();
                    candidate.setElectionId(election.getId());
                    candidate.setDepartmentId(departmentId);
                    candidate.setStudentId(member.getStudentId());
                    candidate.setLastName(member.getLastName());
                    candidate.setFirstName(member.getFirstName());
                    candidate.setMiddleName(member.getMiddleName());
                    candidate.setPosition(
                            votingType == VotingType.PARTYLIST ? member.getPosition() : null
                    );
                    candidate.setCampaignImageUrl(member.getCampaignImageUrl());
                    candidate.setBackgroundImageUrl(member.getBackgroundImageUrl());
                    candidate.setPhotoImageUrl(member.getPhotoImageUrl());
                    candidateRepository.save(candidate);
                }
            }
        }

        Election saved = electionRepository.save(election);

        List<String> changes = new java.util.ArrayList<>();

        if (!java.util.Objects.equals(oldTitle, saved.getTitle())) {
            changes.add("title from \"" + oldTitle + "\" to \"" + saved.getTitle() + "\"");
        }

        if (!java.util.Objects.equals(oldCategory, saved.getCategory())) {
            changes.add("category from \"" + oldCategory + "\" to \"" + saved.getCategory() + "\"");
        }

        if (!java.util.Objects.equals(oldCampusId, saved.getCampusId())) {
            changes.add("campus");
        }

        if (!java.util.Objects.equals(oldSchoolYear, saved.getSchoolYear())) {
            changes.add("school year from \"" + oldSchoolYear + "\" to \"" + saved.getSchoolYear() + "\"");
        }

        if (!java.util.Objects.equals(oldStartAt, saved.getStartAt())) {
            changes.add("start date");
        }

        if (!java.util.Objects.equals(oldEndAt, saved.getEndAt())) {
            changes.add("end date");
        }

        List<UUID> newPartylistIds =
                electionPartylistRepository.findByElectionId(id)
                        .stream()
                        .map(link -> link.getPartylist().getId())
                        .toList();

        List<UUID> newDepartmentIds =
                electionDepartmentRepository.findByElectionId(id)
                        .stream()
                        .map(link -> link.getDepartment().getId())
                        .toList();

        if (!java.util.Objects.equals(oldPartylistIds, newPartylistIds)) {
            changes.add("partylists");
        }

        if (!java.util.Objects.equals(oldDepartmentIds, newDepartmentIds)) {
            changes.add("departments");
        }

        if (!changes.isEmpty()) {

            String description;

            if (changes.size() == 1) {
                description = "changed " + changes.get(0)
                        + " in election \"" + saved.getTitle() + "\"";
            } else {
                description = "changed election \"" + saved.getTitle()
                        + "\": " + String.join(", ", changes);
            }

            auditLogService.log(
                    request,
                    AuditAction.UPDATE,
                    "Elections",
                    saved.getId(),
                    description,
                    Map.of(
                            "changes", changes
                    )
            );
        }

        return saved;
    }

    @Transactional
    public void resyncCandidateImages(UUID electionId) {

        Election election = getById(electionId);

        List<Candidate> candidates = candidateRepository.findByElectionId(electionId);

        for (Candidate candidate : candidates) {

            if (candidate.getDepartmentId() != null) {

                departmentMemberRepository
                        .findByDepartmentIdAndStudentId(
                                candidate.getDepartmentId(),
                                candidate.getStudentId()
                        )
                        .ifPresent(member -> {
                            candidate.setPhotoImageUrl(member.getPhotoImageUrl());
                            candidate.setCampaignImageUrl(member.getCampaignImageUrl());
                            candidate.setBackgroundImageUrl(member.getBackgroundImageUrl());
                            candidateRepository.save(candidate);
                        });

            } else if (candidate.getPartylistId() != null) {

                partylistMemberRepository
                        .findByPartylistIdAndStudentId(
                                candidate.getPartylistId(),
                                candidate.getStudentId()
                        )
                        .ifPresent(member -> {
                            candidate.setPhotoImageUrl(member.getPhotoImageUrl());
                            candidate.setCampaignImageUrl(member.getCampaignImageUrl());
                            candidate.setBackgroundImageUrl(member.getBackgroundImageUrl());
                            candidateRepository.save(candidate);
                        });
            }
        }
    }

    public ElectionResponse toResponse(Election election) {

        ElectionResponse response = new ElectionResponse();

        response.setId(election.getId());
        response.setTitle(election.getTitle());
        response.setCategory(election.getCategory());
        response.setCampusId(election.getCampusId());
        response.setSchoolYear(election.getSchoolYear());
        response.setStartAt(election.getStartAt());
        response.setEndAt(election.getEndAt());
        RecordStatus currentStatus = calculateElectionStatus(election);

        response.setStatus(currentStatus);
        response.setActive(currentStatus != RecordStatus.CONCLUDED);

        // Campus name
        String campusName = campusRepository.findById(election.getCampusId())
                .map(Campus::getName)
                .orElse(null);

        response.setCampusName(campusName);


        // Partylists
        List<ElectionPartylist> partylists =
                electionPartylistRepository.findByElectionId(election.getId());

        response.setPartylistIds(
                partylists.stream()
                        .map(link -> link.getPartylist().getId())
                        .toList()
        );

        response.setPartylistNames(
                partylists.stream()
                        .map(link -> link.getPartylist().getName())
                        .toList()
        );

        // Departments
        List<ElectionDepartment> departments =
                electionDepartmentRepository.findByElectionId(election.getId());

        response.setDepartmentIds(
                departments.stream()
                        .map(link -> link.getDepartment().getId())
                        .toList()
        );

        response.setDepartmentNames(
                departments.stream()
                        .map(link -> link.getDepartment().getTitle())
                        .toList()
        );

        return response;
    }

    public void archive(
            UUID id,
            HttpServletRequest request
    ) {
        Election election = getById(id);

        UUID archivedBy = null;

        if (request.getSession(false) != null) {
            Object userId = request.getSession(false).getAttribute("userId");

            if (userId != null) {
                archivedBy = UUID.fromString(userId.toString());
            }
        }

        ArchiveRecord archive = new ArchiveRecord();

        archive.setEntityType("Elections");
        archive.setEntityId(election.getId());
        archive.setEntityName(election.getTitle());
        archive.setData(electionToJson(election));
        archive.setArchivedBy(archivedBy);
        archive.setArchivedAt(Instant.now());
        archive.setRestored(false);

        archiveRecordRepository.save(archive);

        election.setStatus(RecordStatus.ARCHIVED);
        election.setActive(false);

        electionRepository.save(election);

        auditLogService.log(
                request,
                AuditAction.ARCHIVE,
                "Elections",
                election.getId(),
                "archived election: " + election.getTitle(),
                Map.of(
                        "title", election.getTitle(),
                        "entityType", "Elections"
                )
        );
    }

    public void delete(
            UUID id,
            HttpServletRequest request
    ) {
        Election election = getById(id);

        UUID deletedBy = null;

        if (request.getSession(false) != null) {
            Object userId = request.getSession(false).getAttribute("userId");

            if (userId != null) {
                deletedBy = UUID.fromString(userId.toString());
            }
        }

        TrashRecord trash = new TrashRecord();

        trash.setEntityType("Elections");
        trash.setEntityId(election.getId());
        trash.setEntityName(election.getTitle());
        trash.setData(electionToJson(election));
        trash.setDeletedBy(deletedBy);
        trash.setDeletedAt(Instant.now());
        trash.setRestored(false);

        trashRecordRepository.save(trash);

        election.setStatus(RecordStatus.DELETED);
        election.setActive(false);

        electionRepository.save(election);

        auditLogService.log(
                request,
                AuditAction.DELETE,
                "Elections",
                election.getId(),
                "deleted election: " + election.getTitle(),
                Map.of(
                        "title", election.getTitle(),
                        "entityType", "Elections"
                )
        );
    }

    private String electionToJson(Election election) {
        try {
            Map<String, Object> data = new java.util.HashMap<>();

            data.put("id", election.getId());
            data.put("title", election.getTitle());
            data.put("category", election.getCategory());
            data.put("campusId", election.getCampusId());
            data.put("schoolYear", election.getSchoolYear());
            data.put("startAt", election.getStartAt());
            data.put("endAt", election.getEndAt());
            data.put("status", election.getStatus());
            data.put("active", election.isActive());
            data.put("createdBy", election.getCreatedBy());
            data.put("createdAt", election.getCreatedAt());
            data.put("updatedAt", election.getUpdatedAt());

            data.put(
                    "partylistIds",
                    electionPartylistRepository.findByElectionId(election.getId())
                            .stream()
                            .map(link -> link.getPartylist().getId())
                            .toList()
            );

            data.put(
                    "departmentIds",
                    electionDepartmentRepository.findByElectionId(election.getId())
                            .stream()
                            .map(link -> link.getDepartment().getId())
                            .toList()
            );

            ObjectMapper mapper = objectMapper.copy();
            mapper.registerModule(new JavaTimeModule());

            return mapper.writeValueAsString(data);

        } catch (JsonProcessingException e) {
            throw new RuntimeException(
                    "Failed to serialize election data.",
                    e
            );
        }
    }

    public enum VoterElectionPhase {
        HIDDEN,
        UPCOMING,
        ONGOING,
        CONCLUDED
    }

    public VoterElectionPhase calculateVoterPhase(Election election) {
        Instant now = Instant.now();
        Instant fiveDaysBefore = election.getStartAt().minus(java.time.Duration.ofDays(5));

        if (now.isAfter(election.getEndAt())) {
            return VoterElectionPhase.CONCLUDED;
        }

        if (!now.isBefore(election.getStartAt())) {
            return VoterElectionPhase.ONGOING;
        }

        if (!now.isBefore(fiveDaysBefore)) {
            return VoterElectionPhase.UPCOMING;
        }

        return VoterElectionPhase.HIDDEN;
    }

    private RecordStatus calculateElectionStatus(Election election) {
        Instant now = Instant.now();

        if (now.isBefore(election.getStartAt())) {
            return RecordStatus.ACTIVE;
        }

        if (!now.isAfter(election.getEndAt())) {
            return RecordStatus.ONGOING;
        }

        return RecordStatus.CONCLUDED;
    }

    private VotingType getDepartmentVotingType(UUID departmentId) {
        return departmentRepository.findById(departmentId)
                .orElseThrow(() ->
                        new IllegalArgumentException("Department not found.")
                )
                .getVotingType();
    }

    private void broadcastAnalyticsUpdate() {
        messagingTemplate.convertAndSend("/topic/analytics", analyticsService.getFullAnalytics());
    }
}