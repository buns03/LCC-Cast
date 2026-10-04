package lccast.voting.system.service;

import lccast.voting.system.dto.analytics.AnalyticsDTO.*;
import lccast.voting.system.model.*;
import lccast.voting.system.repository.*;
import lccast.voting.system.util.DepartmentUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Service
@Transactional(readOnly = true)
public class AnalyticsService {

    private final CampusRepository campusRepository;
    private final DepartmentRepository departmentRepository;
    private final ElectionRepository electionRepository;
    private final CandidateRepository candidateRepository;
    private final PartylistRepository partylistRepository;
    private final VoterRepository voterRepository;
    private final BallotRepository ballotRepository;
    private final BallotVoteRepository ballotVoteRepository;
    private final ElectionDepartmentRepository electionDepartmentRepository;
    private final VoteLogRepository voteLogRepository;   // ADD
    private final DrawDetectionService drawDetectionService;

    public AnalyticsService(
            CampusRepository campusRepository,
            DepartmentRepository departmentRepository,
            ElectionRepository electionRepository,
            CandidateRepository candidateRepository,
            PartylistRepository partylistRepository,
            VoterRepository voterRepository,
            BallotRepository ballotRepository,
            BallotVoteRepository ballotVoteRepository,
            ElectionDepartmentRepository electionDepartmentRepository,
            VoteLogRepository voteLogRepository,
            DrawDetectionService drawDetectionService) {   // ADD
        this.campusRepository = campusRepository;
        this.departmentRepository = departmentRepository;
        this.electionRepository = electionRepository;
        this.candidateRepository = candidateRepository;
        this.partylistRepository = partylistRepository;
        this.voterRepository = voterRepository;
        this.ballotRepository = ballotRepository;
        this.ballotVoteRepository = ballotVoteRepository;
        this.electionDepartmentRepository = electionDepartmentRepository;
        this.voteLogRepository = voteLogRepository;   // ADD
        this.drawDetectionService = drawDetectionService;
    }

    public FullAnalytics getFullAnalytics() {
        FullAnalytics full = new FullAnalytics();
        full.ssc = buildSSCAnalytics();
        full.departments = buildDepartmentAnalytics();
        return full;
    }

    public AdminDeptAnalyticsResponse getAdminDeptAnalytics(UUID campusId, String departmentCode) {

        AdminDeptAnalyticsResponse response = new AdminDeptAnalyticsResponse();

        if (campusId == null || departmentCode == null) {
            return response;
        }

        Campus campus = campusRepository.findById(campusId).orElse(null);
        if (campus == null) {
            return response;
        }

        // Resolve the actual Department row for this campus by matching its
        // derived program code, not its raw name (mirrors AdminElectionService).
        Department department = departmentRepository
                .findByCampusIdAndStatus(campusId, RecordStatus.ACTIVE)
                .stream()
                .filter(d -> departmentCode.equalsIgnoreCase(DepartmentUtils.extractProgramCode(d)))
                .max(Comparator.comparing(Department::getCreatedAt))
                .orElse(null);

        if (department == null) {
            return response; // department not configured for this campus yet
        }

        response.departmentId = department.getId();
        response.departmentName = department.getName();
        response.departmentTitle = department.getTitle();
        response.votingType = department.getVotingType() != null ? department.getVotingType().name() : null;

        // Every ACTIVE election of this program code (all school years), newest first.
// Matched by program code, not by one Department row, because each school
// year has its own department row.
        List<Election> elections = orderNewestFirst(electionRepository
                .findByCampusIdAndCategoryAndStatus(campusId, ElectionCategory.DEPARTMENT, RecordStatus.ACTIVE)
                .stream()
                .filter(e -> e.getElectionDepartments().stream().anyMatch(ed ->
                        departmentCode.equalsIgnoreCase(DepartmentUtils.extractProgramCode(ed.getDepartment()))))
                .toList());

        if (elections.isEmpty()) {
            response.hasActiveElection = false;
            return response;
        }

        response.hasActiveElection = true;

        List<CampusAnalytics> history = elections.stream()
                .map(election -> buildDeptElectionAnalytics(campus, election, departmentCode))
                .toList();

        response.analytics = history.get(primaryIndex(elections));
        response.history = history;            // all school years

        return response;
    }

