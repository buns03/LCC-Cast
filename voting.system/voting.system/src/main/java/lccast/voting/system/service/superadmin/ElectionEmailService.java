package lccast.voting.system.service.superadmin;

import jakarta.servlet.http.HttpServletRequest;
import lccast.voting.system.model.*;
import lccast.voting.system.repository.*;
import lccast.voting.system.service.AuditLogService;
import lccast.voting.system.service.DrawDetectionService;
import lccast.voting.system.service.SupabaseStorageService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import lccast.voting.system.repository.BallotVoteRepository;
import lccast.voting.system.repository.PartylistMemberRepository;
import lccast.voting.system.repository.DepartmentMemberRepository;

import jakarta.mail.internet.MimeMessage;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Service
public class ElectionEmailService {

    private static final Logger log = LoggerFactory.getLogger(ElectionEmailService.class);

    private final ElectionService electionService;
    private final ElectionDepartmentRepository electionDepartmentRepository;
    private final ElectionPartylistRepository electionPartylistRepository;
    private final DepartmentRepository departmentRepository;
    private final CampusRepository campusRepository;
    private final VoterRepository voterRepository;
    private final ElectionEmailCampaignRepository campaignRepository;
    private final ElectionEmailRecipientRepository recipientRepository;
    private final JavaMailSender mailSender;
    private final TemplateEngine templateEngine;
    private final AuditLogService auditLogService;
    private final PartylistRepository partylistRepository;
    private final CandidateRepository candidateRepository;
    private final SupabaseStorageService supabaseStorageService;
    private final BallotVoteRepository ballotVoteRepository;
    private final DrawDetectionService drawDetectionService;


    @Value("${lccast.mail.from}")
    private String fromAddress;

    @Value("${lccast.mail.from-name}")
    private String fromName;

    @Value("${lccast.app.login-url}")
    private String loginUrl;

    @Value("${lccast.app.base-url}")
    private String baseUrl;

    public ElectionEmailService(
            ElectionService electionService,
            ElectionDepartmentRepository electionDepartmentRepository,
            ElectionPartylistRepository electionPartylistRepository,
            DepartmentRepository departmentRepository,
            CampusRepository campusRepository,
            VoterRepository voterRepository,
            ElectionEmailCampaignRepository campaignRepository,
            ElectionEmailRecipientRepository recipientRepository,
            JavaMailSender mailSender,
            TemplateEngine templateEngine,
            AuditLogService auditLogService,
            PartylistRepository partylistRepository,
            CandidateRepository candidateRepository,
            SupabaseStorageService supabaseStorageService,
            BallotVoteRepository ballotVoteRepository,
            DrawDetectionService drawDetectionService
    ) {
        this.electionService = electionService;
        this.electionDepartmentRepository = electionDepartmentRepository;
        this.electionPartylistRepository = electionPartylistRepository;
        this.departmentRepository = departmentRepository;
        this.campusRepository = campusRepository;
        this.voterRepository = voterRepository;
        this.campaignRepository = campaignRepository;
        this.recipientRepository = recipientRepository;
        this.mailSender = mailSender;
        this.templateEngine = templateEngine;
        this.auditLogService = auditLogService;
        this.partylistRepository = partylistRepository;
        this.candidateRepository = candidateRepository;
        this.supabaseStorageService = supabaseStorageService;
        this.ballotVoteRepository = ballotVoteRepository;
        this.drawDetectionService = drawDetectionService;
    }

    /* ======================================================
       ELIGIBILITY
    ====================================================== */

