package lccast.voting.system.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import lccast.voting.system.dto.*;
import lccast.voting.system.model.*;
import lccast.voting.system.repository.*;
import lccast.voting.system.service.superadmin.DepartmentService;
import lccast.voting.system.service.superadmin.PartylistService;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import lccast.voting.system.model.Ballot;
import lccast.voting.system.model.VotingStatus;
import lccast.voting.system.dto.*;
import lccast.voting.system.model.*;
import lccast.voting.system.repository.*;
import lccast.voting.system.service.superadmin.VoterService;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class HistoryService {

    private static final int PAGE_SIZE = 10;

    private final VoteLogRepository voteLogRepository;
    private final CampusRepository campusRepository;
    private final ElectionRepository electionRepository;
    private final AuditLogRepository auditLogRepository;
    private final ArchiveRecordRepository archiveRecordRepository;
    private final TrashRecordRepository trashRecordRepository;
    private final AuditLogService auditLogService;
    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;
    private final SimpMessagingTemplate messagingTemplate;
    private final VoterRepository voterRepository;
    private final PartylistRepository partylistRepository;
    private final PartylistMemberRepository partylistMemberRepository;
    private final DepartmentRepository departmentRepository;
    private final DepartmentMemberRepository departmentMemberRepository;
    private final UserProfileRepository userProfileRepository;
    private final BallotRepository ballotRepository;
    private final BallotVoteRepository ballotVoteRepository;
    private final CandidateRepository candidateRepository;
    private final ElectionPartylistRepository electionPartylistRepository;
    private final ElectionDepartmentRepository electionDepartmentRepository;
    private final VoterService voterService;
    private final CandidateRoleSyncService candidateRoleSyncService;
    private final PartylistService partylistService;
    private final DepartmentService departmentService;


    public HistoryService(
            VoteLogRepository voteLogRepository,
            CampusRepository campusRepository,
            ElectionRepository electionRepository,
            AuditLogRepository auditLogRepository,
            ArchiveRecordRepository archiveRecordRepository,
            TrashRecordRepository trashRecordRepository,
            AuditLogService auditLogService,
            JdbcTemplate jdbcTemplate,
            ObjectMapper objectMapper,
            SimpMessagingTemplate messagingTemplate,
            VoterRepository voterRepository,
            PartylistRepository partylistRepository,
            PartylistMemberRepository partylistMemberRepository,
            DepartmentRepository departmentRepository,
            DepartmentMemberRepository departmentMemberRepository,
            UserProfileRepository userProfileRepository,
            BallotVoteRepository ballotVoteRepository,
            BallotRepository ballotRepository,
            CandidateRepository candidateRepository,
            ElectionPartylistRepository electionPartylistRepository,
            ElectionDepartmentRepository electionDepartmentRepository,
            VoterService voterService,
            CandidateRoleSyncService candidateRoleSyncService,
            PartylistService partylistService,
            DepartmentService departmentService
    ) {
        this.voteLogRepository = voteLogRepository;
        this.campusRepository = campusRepository;
        this.electionRepository = electionRepository;
        this.auditLogRepository = auditLogRepository;
        this.archiveRecordRepository = archiveRecordRepository;
        this.trashRecordRepository = trashRecordRepository;
        this.auditLogService = auditLogService;
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
        this.messagingTemplate = messagingTemplate;
        this.voterRepository = voterRepository;
        this.partylistRepository = partylistRepository;
        this.partylistMemberRepository = partylistMemberRepository;
        this.departmentRepository = departmentRepository;
        this.departmentMemberRepository = departmentMemberRepository;
        this.userProfileRepository = userProfileRepository;
        this.ballotRepository = ballotRepository;
        this.ballotVoteRepository = ballotVoteRepository;
        this.candidateRepository = candidateRepository;
        this.electionPartylistRepository = electionPartylistRepository;
        this.electionDepartmentRepository = electionDepartmentRepository;
        this.voterService = voterService;
        this.candidateRoleSyncService = candidateRoleSyncService;
        this.partylistService = partylistService;
        this.departmentService = departmentService;
    }

    // ======================================================
    // VOTE LOGS
    // ======================================================

    public PageResponse<VoteLogDTO> getVoteLogs(
            String search, String program, String section,
            String year, String category, UUID campusId, String sort, int page, boolean all
    ) {
        Specification<VoteLog> spec = (root, query, cb) -> cb.conjunction();

        if (search != null && !search.isBlank()) {
            String like = "%" + search.toLowerCase() + "%";
            spec = spec.and((root, q, cb) -> cb.or(
                    cb.like(cb.lower(root.get("fullName")), like),
                    cb.like(cb.lower(root.get("studentId")), like),
                    cb.like(cb.lower(root.get("email")), like)
            ));
        }
        if (program != null && !program.isBlank()) {
            spec = spec.and((root, q, cb) -> cb.equal(root.get("programCourse"), program));
        }
        if (section != null && !section.isBlank()) {
            spec = spec.and((root, q, cb) -> cb.equal(root.get("section"), section));
        }
        if (year != null && !year.isBlank()) {
            spec = spec.and((root, q, cb) -> cb.equal(root.get("yearLevel"), year));
        }
        if (category != null && !category.isBlank()) {
            spec = spec.and((root, q, cb) -> cb.equal(cb.upper(root.get("electionCategory")), category.toUpperCase()));
        }
        if (campusId != null) {
            spec = spec.and((root, q, cb) -> cb.equal(root.get("campusId"), campusId));
        }

        Sort sortOrder = switch (sort == null ? "" : sort) {
            case "az" -> Sort.by("fullName").ascending();
            case "za" -> Sort.by("fullName").descending();
            case "time-up" -> Sort.by("votedAt").ascending();
            default -> Sort.by("votedAt").descending(); // "time-down"
        };

        if (all) {
            List<VoteLogDTO> items = voteLogRepository.findAll(spec, sortOrder).stream()
                    .map(this::toVoteLogDTO)
                    .collect(Collectors.toList());
            return new PageResponse<>(items, 1, 1, items.size());
        }

        var pageResult = voteLogRepository.findAll(
                spec, PageRequest.of(Math.max(page - 1, 0), PAGE_SIZE, sortOrder)
        );

        List<VoteLogDTO> items = pageResult.getContent().stream()
                .map(this::toVoteLogDTO)
                .collect(Collectors.toList());

        return new PageResponse<>(items, page, pageResult.getTotalPages(), pageResult.getTotalElements());
    }

    private VoteLogDTO toVoteLogDTO(VoteLog voteLog) {
        VoteLogDTO dto = VoteLogDTO.from(voteLog);

        if (voteLog.getCampusId() != null) {
            campusRepository.findById(voteLog.getCampusId())
                    .ifPresent(campus -> dto.setCampusName(campus.getName()));
        }

        if (voteLog.getElectionId() != null) {
            electionRepository.findById(voteLog.getElectionId())
                    .ifPresent(election -> {
                        dto.setElectionName(election.getTitle());

                        if (election.getCategory() != null) {
                            dto.setElectionCategory(
                                    election.getCategory().name()
                            );
                        }
                    });
        }

        return dto;
    }

    public Map<String, List<String>> getVoteLogFilterOptions() {
        Map<String, List<String>> options = new LinkedHashMap<>();
        options.put("programs", voteLogRepository.findDistinctProgramCourses());
        options.put("sections", voteLogRepository.findDistinctSections());
        options.put("yearLevels", voteLogRepository.findDistinctYearLevels());
        return options;
    }

    // ======================================================
    // ACTIONS (AUDIT LOGS)
    // ======================================================

    public PageResponse<AuditLogDTO> getActions(
            String search, UserRole role, AuditAction action, int page, boolean all
    ) {
        Specification<AuditLog> spec = (root, query, cb) -> cb.conjunction();

        if (search != null && !search.isBlank()) {
            String like = "%" + search.toLowerCase() + "%";
            spec = spec.and((root, q, cb) -> cb.like(cb.lower(root.get("description")), like));
        }
        if (role != null) {
            spec = spec.and((root, q, cb) -> cb.equal(root.get("role"), role));
        }
        if (action != null) {
            spec = spec.and((root, q, cb) -> cb.equal(root.get("action"), action));
        }

        if (all) {
            List<AuditLogDTO> items = auditLogRepository.findAll(spec, Sort.by("createdAt").descending()).stream()
                    .map(log -> AuditLogDTO.from(log, auditLogService.getUserName(log.getUserId()), objectMapper))
                    .collect(Collectors.toList());
            return new PageResponse<>(items, 1, 1, items.size());
        }

        var pageResult = auditLogRepository.findAll(
                spec, PageRequest.of(Math.max(page - 1, 0), PAGE_SIZE, Sort.by("createdAt").descending())
        );

        List<AuditLogDTO> items = pageResult.getContent().stream()
                .map(log -> AuditLogDTO.from(log, auditLogService.getUserName(log.getUserId()), objectMapper))
                .collect(Collectors.toList());

        return new PageResponse<>(items, page, pageResult.getTotalPages(), pageResult.getTotalElements());
    }

    // ======================================================
    // ARCHIVES
    // ======================================================

    public PageResponse<ArchiveRecordDTO> getArchives(String search, String entityType, int page) {
        Specification<ArchiveRecord> spec = (root, q, cb) -> cb.isFalse(root.get("restored"));

        if (search != null && !search.isBlank()) {
            String like = "%" + search.toLowerCase() + "%";
            spec = spec.and((root, q, cb) -> cb.like(cb.lower(root.get("entityName")), like));
        }
        if (entityType != null && !entityType.isBlank()) {
            spec = spec.and((root, q, cb) -> cb.equal(root.get("entityType"), entityType));
        }

        var pageResult = archiveRecordRepository.findAll(
                spec, PageRequest.of(Math.max(page - 1, 0), PAGE_SIZE, Sort.by("archivedAt").descending())
        );

        List<ArchiveRecordDTO> items = pageResult.getContent().stream()
                .map(rec -> ArchiveRecordDTO.from(rec, auditLogService.getUserName(rec.getArchivedBy())))
                .collect(Collectors.toList());

        return new PageResponse<>(items, page, pageResult.getTotalPages(), pageResult.getTotalElements());
    }

    @Transactional
    public void restoreArchive(UUID id, HttpServletRequest request) {
        ArchiveRecord record = archiveRecordRepository.findById(id)
                .orElseThrow(() -> new NoSuchElementException("Archive record not found: " + id));

        if (record.isRestored()) {
            throw new IllegalStateException("Record already restored.");
        }

        reactivateEntity(record.getEntityType(), record.getEntityId(), record.getData());

        record.setRestored(true);
        record.setRestoredAt(Instant.now());
        record.setRestoredBy(currentUserId(request));
        archiveRecordRepository.save(record);

        auditLogService.log(
                request, AuditAction.RESTORE, record.getEntityType(), record.getEntityId(),
                "restored archived " + record.getEntityType() + " \"" + record.getEntityName() + "\"",
                Map.of("source", "archive")
        );

        messagingTemplate.convertAndSend("/topic/history/archives",
                HistoryEventDTO.of("restored", id.toString()));
    }

    // ======================================================
    // TRASH
    // ======================================================

    public PageResponse<TrashRecordDTO> getTrash(String search, String entityType, int page) {
        Specification<TrashRecord> spec = (root, q, cb) -> cb.isFalse(root.get("restored"));

        if (search != null && !search.isBlank()) {
            String like = "%" + search.toLowerCase() + "%";
            spec = spec.and((root, q, cb) -> cb.like(cb.lower(root.get("entityName")), like));
        }
        if (entityType != null && !entityType.isBlank()) {
            spec = spec.and((root, q, cb) -> cb.equal(root.get("entityType"), entityType));
        }

        var pageResult = trashRecordRepository.findAll(
                spec, PageRequest.of(Math.max(page - 1, 0), PAGE_SIZE, Sort.by("deletedAt").descending())
        );

        List<TrashRecordDTO> items = pageResult.getContent().stream()
                .map(rec -> TrashRecordDTO.from(rec, auditLogService.getUserName(rec.getDeletedBy())))
                .collect(Collectors.toList());

        return new PageResponse<>(items, page, pageResult.getTotalPages(), pageResult.getTotalElements());
    }

    @Transactional
    public void restoreTrash(UUID id, HttpServletRequest request) {
        TrashRecord record = trashRecordRepository.findById(id)
                .orElseThrow(() -> new NoSuchElementException("Trash record not found: " + id));

        if (record.isRestored()) {
            throw new IllegalStateException("Record already restored.");
        }

        reactivateEntity(record.getEntityType(), record.getEntityId(), record.getData());

        record.setRestored(true);
        record.setRestoredAt(Instant.now());
        record.setRestoredBy(currentUserId(request));
        trashRecordRepository.save(record);

        auditLogService.log(
                request, AuditAction.RESTORE, record.getEntityType(), record.getEntityId(),
                "restored " + record.getEntityType() + " \"" + record.getEntityName() + "\" from trash",
                Map.of("source", "trash")
        );

        messagingTemplate.convertAndSend("/topic/history/trash",
                HistoryEventDTO.of("restored", id.toString()));
    }

    @Transactional
    public void deleteTrashPermanently(UUID id, HttpServletRequest request) {
        TrashRecord record = trashRecordRepository.findById(id)
                .orElseThrow(() -> new NoSuchElementException("Trash record not found: " + id));

        hardDeleteEntity(record.getEntityType(), record.getEntityId());

        trashRecordRepository.delete(record);

        auditLogService.log(
                request, AuditAction.DELETE, record.getEntityType(), record.getEntityId(),
                "permanently deleted " + record.getEntityType() + " \"" + record.getEntityName() + "\"",
                Map.of("permanent", true)
        );

        messagingTemplate.convertAndSend("/topic/history/trash",
                HistoryEventDTO.of("deleted", id.toString()));
    }

    @Transactional
    public void emptyTrash(HttpServletRequest request) {
        List<TrashRecord> all = trashRecordRepository.findAll().stream()
                .filter(r -> !r.isRestored())
                .toList();

        int count = all.size();

        all.forEach(record -> hardDeleteEntity(record.getEntityType(), record.getEntityId()));

        trashRecordRepository.deleteAll(all);

        auditLogService.log(
                request, AuditAction.DELETE, "Trash", null,
                "emptied trash (" + count + " records permanently deleted)",
                Map.of("count", count)
        );

        messagingTemplate.convertAndSend("/topic/history/trash",
                HistoryEventDTO.ofCount("emptied", count));
    }

    // ======================================================
    // HELPERS
    // ======================================================

    private UUID currentUserId(HttpServletRequest request) {
        var session = request.getSession(false);
        if (session == null) return null;
        Object userId = session.getAttribute("userId");
        if (userId == null) return null;
        try {
            return UUID.fromString(userId.toString());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    /**
     * Reinserts a JSON-serialized record back into its origin table.
     * entity_type values must map 1:1 to table names below —
     * extend this map as new archivable/trashable entity types are added.
     */
    private static final Map<String, String> ENTITY_TYPE_TO_TABLE = Map.of(
            "Elections", "elections",
            "Partylists", "partylists",
            "Departments", "departments",
            "Students", "voters",
            "Candidates", "candidates"
    );

    private void reactivateEntity(String entityType, UUID entityId, String jsonData) {
        switch (entityType) {
            case "Students" -> {
                Voter voter = voterRepository.findById(entityId)
                        .orElseThrow(() -> new NoSuchElementException("Voter not found: " + entityId));
                voter.setStatus(RecordStatus.ACTIVE);
                voter.setUpdatedAt(Instant.now());
                voterRepository.save(voter);
                voterService.enableVoterAccount(voter);
            }
            case "Partylists" -> restorePartylist(entityId);

            case "Departments" -> restoreDepartment(entityId);

            case "Admins" -> {
                UserProfile profile = userProfileRepository.findById(entityId)
                        .orElseThrow(() -> new NoSuchElementException("Admin not found: " + entityId));
                profile.setStatus(RecordStatus.ACTIVE);
                profile.setActive(true);
                userProfileRepository.save(profile);
            }

            case "Elections" -> {
                Election election = electionRepository.findById(entityId)
                        .orElseThrow(() -> new NoSuchElementException("Election not found: " + entityId));
                election.setStatus(RecordStatus.ACTIVE);
                election.setActive(true);
                electionRepository.save(election);
            }

            default -> reinsertEntity(entityType, entityId, jsonData);
        }
    }

    private void restorePartylist(UUID id) {
        Partylist partylist = partylistRepository.findById(id)
                .orElseThrow(() -> new NoSuchElementException("Partylist not found: " + id));

        if (partylist.getStatus() != RecordStatus.DELETED &&
                partylist.getStatus() != RecordStatus.ARCHIVED) {
            throw new IllegalStateException("Partylist cannot be restored.");
        }

        if (partylistRepository.existsByNameIgnoreCaseAndStatusAndIdNot(
                partylist.getName().trim(), RecordStatus.ACTIVE, id)) {
            throw new IllegalArgumentException(
                    "A partylist with the same name already exists.");
        }

        List<PartylistMember> members = partylistMemberRepository.findByPartylistId(id);

        partylistService.validateMembers(
                id, partylist.getCampus().getId(), partylist.getSchoolYear(), members);

        partylist.setStatus(RecordStatus.ACTIVE);
        partylistRepository.save(partylist);
    }

    private void restoreDepartment(UUID id) {
        Department department = departmentRepository.findById(id)
                .orElseThrow(() -> new NoSuchElementException("Department not found: " + id));

        if (department.getStatus() != RecordStatus.DELETED &&
                department.getStatus() != RecordStatus.ARCHIVED) {
            throw new IllegalStateException("Department cannot be restored.");
        }

        departmentService.assertMembersRestorable(department);

        department.setStatus(RecordStatus.ACTIVE);
        departmentRepository.save(department);
    }

    /**
     * Permanently removes the real row for entity types using the status-flip
     * model, cascading to member tables where needed. Other entity types
     * (Elections, Candidates) were already hard-deleted at trash time under
     * the legacy model, so deleting the TrashRecord alone is sufficient.
     */
    private void hardDeleteEntity(String entityType, UUID entityId) {
        switch (entityType) {
            case "Students" -> voterRepository.findById(entityId)
                    .ifPresent(voterRepository::delete);
            case "Partylists" -> {
                partylistMemberRepository.deleteByPartylistId(entityId);
                partylistRepository.findById(entityId)
                        .ifPresent(partylistRepository::delete);
            }
            case "Departments" -> {
                departmentMemberRepository.deleteByDepartmentId(entityId);
                departmentRepository.findById(entityId)
                        .ifPresent(departmentRepository::delete);
            }

            case "Elections" -> {
                List<Ballot> ballots = ballotRepository.findByElection_IdOrderBySubmittedAtAsc(entityId);

                Set<UUID> affectedVoterIds = ballots.stream()
                        .map(ballot -> ballot.getVoter().getId())
                        .collect(Collectors.toSet());

                List<UUID> ballotIds = ballots.stream()
                        .map(Ballot::getId)
                        .toList();

                List<String> candidateStudentIds = candidateRepository.findByElectionId(entityId).stream()
                        .map(Candidate::getStudentId)
                        .toList();

                if (!ballotIds.isEmpty()) {
                    ballotVoteRepository.deleteByBallot_IdIn(ballotIds);
                }
                voteLogRepository.deleteByElectionId(entityId);
                ballotRepository.deleteByElection_Id(entityId);
                candidateRepository.deleteByElectionId(entityId);
                electionPartylistRepository.deleteByElectionId(entityId);
                electionDepartmentRepository.deleteByElectionId(entityId);

                for (UUID voterId : affectedVoterIds) {
                    boolean hasOtherBallots = ballotRepository.existsByVoter_Id(voterId);

                    if (!hasOtherBallots) {
                        voterRepository.findById(voterId).ifPresent(voter -> {
                            voter.setVotingStatus(VotingStatus.NOT_VOTED);
                            voter.setTimeVoted(null);
                            voter.setUpdatedAt(Instant.now());
                            voterRepository.save(voter);
                        });
                    }
                }

                electionRepository.findById(entityId).ifPresent(electionRepository::delete);
                candidateStudentIds.forEach(candidateRoleSyncService::demoteToVoterIfNoLongerCandidate);
            }

            case "Admins" -> userProfileRepository.findById(entityId)
                    .ifPresent(userProfileRepository::delete);
            default -> { /* legacy entity types: row already removed at trash time */ }
        }
    }

    private void reinsertEntity(String entityType, UUID entityId, String jsonData) {
        String table = ENTITY_TYPE_TO_TABLE.get(entityType);
        if (table == null) {
            throw new IllegalStateException("No restore mapping for entity type: " + entityType);
        }

        JsonNode node;
        try {
            node = objectMapper.readTree(jsonData);
        } catch (Exception e) {
            throw new RuntimeException("Failed to parse archived data for restore.", e);
        }

        List<String> columns = new ArrayList<>();
        List<Object> values = new ArrayList<>();
        node.fields().forEachRemaining(entry -> {
            columns.add(camelToSnake(entry.getKey()));
            values.add(jsonValueToSqlParam(entry.getValue()));
        });

        String columnList = String.join(", ", columns);
        String placeholders = String.join(", ", Collections.nCopies(columns.size(), "?"));

        String sql = "INSERT INTO " + table + " (" + columnList + ") VALUES (" + placeholders + ") " +
                "ON CONFLICT (id) DO NOTHING";

        jdbcTemplate.update(sql, values.toArray());
    }

    private String camelToSnake(String camel) {
        return camel.replaceAll("([a-z])([A-Z])", "$1_$2").toLowerCase();
    }

    private Object jsonValueToSqlParam(JsonNode value) {
        if (value.isNull()) return null;
        if (value.isTextual()) return value.asText();
        if (value.isBoolean()) return value.asBoolean();
        if (value.isInt()) return value.asInt();
        if (value.isLong()) return value.asLong();
        if (value.isDouble()) return value.asDouble();
        return value.toString(); // objects/arrays -> raw JSON string (for jsonb columns)
    }

    // ======================================================
// ADMIN-DEPT SCOPED VOTE LOGS
// ======================================================

    public PageResponse<VoteLogDTO> getVoteLogsForAdminDept(
            UUID campusId, String programCourse,
            String search, String section, String year, String sort, int page, boolean all
    ) {
        Specification<VoteLog> spec = (root, query, cb) -> cb.equal(root.get("campusId"), campusId);

        spec = spec.and((root, q, cb) -> cb.equal(cb.upper(root.get("programCourse")), programCourse.toUpperCase()));
        spec = spec.and((root, q, cb) -> cb.equal(cb.upper(root.get("electionCategory")), "DEPARTMENT"));

        if (search != null && !search.isBlank()) {
            String like = "%" + search.toLowerCase() + "%";
            spec = spec.and((root, q, cb) -> cb.or(
                    cb.like(cb.lower(root.get("fullName")), like),
                    cb.like(cb.lower(root.get("studentId")), like),
                    cb.like(cb.lower(root.get("email")), like)
            ));
        }
        if (section != null && !section.isBlank()) {
            spec = spec.and((root, q, cb) -> cb.equal(root.get("section"), section));
        }
        if (year != null && !year.isBlank()) {
            spec = spec.and((root, q, cb) -> cb.equal(root.get("yearLevel"), year));
        }

        Sort sortOrder = switch (sort == null ? "" : sort) {
            case "az" -> Sort.by("fullName").ascending();
            case "za" -> Sort.by("fullName").descending();
            case "time-up" -> Sort.by("votedAt").ascending();
            default -> Sort.by("votedAt").descending();
        };

        if (all) {
            List<VoteLogDTO> items = voteLogRepository.findAll(spec, sortOrder).stream()
                    .map(this::toVoteLogDTO)
                    .collect(Collectors.toList());
            return new PageResponse<>(items, 1, 1, items.size());
        }

        var pageResult = voteLogRepository.findAll(
                spec, PageRequest.of(Math.max(page - 1, 0), PAGE_SIZE, sortOrder)
        );

        List<VoteLogDTO> items = pageResult.getContent().stream()
                .map(this::toVoteLogDTO)
                .collect(Collectors.toList());

        return new PageResponse<>(items, page, pageResult.getTotalPages(), pageResult.getTotalElements());
    }

    public Map<String, List<String>> getVoteLogFilterOptionsForAdminDept(UUID campusId, String programCourse) {
        Map<String, List<String>> options = new LinkedHashMap<>();
        options.put("sections", voteLogRepository.findDistinctSectionsByCampusAndProgram(campusId, programCourse));
        options.put("yearLevels", voteLogRepository.findDistinctYearLevelsByCampusAndProgram(campusId, programCourse));
        return options;
    }

// ======================================================
// ADMIN-DEPT SCOPED ACTIONS
// ======================================================

    public PageResponse<AuditLogDTO> getActionsForAdminDept(
            UUID userId, String search, AuditAction action, int page, boolean all
    ) {
        Specification<AuditLog> spec = (root, query, cb) -> cb.equal(root.get("userId"), userId);

        if (search != null && !search.isBlank()) {
            String like = "%" + search.toLowerCase() + "%";
            spec = spec.and((root, q, cb) -> cb.like(cb.lower(root.get("description")), like));
        }
        if (action != null) {
            spec = spec.and((root, q, cb) -> cb.equal(root.get("action"), action));
        }

        if (all) {
            List<AuditLogDTO> items = auditLogRepository.findAll(spec, Sort.by("createdAt").descending()).stream()
                    .map(log -> AuditLogDTO.from(log, auditLogService.getUserName(log.getUserId()), objectMapper))
                    .collect(Collectors.toList());
            return new PageResponse<>(items, 1, 1, items.size());
        }

        var pageResult = auditLogRepository.findAll(
                spec, PageRequest.of(Math.max(page - 1, 0), PAGE_SIZE, Sort.by("createdAt").descending())
        );

        List<AuditLogDTO> items = pageResult.getContent().stream()
                .map(log -> AuditLogDTO.from(log, auditLogService.getUserName(log.getUserId()), objectMapper))
                .collect(Collectors.toList());

        return new PageResponse<>(items, page, pageResult.getTotalPages(), pageResult.getTotalElements());
    }

// ======================================================
// ADMIN-DEPT SCOPED ARCHIVES
// ======================================================

    public PageResponse<ArchiveRecordDTO> getArchivesForAdminDept(UUID userId, String search, String entityType, int page) {
        Specification<ArchiveRecord> spec = (root, q, cb) -> cb.and(
                cb.isFalse(root.get("restored")),
                cb.equal(root.get("archivedBy"), userId)
        );

        if (search != null && !search.isBlank()) {
            String like = "%" + search.toLowerCase() + "%";
            spec = spec.and((root, q, cb) -> cb.like(cb.lower(root.get("entityName")), like));
        }
        if (entityType != null && !entityType.isBlank()) {
            spec = spec.and((root, q, cb) -> cb.equal(root.get("entityType"), entityType));
        }

        var pageResult = archiveRecordRepository.findAll(
                spec, PageRequest.of(Math.max(page - 1, 0), PAGE_SIZE, Sort.by("archivedAt").descending())
        );

        List<ArchiveRecordDTO> items = pageResult.getContent().stream()
                .map(rec -> ArchiveRecordDTO.from(rec, auditLogService.getUserName(rec.getArchivedBy())))
                .collect(Collectors.toList());

        return new PageResponse<>(items, page, pageResult.getTotalPages(), pageResult.getTotalElements());
    }

    @Transactional
    public void restoreArchiveForAdminDept(UUID id, UUID userId, HttpServletRequest request) {
        ArchiveRecord record = archiveRecordRepository.findById(id)
                .orElseThrow(() -> new NoSuchElementException("Archive record not found: " + id));

        if (!userId.equals(record.getArchivedBy())) {
            throw new IllegalArgumentException("You are not authorized to restore this record.");
        }

        restoreArchive(id, request);
    }

// ======================================================
// ADMIN-DEPT SCOPED TRASH
// ======================================================

    public PageResponse<TrashRecordDTO> getTrashForAdminDept(UUID userId, String search, String entityType, int page) {
        Specification<TrashRecord> spec = (root, q, cb) -> cb.and(
                cb.isFalse(root.get("restored")),
                cb.equal(root.get("deletedBy"), userId)
        );

        if (search != null && !search.isBlank()) {
            String like = "%" + search.toLowerCase() + "%";
            spec = spec.and((root, q, cb) -> cb.like(cb.lower(root.get("entityName")), like));
        }
        if (entityType != null && !entityType.isBlank()) {
            spec = spec.and((root, q, cb) -> cb.equal(root.get("entityType"), entityType));
        }

        var pageResult = trashRecordRepository.findAll(
                spec, PageRequest.of(Math.max(page - 1, 0), PAGE_SIZE, Sort.by("deletedAt").descending())
        );

        List<TrashRecordDTO> items = pageResult.getContent().stream()
                .map(rec -> TrashRecordDTO.from(rec, auditLogService.getUserName(rec.getDeletedBy())))
                .collect(Collectors.toList());

        return new PageResponse<>(items, page, pageResult.getTotalPages(), pageResult.getTotalElements());
    }

    @Transactional
    public void restoreTrashForAdminDept(UUID id, UUID userId, HttpServletRequest request) {
        TrashRecord record = trashRecordRepository.findById(id)
                .orElseThrow(() -> new NoSuchElementException("Trash record not found: " + id));

        if (!userId.equals(record.getDeletedBy())) {
            throw new IllegalArgumentException("You are not authorized to restore this record.");
        }

        restoreTrash(id, request);
    }

    @Transactional
    public void deleteTrashPermanentlyForAdminDept(UUID id, UUID userId, HttpServletRequest request) {
        TrashRecord record = trashRecordRepository.findById(id)
                .orElseThrow(() -> new NoSuchElementException("Trash record not found: " + id));

        if (!userId.equals(record.getDeletedBy())) {
            throw new IllegalArgumentException("You are not authorized to delete this record.");
        }

        deleteTrashPermanently(id, request);
    }

    @Transactional
    public void emptyTrashForAdminDept(UUID userId, HttpServletRequest request) {
        List<TrashRecord> owned = trashRecordRepository.findAll().stream()
                .filter(r -> !r.isRestored() && userId.equals(r.getDeletedBy()))
                .toList();

        int count = owned.size();

        owned.forEach(record -> hardDeleteEntity(record.getEntityType(), record.getEntityId()));

        trashRecordRepository.deleteAll(owned);

        auditLogService.log(
                request, AuditAction.DELETE, "Trash", null,
                "emptied own trash (" + count + " records permanently deleted)",
                Map.of("count", count)
        );

        messagingTemplate.convertAndSend("/topic/history/trash",
                HistoryEventDTO.ofCount("emptied", count));
    }

    // ======================================================
// ADMIN-SSC SCOPED VOTE LOGS
// ======================================================

    /**
     * Vote logs for an admin-ssc account: always restricted to the campus
     * registered on that account. campusId is supplied by the controller from
     * the session, never from the request.
     */
    public PageResponse<VoteLogDTO> getVoteLogsForAdminSsc(
            UUID campusId,
            String search, String program, String section, String year,
            String category, String sort, int page, boolean all
    ) {
        Specification<VoteLog> spec = (root, query, cb) -> cb.equal(root.get("campusId"), campusId);

        if (search != null && !search.isBlank()) {
            String like = "%" + search.toLowerCase() + "%";
            spec = spec.and((root, q, cb) -> cb.or(
                    cb.like(cb.lower(root.get("fullName")), like),
                    cb.like(cb.lower(root.get("studentId")), like),
                    cb.like(cb.lower(root.get("email")), like)
            ));
        }
        if (program != null && !program.isBlank()) {
            spec = spec.and((root, q, cb) -> cb.equal(root.get("programCourse"), program));
        }
        if (section != null && !section.isBlank()) {
            spec = spec.and((root, q, cb) -> cb.equal(root.get("section"), section));
        }
        if (year != null && !year.isBlank()) {
            spec = spec.and((root, q, cb) -> cb.equal(root.get("yearLevel"), year));
        }
        if (category != null && !category.isBlank()) {
            spec = spec.and((root, q, cb) ->
                    cb.equal(cb.upper(root.get("electionCategory")), category.toUpperCase()));
        }

        Sort sortOrder = switch (sort == null ? "" : sort) {
            case "az" -> Sort.by("fullName").ascending();
            case "za" -> Sort.by("fullName").descending();
            case "time-up" -> Sort.by("votedAt").ascending();
            default -> Sort.by("votedAt").descending(); // "time-down"
        };

        if (all) {
            List<VoteLogDTO> items = voteLogRepository.findAll(spec, sortOrder).stream()
                    .map(this::toVoteLogDTO)
                    .collect(Collectors.toList());
            return new PageResponse<>(items, 1, 1, items.size());
        }

        var pageResult = voteLogRepository.findAll(
                spec, PageRequest.of(Math.max(page - 1, 0), PAGE_SIZE, sortOrder)
        );

        List<VoteLogDTO> items = pageResult.getContent().stream()
                .map(this::toVoteLogDTO)
                .collect(Collectors.toList());

        return new PageResponse<>(items, page, pageResult.getTotalPages(), pageResult.getTotalElements());
    }

    /** Dropdown options built only from the campus this account belongs to. */
    public Map<String, List<String>> getVoteLogFilterOptionsForAdminSsc(UUID campusId) {
        Map<String, List<String>> options = new LinkedHashMap<>();
        options.put("programs", voteLogRepository.findDistinctProgramCoursesByCampus(campusId));
        options.put("sections", voteLogRepository.findDistinctSectionsByCampus(campusId));
        options.put("yearLevels", voteLogRepository.findDistinctYearLevelsByCampus(campusId));
        return options;
    }

// ======================================================
// ADMIN-SSC SCOPED ACTIONS
// ======================================================

    /**
     * Audit entries created by this account only. The role filter is still
     * honoured so the Actions tab keeps working as-is (and the Role column
     * stays populated), but it can never widen the user scope.
     */
    public PageResponse<AuditLogDTO> getActionsForAdminSsc(
            UUID userId, String search, UserRole role, AuditAction action, int page, boolean all
    ) {
        Specification<AuditLog> spec = (root, query, cb) -> cb.equal(root.get("userId"), userId);

        if (search != null && !search.isBlank()) {
            String like = "%" + search.toLowerCase() + "%";
            spec = spec.and((root, q, cb) -> cb.like(cb.lower(root.get("description")), like));
        }
        if (role != null) {
            spec = spec.and((root, q, cb) -> cb.equal(root.get("role"), role));
        }
        if (action != null) {
            spec = spec.and((root, q, cb) -> cb.equal(root.get("action"), action));
        }

        if (all) {
            List<AuditLogDTO> items = auditLogRepository.findAll(spec, Sort.by("createdAt").descending()).stream()
                    .map(log -> AuditLogDTO.from(log, auditLogService.getUserName(log.getUserId()), objectMapper))
                    .collect(Collectors.toList());
            return new PageResponse<>(items, 1, 1, items.size());
        }

        var pageResult = auditLogRepository.findAll(
                spec, PageRequest.of(Math.max(page - 1, 0), PAGE_SIZE, Sort.by("createdAt").descending())
        );

        List<AuditLogDTO> items = pageResult.getContent().stream()
                .map(log -> AuditLogDTO.from(log, auditLogService.getUserName(log.getUserId()), objectMapper))
                .collect(Collectors.toList());

        return new PageResponse<>(items, page, pageResult.getTotalPages(), pageResult.getTotalElements());
    }

// ======================================================
// ADMIN-SSC SCOPED ARCHIVES / TRASH
//
// Ownership scoping is identical to the admin-dept variants
// (own records only), so these delegate rather than duplicate.
// ======================================================

    public PageResponse<ArchiveRecordDTO> getArchivesForAdminSsc(
            UUID userId, String search, String entityType, int page
    ) {
        return getArchivesForAdminDept(userId, search, entityType, page);
    }

    @Transactional
    public void restoreArchiveForAdminSsc(UUID id, UUID userId, HttpServletRequest request) {
        restoreArchiveForAdminDept(id, userId, request);
    }

    public PageResponse<TrashRecordDTO> getTrashForAdminSsc(
            UUID userId, String search, String entityType, int page
    ) {
        return getTrashForAdminDept(userId, search, entityType, page);
    }

    @Transactional
    public void restoreTrashForAdminSsc(UUID id, UUID userId, HttpServletRequest request) {
        restoreTrashForAdminDept(id, userId, request);
    }

    @Transactional
    public void deleteTrashPermanentlyForAdminSsc(UUID id, UUID userId, HttpServletRequest request) {
        deleteTrashPermanentlyForAdminDept(id, userId, request);
    }

    @Transactional
    public void emptyTrashForAdminSsc(UUID userId, HttpServletRequest request) {
        emptyTrashForAdminDept(userId, request);
    }
}