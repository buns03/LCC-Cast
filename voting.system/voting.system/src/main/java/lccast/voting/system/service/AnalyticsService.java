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
            VoteLogRepository voteLogRepository) {   // ADD
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

        // Only ACTIVE elections are ever considered — archived/deleted drop out automatically
        Election election = electionRepository
                .findByCampusIdAndCategoryAndStatus(campusId, ElectionCategory.DEPARTMENT, RecordStatus.ACTIVE)
                .stream()
                .filter(e -> e.getElectionDepartments().stream()
                        .anyMatch(ed -> ed.getDepartment().getId().equals(department.getId())))
                .findFirst()
                .orElse(null);

        if (election == null) {
            response.hasActiveElection = false;
            return response;
        }

        response.hasActiveElection = true;

        CampusAnalytics ca = buildCampusAnalytics(campus, election, List.of(department));
        ca.status = new LabeledSeries(
                List.of("Voted", "Not Yet Voted"),
                List.of(ca.votesCast, Math.max(ca.totalVoters - ca.votesCast, 0))
        );
        ca.votingType = response.votingType;

        response.analytics = ca;
        return response;
    }

    private List<Campus> activeCampuses() {
        return campusRepository.findByStatus(RecordStatus.ACTIVE);
    }

    // ============ SSC ============

    private SSCAnalytics buildSSCAnalytics() {
        SSCAnalytics ssc = new SSCAnalytics();
        ssc.campuses = new LinkedHashMap<>();

        for (Campus campus : activeCampuses()) {
            ssc.campuses.put(campusKey(campus), buildSSCCampusAnalytics(campus));
        }
        return ssc;
    }

    /**
     * Scoped SSC analytics for a single campus — used by admin-ssc,
     * which only ever needs its own campus's numbers. Superadmin's
     * buildSSCAnalytics() above calls this same helper per-campus,
     * so behavior there is unchanged.
     */
    public CampusAnalytics getSscAnalyticsForCampus(UUID campusId) {
        Campus campus = campusRepository.findById(campusId)
                .orElseThrow(() -> new RuntimeException("Campus not found."));

        return buildSSCCampusAnalytics(campus);
    }

    private CampusAnalytics buildSSCCampusAnalytics(Campus campus) {
        Optional<Election> electionOpt = electionRepository
                .findByCampusIdAndCategoryAndStatus(campus.getId(), ElectionCategory.SSC, RecordStatus.ACTIVE)
                .stream().findFirst();

        CampusAnalytics ca = new CampusAnalytics();
        ca.campus = campus.getName();

        if (electionOpt.isPresent()) {
            Election election = electionOpt.get();
            ca = buildCampusAnalytics(campus, election, null);
            ca.partyLists = buildPartylistVotes(election);
            ca.programVotes = buildProgramVotes(election.getId());
        } else {
            ca.candidates = List.of();
            ca.partyLists = List.of();
            ca.programVotes = List.of();
            ca.yearLevel = new LabeledSeries(List.of(), List.of());
            ca.activity = new LabeledSeries(List.of(), List.of());
        }

        return ca;
    }

    // ============ DEPARTMENT ============

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



}