    public List<Voter> getEligibleVoters(Election election) {
        log.info("Eligibility check — electionId={}, category={}, campusId={}",
                election.getId(), election.getCategory(), election.getCampusId());

        if (election.getCategory() == ElectionCategory.SSC) {
            List<Voter> voters = voterRepository.findByCampusIdAndStatus(
                    election.getCampusId(), RecordStatus.ACTIVE);
            log.info("SSC eligibility — found {} voters for campusId={}",
                    voters.size(), election.getCampusId());
            return voters;
        }

        List<UUID> departmentIds = electionDepartmentRepository
                .findByElectionId(election.getId())
                .stream()
                .map(link -> link.getDepartment().getId())
                .toList();

        log.info("Department eligibility — linked departmentIds={}", departmentIds);

        if (departmentIds.isEmpty()) return List.of();

        List<String> departmentCodes = departmentRepository.findAllById(departmentIds)
                .stream()
                .map(this::extractProgramCode) // ELIGIBILITY MATCH FIELD — derived from title
                .filter(Objects::nonNull)
                .toList();

        log.info("Department eligibility — resolved codes={}", departmentCodes);

        if (departmentCodes.isEmpty()) return List.of();

        List<Voter> voters = voterRepository.findByCampusIdAndProgramCourseInIgnoreCaseAndStatus(
                election.getCampusId(), departmentCodes, RecordStatus.ACTIVE);

        log.info("Department eligibility — found {} voters for campusId={}, codes={}",
                voters.size(), election.getCampusId(), departmentCodes);

        return voters;
    }

    /**
     * Departments are named like "BSIS - Department Test" or "BAEL - Election 2026".
     * Voter program_course only stores the bare code ("BSIS", "BAEL").
     * This extracts the code portion (everything before the first " - ").
     * Falls back to department.getCode() if it's ever populated, and to the
     * full title (trimmed) if there's no separator at all.
     */
    private String extractProgramCode(Department department) {
        if (department.getCode() != null && !department.getCode().isBlank()) {
            return department.getCode().trim().toLowerCase();
        }

        String title = department.getTitle();
        if (title == null || title.isBlank()) return null;

        String[] parts = title.split("-", 2);
        String code = parts[0].trim();

        return code.isBlank() ? null : code.toLowerCase();
    }

    public long getEligibleVoterCount(UUID electionId) {
        return getEligibleVoters(electionService.getById(electionId)).size();
    }

    /* ======================================================
       TEMPLATE RENDERING
    ====================================================== */

    private static final DateTimeFormatter DATE_FMT =
            DateTimeFormatter.ofPattern("MMMM d, yyyy 'at' h:mm a").withZone(ZoneId.systemDefault());

    private String formatDate(Instant value) {
        return value == null ? "To be announced" : DATE_FMT.format(value);
    }

    private void assertHasSchedule(Election election) {
        if (election.getStartAt() == null || election.getEndAt() == null) {
            throw new IllegalStateException(
                    "Set the voting schedule for this draw election before sending the announcement email.");
        }
    }