    private List<Campus> activeCampuses() {
        return campusRepository.findByStatus(RecordStatus.ACTIVE);
    }

    // ============ SSC ============

    private List<Election> sscElectionsNewestFirst(UUID campusId) {
        return orderNewestFirst(electionRepository
                .findByCampusIdAndCategoryAndStatus(campusId, ElectionCategory.SSC, RecordStatus.ACTIVE));
    }

    private CampusAnalytics buildSSCElectionAnalytics(Campus campus, Election election) {
        CampusAnalytics ca = buildCampusAnalytics(campus, election, null);
        ca.partyLists = buildPartylistVotes(election);
        ca.programVotes = buildProgramVotes(election.getId());
        ca.programNotVoted = buildProgramNotVoted(campus.getId(), election.getId());
        return ca;
    }

    private CampusAnalytics emptySscAnalytics(Campus campus) {
        CampusAnalytics ca = new CampusAnalytics();
        ca.campus = campus.getName();
        ca.candidates = List.of();
        ca.partyLists = List.of();
        ca.programVotes = List.of();
        ca.programNotVoted = List.of();
        ca.yearLevel = new LabeledSeries(List.of(), List.of());
        ca.activity = new LabeledSeries(List.of(), List.of());
        return ca;
    }

    private SSCAnalytics buildSSCAnalytics() {
        SSCAnalytics ssc = new SSCAnalytics();
        ssc.campuses = new LinkedHashMap<>();
        ssc.elections = new LinkedHashMap<>();

        for (Campus campus : activeCampuses()) {
            String key = campusKey(campus);
            List<Election> elections = sscElectionsNewestFirst(campus.getId());

            if (elections.isEmpty()) {
                ssc.campuses.put(key, emptySscAnalytics(campus));
                continue;
            }

            int primary = primaryIndex(elections);

            for (int i = 0; i < elections.size(); i++) {
                Election election = elections.get(i);
                CampusAnalytics ca = buildSSCElectionAnalytics(campus, election);

                if (i == primary) ssc.campuses.put(key, ca);   // original stays the "latest" slot

                ssc.elections
                        .computeIfAbsent(election.getId().toString(), k -> new LinkedHashMap<>())
                        .put(key, ca);
            }
        }
        return ssc;
    }

    /** Latest SSC election for the campus (unchanged signature for admin-ssc). */
    public CampusAnalytics getSscAnalyticsForCampus(UUID campusId) {
        Campus campus = campusRepository.findById(campusId)
                .orElseThrow(() -> new RuntimeException("Campus not found."));

        List<Election> elections = sscElectionsNewestFirst(campusId);

        return elections.isEmpty()
                ? emptySscAnalytics(campus)
                : buildSSCElectionAnalytics(campus, elections.get(primaryIndex(elections)));
    }

    /** Every SSC election of the campus (all school years), newest first. */
    public List<CampusAnalytics> getSscAnalyticsHistoryForCampus(UUID campusId) {
        Campus campus = campusRepository.findById(campusId)
                .orElseThrow(() -> new RuntimeException("Campus not found."));

        return sscElectionsNewestFirst(campusId).stream()
                .map(election -> buildSSCElectionAnalytics(campus, election))
                .toList();
    }

    // ============ DEPARTMENT ============

    private CampusAnalytics buildDeptElectionAnalytics(Campus campus, Election election, String departmentCode) {

        // This election's departments that belong to the admin's program code
        List<Department> depts = election.getElectionDepartments().stream()
                .map(ElectionDepartment::getDepartment)
                .filter(d -> departmentCode.equalsIgnoreCase(DepartmentUtils.extractProgramCode(d)))
                .toList();

        CampusAnalytics ca = buildCampusAnalytics(campus, election, depts);
        ca.status = new LabeledSeries(
                List.of("Voted", "Not Yet Voted"),
                List.of(ca.votesCast, Math.max(ca.totalVoters - ca.votesCast, 0))
        );

        VotingType votingType = depts.isEmpty() ? null : depts.get(0).getVotingType();
        ca.votingType = votingType != null ? votingType.name() : null;

        return ca;
    }

