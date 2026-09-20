package lccast.voting.system.service.superadmin;

import com.fasterxml.jackson.databind.ObjectMapper;

import lccast.voting.system.dto.HistoryEventDTO;
import lccast.voting.system.model.*;
import lccast.voting.system.repository.*;

import lccast.voting.system.service.AuditLogService;
import lccast.voting.system.service.SupabaseAdminCreateUserResponse;
import lccast.voting.system.service.SupabaseAuthService;
import lccast.voting.system.service.student.VoterDepartmentElectionService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class VoterService {

    private final VoterRepository voterRepository;
    private final CampusRepository campusRepository;
    private final ArchiveRecordRepository archiveRecordRepository;
    private final TrashRecordRepository trashRecordRepository;
    private final AuditLogService auditLogService;
    private final ObjectMapper objectMapper;
    private final SupabaseAuthService supabaseAuthService;
    private final UserProfileRepository userProfileRepository;
    private final SimpMessagingTemplate messagingTemplate;
    private final ElectionRepository electionRepository;
    private final VoteLogRepository voteLogRepository;
    private final lccast.voting.system.service.student.VoterDepartmentElectionService voterDepartmentElectionService;

    public VoterService(
            VoterRepository voterRepository,
            CampusRepository campusRepository,
            ArchiveRecordRepository archiveRecordRepository,
            TrashRecordRepository trashRecordRepository,
            AuditLogService auditLogService,
            ObjectMapper objectMapper,
            SupabaseAuthService supabaseAuthService,
            UserProfileRepository userProfileRepository,
            SimpMessagingTemplate messagingTemplate,   // ADD
            ElectionRepository electionRepository,
            VoteLogRepository voteLogRepository,
            VoterDepartmentElectionService voterDepartmentElectionService
    ) {
        this.voterRepository = voterRepository;
        this.campusRepository = campusRepository;
        this.archiveRecordRepository = archiveRecordRepository;
        this.trashRecordRepository = trashRecordRepository;
        this.auditLogService = auditLogService;
        this.objectMapper = objectMapper;
        this.supabaseAuthService = supabaseAuthService;
        this.userProfileRepository = userProfileRepository;
        this.messagingTemplate = messagingTemplate;   // ADD
        this.electionRepository = electionRepository;
        this.voteLogRepository = voteLogRepository;
        this.voterDepartmentElectionService = voterDepartmentElectionService;
    }

    // =========================================================
    // ARCHIVE
    // =========================================================

    @Transactional
    public void archiveVoter(UUID id, HttpServletRequest request) {

        Voter voter = voterRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Voter not found."));

        try {
            ArchiveRecord record = new ArchiveRecord();

            record.setEntityType("Students");
            record.setEntityId(voter.getId());
            record.setEntityName(voter.getFullName());
            record.setData(voterSnapshot(voter));
            record.setArchivedAt(Instant.now());

            Object userIdAttribute =
                    request.getSession().getAttribute("userId");

            UUID userId = null;

            if (userIdAttribute != null) {
                userId = UUID.fromString(userIdAttribute.toString());
            }

            record.setArchivedBy(userId);

            archiveRecordRepository.save(record);

            messagingTemplate.convertAndSend(
                    "/topic/history/archives",
                    HistoryEventDTO.of("ARCHIVED", record.getId().toString())
            );

            // DO NOT physically delete the voter
            voter.setStatus(RecordStatus.ARCHIVED);
            voter.setUpdatedAt(Instant.now());

            voterRepository.save(voter);
            disableVoterAccount(voter);

            auditLogService.log(
                    request,
                    AuditAction.ARCHIVE,
                    "Students",
                    voter.getId(),
                    "Archived voter " + voter.getStudentId(),
                    Map.of(
                            "studentId", voter.getStudentId(),
                            "fullName", voter.getFullName()
                    )
            );

        } catch (Exception e) {
            e.printStackTrace();
            throw new RuntimeException(
                    "Unable to archive voter: " + e.getMessage(),
                    e
            );
        }
    }

    // =========================================================
    // TRASH
    // =========================================================

    @Transactional
    public void deleteVoter(UUID id, HttpServletRequest request) {

        Voter voter = voterRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Voter not found."));

        try {
            TrashRecord record = new TrashRecord();

            record.setEntityType("Students");
            record.setEntityId(voter.getId());
            record.setEntityName(voter.getFullName());
            record.setData(voterSnapshot(voter));
            record.setDeletedAt(Instant.now());

            Object userIdAttribute =
                    request.getSession().getAttribute("userId");

            UUID userId = null;

            if (userIdAttribute != null) {
                userId = UUID.fromString(userIdAttribute.toString());
            }

            record.setDeletedBy(userId);

            trashRecordRepository.save(record);

            messagingTemplate.convertAndSend(
                    "/topic/history/trash",
                    HistoryEventDTO.of("TRASHED", record.getId().toString())
            );

            // DO NOT physically delete the voter
            voter.setStatus(RecordStatus.DELETED);
            voter.setUpdatedAt(Instant.now());

            voterRepository.save(voter);
            disableVoterAccount(voter);

            auditLogService.log(
                    request,
                    AuditAction.DELETE,
                    "Students",
                    voter.getId(),
                    "Moved voter " + voter.getStudentId() + " to trash",
                    Map.of(
                            "studentId", voter.getStudentId(),
                            "fullName", voter.getFullName()
                    )
            );

        } catch (Exception e) {
            e.printStackTrace();
            throw new RuntimeException(
                    "Unable to delete voter: " + e.getMessage(),
                    e
            );
        }
    }

    // =========================================================
// RESTORE
// =========================================================

    @Transactional
    public void restoreVoterRecords(UUID voterId) {

        Instant now = Instant.now();

        trashRecordRepository.findByEntityTypeAndRestoredFalse("Students").stream()
                .filter(r -> voterId.equals(r.getEntityId()))
                .forEach(r -> {
                    r.setRestored(true);
                    r.setRestoredAt(now);
                    trashRecordRepository.save(r);
                });

        archiveRecordRepository.findByEntityTypeAndRestoredFalse("Students").stream()
                .filter(r -> voterId.equals(r.getEntityId()))
                .forEach(r -> {
                    r.setRestored(true);
                    r.setRestoredAt(now);
                    archiveRecordRepository.save(r);
                });

        voterRepository.findById(voterId).ifPresent(this::enableVoterAccount);
    }

// =========================================================
// PERMANENT DELETE
// =========================================================

    @Transactional
    public void permanentlyDeleteVoter(UUID id) {

        Voter voter = voterRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Voter not found."));

        if (voter.getStatus() != RecordStatus.DELETED) {
            throw new RuntimeException("Only trashed voters can be permanently deleted.");
        }

        trashRecordRepository.findByEntityTypeAndRestoredFalse("Students").stream()
                .filter(r -> id.equals(r.getEntityId()))
                .forEach(trashRecordRepository::delete);

        if (voter.getAuthUserId() != null) {
            try {
                supabaseAuthService.deleteUser(voter.getAuthUserId().toString());
            } catch (org.springframework.web.client.HttpClientErrorException.NotFound e) {
                // Auth user already gone — fine, continue.
            }
            // Any other Supabase error propagates and aborts the deletes below —
            // safer than deleting local rows while the auth account survives.

            userProfileRepository.findByAuthUserId(voter.getAuthUserId())
                    .ifPresent(userProfileRepository::delete);
        }

        voterRepository.delete(voter);
    }

    // =========================================================
// BULK DELETE / TRASH
// =========================================================

    @Transactional
    public int deleteVoters(
            List<UUID> ids,
            HttpServletRequest request
    ) {
        int count = 0;

        List<Map<String, Object>> deletedVoters = new ArrayList<>();

        Object userIdAttribute =
                request.getSession().getAttribute("userId");

        UUID userId = null;

        if (userIdAttribute != null) {
            userId = UUID.fromString(userIdAttribute.toString());
        }

        for (UUID id : ids) {

            Voter voter = voterRepository.findById(id)
                    .orElseThrow(() ->
                            new RuntimeException("Voter not found.")
                    );

            TrashRecord record = new TrashRecord();

            record.setEntityType("Students");
            record.setEntityId(voter.getId());
            record.setEntityName(voter.getFullName());
            record.setData(voterSnapshot(voter));
            record.setDeletedAt(Instant.now());
            record.setDeletedBy(userId);

            trashRecordRepository.save(record);



            voter.setStatus(RecordStatus.DELETED);
            voter.setUpdatedAt(Instant.now());

            voterRepository.save(voter);
            disableVoterAccount(voter);

            deletedVoters.add(
                    Map.of(
                            "id", voter.getId(),
                            "studentId", voter.getStudentId(),
                            "fullName", voter.getFullName()
                    )
            );

            count++;
        }

        if (count > 0) {
            auditLogService.log(
                    request,
                    AuditAction.DELETE,
                    "Students",
                    null,
                    "Moved " + count + " voters to trash",
                    Map.of(
                            "count", count,
                            "voters", deletedVoters
                    )
            );

            messagingTemplate.convertAndSend(          // ADD
                    "/topic/history/trash",
                    HistoryEventDTO.ofCount("BULK_TRASHED", count)
            );
        }

        return count;
    }

// =========================================================
// BULK ARCHIVE
// =========================================================

    @Transactional
    public int archiveVoters(
            List<UUID> ids,
            HttpServletRequest request
    ) {
        int count = 0;

        List<Map<String, Object>> archivedVoters = new ArrayList<>();

        Object userIdAttribute =
                request.getSession().getAttribute("userId");

        UUID userId = null;

        if (userIdAttribute != null) {
            userId = UUID.fromString(userIdAttribute.toString());
        }

        for (UUID id : ids) {

            Voter voter = voterRepository.findById(id)
                    .orElseThrow(() ->
                            new RuntimeException("Voter not found.")
                    );

            ArchiveRecord record = new ArchiveRecord();

            record.setEntityType("Students");
            record.setEntityId(voter.getId());
            record.setEntityName(voter.getFullName());
            record.setData(voterSnapshot(voter));
            record.setArchivedAt(Instant.now());
            record.setArchivedBy(userId);

            archiveRecordRepository.save(record);



            voter.setStatus(RecordStatus.ARCHIVED);
            voter.setUpdatedAt(Instant.now());

            voterRepository.save(voter);
            disableVoterAccount(voter);

            archivedVoters.add(
                    Map.of(
                            "id", voter.getId(),
                            "studentId", voter.getStudentId(),
                            "fullName", voter.getFullName()
                    )
            );

            count++;
        }

        if (count > 0) {
            auditLogService.log(
                    request,
                    AuditAction.ARCHIVE,
                    "Students",
                    null,
                    "Archived " + count + " voters",
                    Map.of(
                            "count", count,
                            "voters", archivedVoters
                    )
            );

            messagingTemplate.convertAndSend(
                    "/topic/history/archives",
                    HistoryEventDTO.ofCount("BULK_ARCHIVED", count)
            );
        }

        return count;
    }

    // =========================================================
    // IMPORT
    // =========================================================

    @Transactional
    public ImportResult importVoters(
            List<Map<String, Object>> rows,
            HttpServletRequest request
    ){

        int added = 0;
        int updated = 0;
        int skipped = 0;

        List<String> errors = new ArrayList<>();

        for (int i = 0; i < rows.size(); i++) {

            Map<String, Object> row = rows.get(i);
            int rowNumber = i + 2;

            try {

                String studentId = stringValue(row.get("studentId"));
                String lastName = stringValue(row.get("lastName"));
                String firstName = stringValue(row.get("firstName"));
                String middleName = stringValue(row.get("middleName"));
                String fullName = stringValue(row.get("fullName"));
                String email = stringValue(row.get("email"));
                String programCourse = stringValue(row.get("programCourse"));
                String yearLevel = stringValue(row.get("yearLevel"));
                String section = stringValue(row.get("section"));
                String campusId = stringValue(row.get("campusId"));

                if (studentId.isBlank()) {
                    errors.add("Row " + rowNumber + ": Student ID is required.");
                    skipped++;
                    continue;
                }

                if (firstName.isBlank()) {
                    errors.add("Row " + rowNumber + ": First name is required.");
                    skipped++;
                    continue;
                }

                if (lastName.isBlank()) {
                    errors.add("Row " + rowNumber + ": Last name is required.");
                    skipped++;
                    continue;
                }

                if (fullName.isBlank()) {
                    fullName = buildFullName(
                            firstName,
                            middleName,
                            lastName
                    );
                }

                if (campusId.isBlank()) {
                    errors.add("Row " + rowNumber + ": Campus is required.");
                    skipped++;
                    continue;
                }

                UUID campusUUID;

                try {
                    campusUUID = UUID.fromString(campusId);
                } catch (IllegalArgumentException e) {
                    errors.add("Row " + rowNumber + ": Invalid campus ID.");
                    skipped++;
                    continue;
                }

                Optional<Campus> campus =
                        campusRepository.findById(campusUUID);

                if (campus.isEmpty()) {
                    errors.add(
                            "Row " + rowNumber +
                                    ": Selected campus was not found."
                    );
                    skipped++;
                    continue;
                }

                Optional<Voter> existing =
                        voterRepository.findByStudentId(studentId);

                if (existing.isPresent()) {

                    Voter voter = existing.get();

                    voter.setLastName(lastName);
                    voter.setFirstName(firstName);
                    voter.setMiddleName(
                            middleName.isBlank()
                                    ? null
                                    : middleName
                    );
                    voter.setFullName(fullName);
                    voter.setEmail(
                            email.isBlank()
                                    ? null
                                    : email
                    );
                    voter.setProgramCourse(
                            programCourse.isBlank()
                                    ? null
                                    : programCourse
                    );
                    voter.setYearLevel(
                            yearLevel.isBlank()
                                    ? null
                                    : yearLevel
                    );
                    voter.setSection(
                            section.isBlank()
                                    ? null
                                    : section
                    );
                    voter.setCampusId(campusUUID);
                    voter.setStatus(RecordStatus.ACTIVE);
                    voter.setUpdatedAt(Instant.now());

                    voterRepository.save(voter);

                    if (voter.getAuthUserId() == null) {
                        createVoterUserAccount(voter, errors, rowNumber);
                    }

                    updated++;
                    continue;
                }

                Voter voter = new Voter();

                voter.setStudentId(studentId);
                voter.setLastName(lastName);
                voter.setFirstName(firstName);
                voter.setMiddleName(
                        middleName.isBlank()
                                ? null
                                : middleName
                );
                voter.setFullName(fullName);
                voter.setEmail(
                        email.isBlank()
                                ? null
                                : email
                );
                voter.setProgramCourse(
                        programCourse.isBlank()
                                ? null
                                : programCourse
                );
                voter.setYearLevel(
                        yearLevel.isBlank()
                                ? null
                                : yearLevel
                );
                voter.setSection(
                        section.isBlank()
                                ? null
                                : section
                );
                voter.setCampusId(campusUUID);
                voter.setVotingStatus(VotingStatus.NOT_VOTED);
                voter.setTimeVoted(null);
                voter.setStatus(RecordStatus.ACTIVE);

                Instant now = Instant.now();
                voter.setCreatedAt(now);
                voter.setUpdatedAt(now);

                voterRepository.save(voter);

                createVoterUserAccount(voter, errors, rowNumber);

                added++;

            } catch (Exception e) {

                errors.add(
                        "Row " + rowNumber +
                                ": " +
                                (e.getMessage() != null
                                        ? e.getMessage()
                                        : "Unable to import voter.")
                );

                skipped++;
            }
        }

        if (added > 0 || updated > 0) {
            auditLogService.log(
                    request,
                    AuditAction.IMPORT,
                    "Students",
                    null,
                    "Imported voters: " +
                            added + " added, " +
                            updated + " updated, " +
                            skipped + " skipped.",
                    Map.of(
                            "added", added,
                            "updated", updated,
                            "skipped", skipped
                    )
            );
        }

        return new ImportResult(
                added,
                updated,
                skipped,
                errors
        );
    }

    private void createVoterUserAccount(
            Voter voter,
            List<String> errors,
            int rowNumber
    ) {
        try {
            String studentId = voter.getStudentId();
            String syntheticEmail = studentId + "@voter.local";

            SupabaseAdminCreateUserResponse authResponse =
                    supabaseAuthService.createUser(syntheticEmail, studentId);

            if (authResponse == null || authResponse.getId() == null) {
                errors.add(
                        "Row " + rowNumber +
                                ": Voter saved but account creation failed."
                );
                return;
            }

            UUID authUserId = authResponse.getId();

            voter.setAuthUserId(authUserId);
            voterRepository.save(voter);

            UserProfile profile = new UserProfile();
            profile.setAuthUserId(authUserId);
            profile.setRole(UserRole.STUDENT);
            profile.setCampusId(voter.getCampusId());
            profile.setSchoolId(studentId);
            profile.setEmail(syntheticEmail);
            profile.setFirstName(voter.getFirstName());
            profile.setLastName(voter.getLastName());
            profile.setActive(true);
            profile.setMustChangePassword(true);

            userProfileRepository.save(profile);

        } catch (Exception e) {
            e.printStackTrace();
            errors.add(
                    "Row " + rowNumber +
                            ": Voter saved but account creation failed: " +
                            e.getMessage()
            );
        }
    }

    private String buildFullName(
            String firstName,
            String middleName,
            String lastName
    ) {
        return String.join(
                " ",
                List.of(firstName, middleName, lastName)
                        .stream()
                        .filter(value ->
                                value != null && !value.isBlank())
                        .toList()
        ).trim();
    }

    private String stringValue(Object value) {
        return value == null
                ? ""
                : String.valueOf(value).trim();
    }

    public record ImportResult(
            int added,
            int updated,
            int skipped,
            List<String> errors
    ) {}

    public void auditUpdate(
            HttpServletRequest request,
            Voter voter
    ) {
        auditLogService.log(
                request,
                AuditAction.UPDATE,
                "Students",
                voter.getId(),
                "Updated voter " + voter.getStudentId(),
                Map.of(
                        "studentId", voter.getStudentId(),
                        "fullName", voter.getFullName()
                )
        );
    }

    public void auditRestore(
            HttpServletRequest request,
            Voter voter
    ) {
        auditLogService.log(
                request,
                AuditAction.RESTORE,
                "Students",
                voter.getId(),
                "Restored voter " + voter.getStudentId(),
                Map.of(
                        "studentId", voter.getStudentId(),
                        "fullName", voter.getFullName()
                )
        );
    }

    private String voterSnapshot(Voter voter) {
        try {
            Map<String, Object> snapshot = new java.util.LinkedHashMap<>();

            snapshot.put("id", voter.getId());
            snapshot.put("studentId", voter.getStudentId());
            snapshot.put("lastName", voter.getLastName());
            snapshot.put("firstName", voter.getFirstName());
            snapshot.put("middleName", voter.getMiddleName());
            snapshot.put("fullName", voter.getFullName());
            snapshot.put("email", voter.getEmail());
            snapshot.put("programCourse", voter.getProgramCourse());
            snapshot.put("yearLevel", voter.getYearLevel());
            snapshot.put("section", voter.getSection());
            snapshot.put("campusId", voter.getCampusId());

            snapshot.put(
                    "votingStatus",
                    voter.getVotingStatus() == null
                            ? null
                            : voter.getVotingStatus().name()
            );

            snapshot.put(
                    "timeVoted",
                    voter.getTimeVoted() == null
                            ? null
                            : voter.getTimeVoted().toString()
            );

            snapshot.put(
                    "status",
                    voter.getStatus() == null
                            ? null
                            : voter.getStatus().name()
            );

            snapshot.put(
                    "createdAt",
                    voter.getCreatedAt() == null
                            ? null
                            : voter.getCreatedAt().toString()
            );

            snapshot.put(
                    "updatedAt",
                    voter.getUpdatedAt() == null
                            ? null
                            : voter.getUpdatedAt().toString()
            );

            return objectMapper.writeValueAsString(snapshot);

        } catch (Exception e) {
            throw new RuntimeException(
                    "Unable to create voter snapshot.",
                    e
            );
        }
    }

    private void disableVoterAccount(Voter voter) {
        if (voter.getAuthUserId() == null) return;

        userProfileRepository.findByAuthUserId(voter.getAuthUserId()).ifPresent(profile -> {
            profile.setActive(false);
            userProfileRepository.save(profile);
        });

        try {
            supabaseAuthService.setUserBanStatus(voter.getAuthUserId().toString(), "876000h"); // ~100 years
        } catch (Exception e) {
            e.printStackTrace(); // don't fail the archive/delete if Supabase call hiccups
        }
    }

    public void enableVoterAccount(Voter voter) {
        if (voter.getAuthUserId() == null) return;

        userProfileRepository.findByAuthUserId(voter.getAuthUserId()).ifPresent(profile -> {
            profile.setActive(true);
            userProfileRepository.save(profile);
        });

        try {
            supabaseAuthService.setUserBanStatus(voter.getAuthUserId().toString(), "none");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public List<Map<String, Object>> toResponseList(List<Voter> voters) {

        if (voters.isEmpty()) return List.of();

        Set<UUID> campusIds = voters.stream()
                .map(Voter::getCampusId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        Map<UUID, String> campusNamesById = campusRepository.findAllById(campusIds).stream()
                .collect(Collectors.toMap(Campus::getId, Campus::getName));

        Map<UUID, UUID> sscElectionIdByCampus = new HashMap<>();
        for (UUID campusId : campusIds) {
            electionRepository
                    .findByCampusIdAndCategoryAndStatus(campusId, ElectionCategory.SSC, RecordStatus.ACTIVE)
                    .stream().findFirst()
                    .ifPresent(election -> sscElectionIdByCampus.put(campusId, election.getId()));
        }

        Map<UUID, UUID> departmentElectionIdByVoter = new HashMap<>();
        for (Voter voter : voters) {
            voterDepartmentElectionService.resolveVisibleElection(voter)
                    .ifPresent(election -> departmentElectionIdByVoter.put(voter.getId(), election.getId()));
        }

        Set<UUID> relevantElectionIds = new HashSet<>();
        relevantElectionIds.addAll(sscElectionIdByCampus.values());
        relevantElectionIds.addAll(departmentElectionIdByVoter.values());

        Map<String, VoteLog> voteLogByElectionAndVoter = relevantElectionIds.isEmpty()
                ? Map.of()
                : voteLogRepository.findByElectionIdIn(relevantElectionIds).stream()
                .collect(Collectors.toMap(
                        vl -> vl.getElectionId() + "|" + vl.getVoterId(),
                        vl -> vl,
                        (a, b) -> a
                ));

        return voters.stream()
                .map(voter -> buildVoterResponse(
                        voter, campusNamesById, sscElectionIdByCampus,
                        departmentElectionIdByVoter, voteLogByElectionAndVoter
                ))
                .collect(Collectors.toList());
    }

    public Map<String, Object> toResponse(Voter voter) {

        Map<UUID, String> campusNamesById = campusRepository.findById(voter.getCampusId())
                .map(campus -> Map.of(voter.getCampusId(), campus.getName()))
                .orElse(Map.of());

        Map<UUID, UUID> sscElectionIdByCampus = new HashMap<>();
        electionRepository
                .findByCampusIdAndCategoryAndStatus(voter.getCampusId(), ElectionCategory.SSC, RecordStatus.ACTIVE)
                .stream().findFirst()
                .ifPresent(election -> sscElectionIdByCampus.put(voter.getCampusId(), election.getId()));

        Map<UUID, UUID> departmentElectionIdByVoter = new HashMap<>();
        voterDepartmentElectionService.resolveVisibleElection(voter)
                .ifPresent(election -> departmentElectionIdByVoter.put(voter.getId(), election.getId()));

        Set<UUID> relevantElectionIds = new HashSet<>();
        relevantElectionIds.addAll(sscElectionIdByCampus.values());
        relevantElectionIds.addAll(departmentElectionIdByVoter.values());

        Map<String, VoteLog> voteLogByElectionAndVoter = relevantElectionIds.isEmpty()
                ? Map.of()
                : voteLogRepository.findByElectionIdIn(relevantElectionIds).stream()
                .collect(Collectors.toMap(
                        vl -> vl.getElectionId() + "|" + vl.getVoterId(),
                        vl -> vl,
                        (a, b) -> a
                ));

        return buildVoterResponse(
                voter, campusNamesById, sscElectionIdByCampus,
                departmentElectionIdByVoter, voteLogByElectionAndVoter
        );
    }

    private Map<String, Object> buildVoterResponse(
            Voter voter,
            Map<UUID, String> campusNamesById,
            Map<UUID, UUID> sscElectionIdByCampus,
            Map<UUID, UUID> departmentElectionIdByVoter,
            Map<String, VoteLog> voteLogByElectionAndVoter
    ) {
        String campusName = campusNamesById.getOrDefault(voter.getCampusId(), "Unknown");
        Map<String, Object> response = new HashMap<>();

        response.put("id", voter.getId());
        response.put("studentId", voter.getStudentId());
        response.put("lastName", voter.getLastName());
        response.put("firstName", voter.getFirstName());
        response.put("createdAt", voter.getCreatedAt());
        response.put("updatedAt", voter.getUpdatedAt());
        response.put("middleName", voter.getMiddleName() == null ? "" : voter.getMiddleName());
        response.put("fullName", voter.getFullName());
        response.put("email", voter.getEmail() == null ? "" : voter.getEmail());
        response.put("programCourse", voter.getProgramCourse() == null ? "" : voter.getProgramCourse());
        response.put("yearLevel", voter.getYearLevel() == null ? "" : voter.getYearLevel());
        response.put("section", voter.getSection() == null ? "" : voter.getSection());
        response.put("campusId", voter.getCampusId());
        response.put("campus", campusName);

        UUID sscElectionId = sscElectionIdByCampus.get(voter.getCampusId());
        VoteLog sscVoteLog = sscElectionId != null
                ? voteLogByElectionAndVoter.get(sscElectionId + "|" + voter.getId()) : null;
        response.put("sscVotingStatus", sscVoteLog != null ? "VOTED" : "NOT_VOTED");
        response.put("sscVotedAt", sscVoteLog != null ? sscVoteLog.getVotedAt() : null);

        UUID departmentElectionId = departmentElectionIdByVoter.get(voter.getId());
        VoteLog departmentVoteLog = departmentElectionId != null
                ? voteLogByElectionAndVoter.get(departmentElectionId + "|" + voter.getId()) : null;
        response.put("departmentVotingStatus", departmentVoteLog != null ? "VOTED" : "NOT_VOTED");
        response.put("departmentVotedAt", departmentVoteLog != null ? departmentVoteLog.getVotedAt() : null);

        response.put("status", voter.getStatus().name());
        return response;
    }
}