    private Context buildEmailContext(Election election) {
        Context ctx = new Context();

        String campusName = campusRepository.findById(election.getCampusId())
                .map(Campus::getName).orElse("");

        ctx.setVariable("electionTitle", election.getTitle());
        ctx.setVariable("schoolYear", election.getSchoolYear());
        ctx.setVariable("campus", campusName);

        ElectionPhase phase = election.getPhase();

        String description = (phase == ElectionPhase.UPCOMING)
                ? "This election has not started yet. Mark your calendar and log in to LCC Cast once voting opens to cast your vote."
                : "Voting for this election is now open. Log in to LCC Cast to view the candidates or program information and cast your vote before it closes.";

        if (election.isDrawElection()) {
            description = "This is a tie-break election for positions that ended in a draw. " + description;
        }

        ctx.setVariable("description", description);
        ctx.setVariable("startAt", formatDate(election.getStartAt()));
        ctx.setVariable("endAt", formatDate(election.getEndAt()));
        ctx.setVariable("loginUrl", loginUrl);

        boolean isSSC = election.getCategory() == ElectionCategory.SSC;
        ctx.setVariable("isSSC", isSSC);

        List<Candidate> candidates = candidateRepository.findByElectionId(election.getId());

        if (isSSC) {
            ctx.setVariable("categoryLabel", "Student Supreme Council Election");

            List<String> partylistNames = electionPartylistRepository
                    .findByElectionId(election.getId())
                    .stream()
                    .map(link -> link.getPartylist().getName())
                    .toList();

            ctx.setVariable("showGroups", true);
            ctx.setVariable("groupNames", partylistNames);
            ctx.setVariable("isRepresentative", false);

            ctx.setVariable("positionsWithCandidates",
                    groupCandidatesByPosition(candidates, this::resolvePartylistName));

        } else {
            List<Department> departments = electionDepartmentRepository
                    .findByElectionId(election.getId())
                    .stream()
                    .map(ElectionDepartment::getDepartment)
                    .toList();

            VotingType votingType = departments.isEmpty()
                    ? null : departments.get(0).getVotingType();

            String votingTypeLabel = votingType == VotingType.PARTYLIST
                    ? "Partylist Voting" : "Representative Voting";

            ctx.setVariable("categoryLabel", "Department Election (" + votingTypeLabel + ")");
            ctx.setVariable("votingTypeLabel", votingTypeLabel);
            ctx.setVariable("departmentNames",
                    departments.stream().map(Department::getTitle).toList());

            boolean isRepresentative = votingType == VotingType.REPRESENTATIVE;
            ctx.setVariable("isRepresentative", isRepresentative);

            if (isRepresentative) {
                ctx.setVariable("showGroups", false);

                List<Map<String, String>> representativeCandidates = candidates.stream()
                        .filter(c -> !candidateFullName(c).isBlank())
                        .map(c -> {
                            Map<String, String> info = new LinkedHashMap<>();
                            info.put("name", candidateFullName(c));
                            info.put("photo", resolveCandidatePhotoUrl(c));
                            return info;
                        })
                        .toList();

                ctx.setVariable("representativeCandidates", representativeCandidates);

            } else {
                List<String> groupNames = departments.stream()
                        .map(Department::getTitle)
                        .toList();

                ctx.setVariable("showGroups", true);
                ctx.setVariable("groupNames", groupNames);

                ctx.setVariable("positionsWithCandidates",
                        groupCandidatesByPosition(candidates, this::resolveDepartmentGroupName));
            }
        }

        return ctx;
    }

    /**
     * Groups candidates by their position, resolving each candidate's
     * "group" label (partylist name, or department/partylist-group title)
     * via the given resolver.
     */
    private List<Map<String, Object>> groupCandidatesByPosition(
            List<Candidate> candidates,
            java.util.function.Function<Candidate, String> groupNameResolver) {

        Map<String, List<Candidate>> byPosition = candidates.stream()
                .collect(Collectors.groupingBy(
                        c -> (c.getPosition() == null || c.getPosition().isBlank())
                                ? "Unassigned" : c.getPosition(),
                        LinkedHashMap::new,
                        Collectors.toList()));

        List<Map<String, Object>> result = new ArrayList<>();

        for (Map.Entry<String, List<Candidate>> entry : byPosition.entrySet()) {
            List<Map<String, String>> candidateInfos = entry.getValue().stream()
                    .map(c -> {
                        Map<String, String> info = new LinkedHashMap<>();
                        info.put("name", candidateFullName(c));
                        info.put("group", groupNameResolver.apply(c));
                        info.put("photo", resolveCandidatePhotoUrl(c));
                        return info;
                    })
                    .toList();

            Map<String, Object> positionMap = new LinkedHashMap<>();
            positionMap.put("name", entry.getKey());
            positionMap.put("candidates", candidateInfos);
            result.add(positionMap);
        }

        return result;
    }

    private String candidateFullName(Candidate candidate) {
        return java.util.stream.Stream.of(
                        candidate.getFirstName(),
                        candidate.getMiddleName(),
                        candidate.getLastName())
                .filter(part -> part != null && !part.isBlank())
                .collect(Collectors.joining(" "));
    }