    private DepartmentAnalytics buildDepartmentAnalytics() {
        DepartmentAnalytics da = new DepartmentAnalytics();
        da.departments = new LinkedHashMap<>();

        List<Election> departmentElections = electionRepository
                .findByCategoryAndStatus(ElectionCategory.DEPARTMENT, RecordStatus.ACTIVE);

        for (Election election : departmentElections) {
            Campus campus = campusRepository.findById(election.getCampusId()).orElse(null);
            if (campus == null) continue;

            List<Department> depts = election.getElectionDepartments().stream()
                    .map(ElectionDepartment::getDepartment)
                    .collect(Collectors.toList());
            if (depts.isEmpty()) continue;

            CampusAnalytics ca = buildCampusAnalytics(campus, election, depts);
            ca.status = new LabeledSeries(
                    List.of("Voted", "Not Yet Voted"),
                    List.of(ca.votesCast, Math.max(ca.totalVoters - ca.votesCast, 0))
            );

            VotingType votingType = depts.get(0).getVotingType();
            ca.votingType = votingType != null ? votingType.name() : null;

            da.departments
                    .computeIfAbsent(election.getId().toString(), k -> new LinkedHashMap<>())
                    .put(campusKey(campus), ca);
        }
        return da;
    }

    // ============ SHARED BUILDERS ============

    private CampusAnalytics buildCampusAnalytics(Campus campus, Election election, List<Department> deptFilter) {
        CampusAnalytics ca = new CampusAnalytics();
        ca.campus = campus.getName();
        ca.electionId = election.getId().toString();
        ca.electionTitle = election.getTitle();
        ca.schoolYear = election.getSchoolYear();
        ca.phase = election.getPhase() != null ? election.getPhase().name() : null;
        ca.parentElectionId = election.getParentElectionId() != null ? election.getParentElectionId().toString() : null;
        ca.drawElection = election.isDrawElection();
        ca.drawPositions = new ArrayList<>(drawDetectionService.detect(election).positions());

        if (deptFilter == null || deptFilter.isEmpty()) {
            ca.totalVoters = voterRepository.countByStatusAndCampusId(RecordStatus.ACTIVE, campus.getId());
            ca.votesCast = ballotVoteRepository.countDistinctBallotsForElection(election.getId());
        } else {
            Set<String> electionDeptNames = deptFilter.stream()
                    .map(Department::getName)
                    .collect(Collectors.toSet());

            List<Voter> deptVoters = voterRepository.findByCampusIdAndStatus(campus.getId(), RecordStatus.ACTIVE)
                    .stream()
                    .filter(v -> electionDeptNames.stream().anyMatch(n -> n.equalsIgnoreCase(v.getProgramCourse())))
                    .collect(Collectors.toList());
            ca.totalVoters = deptVoters.size();
            ca.votesCast = ballotVoteRepository.countDistinctBallotsForElection(election.getId());
        }

        ca.candidates = buildCandidateVotes(election, deptFilter);
        ca.yearLevel = buildYearLevel(election.getId());
        ca.activity = buildActivity(election);

        return ca;
    }

    private List<CandidateVotes> buildCandidateVotes(Election election, List<Department> deptFilter) {
        List<Candidate> candidates = candidateRepository.findByElectionId(election.getId());

        Map<UUID, Long> voteCounts = ballotVoteRepository
                .countVotesByCandidateForElection(election.getId())
                .stream()
                .collect(Collectors.toMap(r -> (UUID) r[0], r -> (Long) r[1]));

        return candidates.stream()
                .map(c -> new CandidateVotes(
                        (c.getFirstName() + " " + c.getLastName()).trim(),
                        voteCounts.getOrDefault(c.getId(), 0L),
                        c.getPosition(),
                        c.getPhotoImageUrl()
                ))
                .collect(Collectors.toList());
    }

    private List<PartylistVotes> buildPartylistVotes(Election election) {
        Map<UUID, Long> voteCounts = ballotVoteRepository
                .countVotesByPartylistForElection(election.getId())
                .stream()
                .collect(Collectors.toMap(r -> (UUID) r[0], r -> (Long) r[1]));

        List<Partylist> partylists = election.getElectionPartylists().stream()
                .map(ElectionPartylist::getPartylist)
                .collect(Collectors.toList());

        return partylists.stream()
                .map(p -> new PartylistVotes(p.getName(), voteCounts.getOrDefault(p.getId(), 0L)))
                .collect(Collectors.toList());
    }