    private String resolvePartylistName(Candidate candidate) {
        if (candidate.getPartylistId() == null) return "Independent";

        return partylistRepository.findById(candidate.getPartylistId())
                .map(Partylist::getName)
                .orElse("Independent");
    }

    private String resolveDepartmentGroupName(Candidate candidate) {
        if (candidate.getDepartmentId() == null) return "Independent";

        return departmentRepository.findById(candidate.getDepartmentId())
                .map(Department::getTitle)
                .orElse("Independent");
    }

    private String resolveCandidatePhotoUrl(Candidate candidate) {
        String path = candidate.getPhotoImageUrl();
        if (path == null || path.isBlank()) return null;
        return supabaseStorageService.createSignedUrl(path, 60 * 60 * 24 * 7); // 7-day expiry
    }

    private String renderHtml(Election election) {
        return templateEngine.process("email/election-notification", buildEmailContext(election));
    }

    private String buildSubject(Election election) {
        return "[LCC Cast] " + election.getTitle();
    }

    /* ======================================================
       SEND NOW
    ====================================================== */

    @Transactional
    public ElectionEmailCampaign sendNow(UUID electionId, UUID adminId, HttpServletRequest request) {
        Election election = electionService.getById(electionId);
        assertHasSchedule(election);
        List<Voter> recipients = getEligibleVoters(election);

        String html = renderHtml(election);
        String subject = buildSubject(election);

        ElectionEmailCampaign campaign = new ElectionEmailCampaign();
        campaign.setElectionId(electionId);
        campaign.setSubject(subject);
        campaign.setBody(html);
        campaign.setSendType("NOW");
        campaign.setStatus("SENDING");
        campaign.setCreatedBy(adminId);
        campaign = campaignRepository.save(campaign);

        dispatch(campaign, recipients, html, subject);

        campaign.setStatus("SENT");
        campaign.setSentAt(Instant.now());
        campaign = campaignRepository.save(campaign);

        auditLogService.log(
                request, lccast.voting.system.model.AuditAction.UPDATE, "Elections", electionId,
                "sent election email for: " + election.getTitle(),
                Map.of("recipients", recipients.size(), "sendType", "NOW")
        );

        return campaign;
    }

    /* ======================================================
       SCHEDULE
    ====================================================== */

    @Transactional
    public ElectionEmailCampaign schedule(
            UUID electionId, Instant scheduledAt, UUID adminId, HttpServletRequest request) {

        if (scheduledAt == null || !scheduledAt.isAfter(Instant.now())) {
            throw new IllegalArgumentException("Scheduled time must be in the future.");
        }

        campaignRepository.findFirstByElectionIdAndStatusOrderByCreatedAtDesc(electionId, "SCHEDULED")
                .ifPresent(existing -> {
                    throw new IllegalStateException(
                            "An email is already scheduled for this election. Cancel it first."
                    );
                });

        Election election = electionService.getById(electionId);
        assertHasSchedule(election);
        String html = renderHtml(election);
        String subject = buildSubject(election);

        ElectionEmailCampaign campaign = new ElectionEmailCampaign();
        campaign.setElectionId(electionId);
        campaign.setSubject(subject);
        campaign.setBody(html);
        campaign.setSendType("SCHEDULED");
        campaign.setScheduledAt(scheduledAt);
        campaign.setStatus("SCHEDULED");
        campaign.setCreatedBy(adminId);
        campaign = campaignRepository.save(campaign);

        auditLogService.log(
                request, lccast.voting.system.model.AuditAction.UPDATE, "Elections", electionId,
                "scheduled election email for: " + election.getTitle(),
                Map.of("scheduledAt", scheduledAt.toString())
        );

        return campaign;
    }

    @Transactional
    public void cancelScheduled(UUID electionId, HttpServletRequest request) {
        ElectionEmailCampaign campaign = campaignRepository
                .findFirstByElectionIdAndStatusOrderByCreatedAtDesc(electionId, "SCHEDULED")
                .orElseThrow(() -> new IllegalArgumentException("No scheduled email found for this election."));

        campaign.setStatus("CANCELLED");
        campaignRepository.save(campaign);

        auditLogService.log(
                request, lccast.voting.system.model.AuditAction.UPDATE, "Elections", electionId,
                "cancelled scheduled election email", Map.of()
        );
    }

    public Optional<ElectionEmailCampaign> getScheduledStatus(UUID electionId) {
        return campaignRepository.findFirstByElectionIdAndStatusOrderByCreatedAtDesc(electionId, "SCHEDULED");
    }

    /* ======================================================
       CALLED BY THE SCHEDULER (Section 15)
    ====================================================== */

    @Transactional
    public void sendScheduledCampaign(ElectionEmailCampaign campaign) {
        try {
            Election election = electionService.getById(campaign.getElectionId());
            List<Voter> recipients = getEligibleVoters(election);

            campaign.setStatus("SENDING");
            campaignRepository.save(campaign);

            dispatch(campaign, recipients, campaign.getBody(), campaign.getSubject());

            campaign.setStatus("SENT");
            campaign.setSentAt(Instant.now());
            campaignRepository.save(campaign);
        } catch (Exception e) {
            campaign.setStatus("FAILED");
            campaignRepository.save(campaign);
        }
    }

    /* ======================================================
       ACTUAL SENDING
    ====================================================== */

    private void dispatch(
            ElectionEmailCampaign campaign, List<Voter> voters, String html, String subject) {

        for (Voter voter : voters) {
            if (voter.getEmail() == null || voter.getEmail().isBlank()) continue;

            ElectionEmailRecipient recipient = new ElectionEmailRecipient();
            recipient.setCampaignId(campaign.getId());
            recipient.setVoterId(voter.getId());
            recipient.setEmail(voter.getEmail());
            recipient.setStatus("PENDING");
            recipient = recipientRepository.save(recipient);

            try {
                MimeMessage message = mailSender.createMimeMessage();
                MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
                helper.setFrom(fromAddress, fromName);
                helper.setTo(voter.getEmail());
                helper.setSubject(subject);
                helper.setText(html, true);

                mailSender.send(message);

                recipient.setStatus("SENT");
                recipient.setSentAt(Instant.now());
            } catch (Exception e) {
                recipient.setStatus("FAILED");
                recipient.setErrorMessage(e.getMessage());
            }

            recipientRepository.save(recipient);
        }
    }

    /* ======================================================
   WINNER ANNOUNCEMENT
====================================================== */

    private record WinnerRow(String position, String name, String group, String photo, long votes) {}

    private static final List<String> DEFAULT_REPRESENTATIVE_POSITIONS = List.of(
            "President", "Vice President", "Secretary", "Treasurer",
            "Auditor", "PRO Internal", "PRO External");

    private List<WinnerRow> computeWinners(Election election) {
        List<Candidate> candidates = candidateRepository.findByElectionId(election.getId());

        if (candidates.isEmpty()) {
            log.warn("No candidates found for electionId={} — winner email will have no winners.", election.getId());
            return List.of();
        }

        Map<UUID, Long> votesByCandidateId = new HashMap<>();
        for (Object[] row : ballotVoteRepository.countVotesByCandidateForElection(election.getId())) {
            votesByCandidateId.put((UUID) row[0], (Long) row[1]);
        }

        boolean isSSC = election.getCategory() == ElectionCategory.SSC;
        Department department = null;

        if (!isSSC) {
            List<Department> departments = electionDepartmentRepository
                    .findByElectionId(election.getId())
                    .stream()
                    .map(ElectionDepartment::getDepartment)
                    .toList();
            department = departments.isEmpty() ? null : departments.get(0);
        }

        boolean isRepresentative = department != null && department.getVotingType() == VotingType.REPRESENTATIVE;

        DrawDetectionService.DrawOutcome outcome = drawDetectionService.detect(election);

        return isRepresentative
                ? computeRepresentativeWinners(election, department, candidates, outcome)
                : computePositionalWinners(election, candidates, votesByCandidateId, outcome);
    }