    private List<ProgramVotes> buildProgramVotes(UUID electionId) {
        List<Object[]> rows = voteLogRepository.countByElectionIdGroupByProgram(electionId);
        return rows.stream()
                .map(r -> new ProgramVotes((String) r[0], (Long) r[1], null))
                .collect(Collectors.toList());
    }

    /** Registered voters per program (this campus only) minus those who already voted. */
    private List<ProgramVotes> buildProgramNotVoted(UUID campusId, UUID electionId) {
        Map<String, Long> votedByProgram = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        for (Object[] r : voteLogRepository.countByElectionIdGroupByProgram(electionId)) {
            if (r[0] == null) continue;
            votedByProgram.merge(((String) r[0]).trim(), (Long) r[1], Long::sum);
        }

        Map<String, Long> totalByProgram = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        for (Voter v : voterRepository.findByCampusIdAndStatus(campusId, RecordStatus.ACTIVE)) {
            String program = v.getProgramCourse();
            if (program == null || program.isBlank()) continue;
            totalByProgram.merge(program.trim(), 1L, Long::sum);
        }

        return totalByProgram.entrySet().stream()
                .map(e -> new ProgramVotes(
                        e.getKey(),
                        Math.max(e.getValue() - votedByProgram.getOrDefault(e.getKey(), 0L), 0L),
                        null))
                .collect(Collectors.toList());
    }

    private LabeledSeries buildYearLevel(UUID electionId) {
        List<Object[]> rows = voteLogRepository.countByElectionIdGroupByYearLevel(electionId);

        List<String> labels = rows.stream().map(r -> (String) r[0]).collect(Collectors.toList());
        List<Long> values = rows.stream().map(r -> (Long) r[1]).collect(Collectors.toList());

        return new LabeledSeries(labels, values);
    }

    private LabeledSeries buildActivity(Election election) {
        List<Ballot> ballots = ballotRepository.findByElection_IdOrderBySubmittedAtAsc(election.getId());

        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("h a");
        Map<String, Long> hourly = new LinkedHashMap<>();

        for (Ballot b : ballots) {
            ZonedDateTime zdt = b.getSubmittedAt().atZone(ZoneId.systemDefault());
            String label = zdt.format(fmt);
            hourly.merge(label, 1L, Long::sum);
        }

        // cumulative
        List<String> labels = new ArrayList<>(hourly.keySet());
        List<Long> values = new ArrayList<>();
        long running = 0;
        for (String label : labels) {
            running += hourly.get(label);
            values.add(running);
        }

        return new LabeledSeries(labels, values);
    }

    private String campusKey(Campus campus) {
        return campus.getName().toLowerCase().replaceAll("\\s+", "-");
    }

    private List<Election> orderNewestFirst(List<Election> elections) {
        Map<UUID, Election> byId = elections.stream()
                .collect(Collectors.toMap(Election::getId, e -> e, (a, b) -> a));

        Comparator<Election> cmp = Comparator
                .comparing((Election e) -> rootStart(e, byId))
                .reversed()
                .thenComparing(Election::isDrawElection, Comparator.reverseOrder())   // draw sits above its parent
                .thenComparing(Election::getCreatedAt, Comparator.nullsLast(Comparator.reverseOrder()));

        return elections.stream().sorted(cmp).toList();
    }

    private Instant rootStart(Election e, Map<UUID, Election> byId) {
        Election cur = e;
        for (int i = 0; i < 10 && cur.getParentElectionId() != null && byId.containsKey(cur.getParentElectionId()); i++) {
            cur = byId.get(cur.getParentElectionId());
        }
        return cur.getStartAt() != null ? cur.getStartAt() : cur.getCreatedAt();
    }

    /** The slot the existing UI reads = newest NON-draw election. */
    private int primaryIndex(List<Election> elections) {
        for (int i = 0; i < elections.size(); i++) if (!elections.get(i).isDrawElection()) return i;
        return 0;
    }

}