    /** SSC and Department‑Partylist: one winner per distinct declared position. */
    private List<WinnerRow> computePositionalWinners(
            Election election, List<Candidate> candidates, Map<UUID, Long> votesByCandidateId,
            DrawDetectionService.DrawOutcome outcome) {

        Set<String> drawPositions = outcome.positions().stream()
                .map(String::trim).collect(Collectors.toSet());

        Map<String, List<Candidate>> byPosition = candidates.stream()
                .collect(Collectors.groupingBy(
                        c -> (c.getPosition() == null || c.getPosition().isBlank())
                                ? "Unassigned" : c.getPosition().trim(),
                        LinkedHashMap::new,
                        Collectors.toList()));

        List<WinnerRow> winners = new ArrayList<>();
        for (Map.Entry<String, List<Candidate>> entry : byPosition.entrySet()) {
            if (drawPositions.contains(entry.getKey())) continue;   // draw: no official winner yet

            Candidate top = null;
            long topVotes = -1;
            for (Candidate c : entry.getValue()) {
                long votes = votesByCandidateId.getOrDefault(c.getId(), 0L);
                if (votes > topVotes) { topVotes = votes; top = c; }
            }
            if (top == null) continue;

            String group = election.getCategory() == ElectionCategory.SSC
                    ? resolvePartylistName(top) : resolveDepartmentGroupName(top);

            winners.add(new WinnerRow(entry.getKey(), candidateFullName(top), group,
                    resolveCandidatePhotoUrl(top), topVotes));
        }
        return winners;
    }


    /** Department‑Representative: no declared positions — rank everyone by votes
     *  and assign the department's position list in order (1st = President, etc). */
    private List<WinnerRow> computeRepresentativeWinners(
            Election election, Department department, List<Candidate> candidates,
            DrawDetectionService.DrawOutcome outcome) {

        Map<UUID, Candidate> candidateById = candidates.stream()
                .collect(Collectors.toMap(Candidate::getId, c -> c));

        // position -> candidateId -> votes (only used to show the winner's vote count)
        Map<String, Map<UUID, Long>> tallyByPosition = new LinkedHashMap<>();
        for (Object[] row : ballotVoteRepository.countVotesByCandidateAndPositionForElectionAndDepartment(
                election.getId(), department.getId())) {

            UUID candidateId = (UUID) row[0];
            String position = (String) row[1];
            Long count = (Long) row[2];

            if (position == null || position.isBlank()) continue;
            if (!candidateById.containsKey(candidateId)) continue;

            tallyByPosition.computeIfAbsent(position, k -> new HashMap<>())
                    .merge(candidateId, count, Long::sum);
        }

        List<String> positionOrder = drawDetectionService.openPositions(election, department);
        if (positionOrder.isEmpty()) positionOrder = DEFAULT_REPRESENTATIVE_POSITIONS;

        String group = resolveDepartmentGroupName(candidates.get(0));
        List<WinnerRow> winners = new ArrayList<>();

        for (String position : positionOrder) {
            if (outcome.tiedByPosition().containsKey(position)) continue;   // draw: no official winner yet

            UUID winnerId = outcome.winnersByPosition().get(position);
            Candidate top = winnerId == null ? null : candidateById.get(winnerId);
            if (top == null) continue;

            long votes = tallyByPosition.getOrDefault(position, Map.of()).getOrDefault(winnerId, 0L);
            winners.add(new WinnerRow(position, candidateFullName(top), group,
                    resolveCandidatePhotoUrl(top), votes));
        }
        return winners;
    }

    private Context buildWinnerEmailContext(Election election) {
        Context ctx = new Context();

        String campusName = campusRepository.findById(election.getCampusId())
                .map(Campus::getName).orElse("");

        boolean isSSC = election.getCategory() == ElectionCategory.SSC;

        String electionType = isSSC ? "Supreme Student Council" : "Departmental Election";

        String programCourse = null;
        if (!isSSC) {
            List<Department> departments = electionDepartmentRepository
                    .findByElectionId(election.getId())
                    .stream()
                    .map(ElectionDepartment::getDepartment)
                    .toList();

            Department dept = departments.isEmpty() ? null : departments.get(0);

            programCourse = dept == null ? null : dept.getName();
        }

        String headerSubtitle = programCourse != null
                ? programCourse + " - " + campusName + " - " + electionType
                : campusName + " - " + electionType;

        ctx.setVariable("headerSubtitle", headerSubtitle);
        ctx.setVariable("logoUrl", baseUrl + "/images/lcccast_logo.png");
        ctx.setVariable("electionTitle", election.getTitle());
        ctx.setVariable("campus", campusName);
        ctx.setVariable("schoolYear", election.getSchoolYear());
        ctx.setVariable("loginUrl", loginUrl);


        List<WinnerRow> winners = computeWinners(election);
        List<Map<String, Object>> winnerMaps = winners.stream().map(w -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("position", w.position());
            m.put("name", w.name());
            m.put("group", w.group());
            m.put("photo", w.photo());
            m.put("votes", w.votes());
            return m;
        }).toList();

        ctx.setVariable("winners", winnerMaps);
        ctx.setVariable("drawPositions", new ArrayList<>(drawDetectionService.detect(election).positions()));

        return ctx;
    }


    private String renderWinnerHtml(Election election) {
        return templateEngine.process("email/election-winners", buildWinnerEmailContext(election));
    }

    private String buildWinnerSubject(Election election) {
        return "[LCC Cast] Results: " + election.getTitle();
    }

    /**
     * Sends the winner announcement to every voter eligible for this
     * election — SSC elections reach only that campus, department
     * elections reach only that campus + program combination, both
     * via the existing getEligibleVoters() scoping.
     *
     * Idempotent: a WINNERS campaign is only sent once per election.
     */
    @Transactional
    public ElectionEmailCampaign sendWinnerAnnouncement(UUID electionId, HttpServletRequest request) {
        boolean alreadySent = campaignRepository.existsByElectionIdAndSendType(electionId, "WINNERS");

        if (alreadySent) {
            log.info("Winner announcement already sent for electionId={}, skipping.", electionId);
            throw new IllegalStateException("Winner announcement already sent for this election.");
        }

        Election election = electionService.getById(electionId);
        log.info("Building winner announcement — electionId={}, title={}", electionId, election.getTitle());

        List<Voter> recipients = getEligibleVoters(election);
        log.info("Winner announcement — {} eligible recipients for electionId={}", recipients.size(), electionId);

        String html;
        String subject;
        try {
            html = renderWinnerHtml(election);
            subject = buildWinnerSubject(election);
        } catch (Exception e) {
            log.error("Failed to render winner email for electionId={}", electionId, e);
            throw e;
        }

        ElectionEmailCampaign campaign = new ElectionEmailCampaign();
        campaign.setElectionId(electionId);
        campaign.setSubject(subject);
        campaign.setBody(html);
        campaign.setSendType("WINNERS");
        campaign.setStatus("SENDING");
        campaign = campaignRepository.save(campaign);

        dispatch(campaign, recipients, html, subject);

        campaign.setStatus("SENT");
        campaign.setSentAt(Instant.now());
        campaign = campaignRepository.save(campaign);

        log.info("Winner announcement sent — electionId={}, recipients={}", electionId, recipients.size());

        if (request != null) {
            auditLogService.log(
                    request, lccast.voting.system.model.AuditAction.UPDATE, "Elections", electionId,
                    "sent winner announcement for: " + election.getTitle(),
                    Map.of("recipients", recipients.size(), "sendType", "WINNERS")
            );
        }

        return campaign;
    }
}