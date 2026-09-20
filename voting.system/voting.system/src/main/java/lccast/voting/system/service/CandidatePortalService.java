package lccast.voting.system.service;

import lccast.voting.system.model.*;
import lccast.voting.system.repository.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * ASSUMPTIONS (Candidate.java, PartylistMember.java, DepartmentMember.java,
 * Partylist.java, Department.java, and their repositories were not shared —
 * field names below follow your Supabase schema 1:1 using the same camelCase
 * convention Voter.java uses, e.g. campaign_image_url -> campaignImageUrl):
 *  - Candidate: id, studentId, lastName, firstName, middleName, position,
 *    photoImageUrl, backgroundImageUrl, campaignImageUrl, partylistId, departmentId
 *  - PartylistMember: id, partylistId, studentId, lastName, firstName, middleName,
 *    position, photoImageUrl, backgroundImageUrl, campaignImageUrl
 *  - DepartmentMember: id, departmentId, studentId, lastName, firstName, middleName,
 *    position, photoImageUrl, backgroundImageUrl, campaignImageUrl
 *  - Partylist: id, name, description, posterImageUrl, posterLogoUrl, schoolYear
 *  - Department: id, name, description, posterImageUrl, posterLogoUrl, schoolYear
 *
 * Required additive repository methods are listed in repository-additions.txt.
 */
@Service
public class CandidatePortalService {

    private final VoterRepository voterRepository;
    private final CandidateRepository candidateRepository;
    private final PartylistMemberRepository partylistMemberRepository;
    private final DepartmentMemberRepository departmentMemberRepository;
    private final PartylistRepository partylistRepository;
    private final DepartmentRepository departmentRepository;
    private final SupabaseStorageService storageService;

    public CandidatePortalService(
            VoterRepository voterRepository,
            CandidateRepository candidateRepository,
            PartylistMemberRepository partylistMemberRepository,
            DepartmentMemberRepository departmentMemberRepository,
            PartylistRepository partylistRepository,
            DepartmentRepository departmentRepository,
            SupabaseStorageService storageService) {
        this.voterRepository = voterRepository;
        this.candidateRepository = candidateRepository;
        this.partylistMemberRepository = partylistMemberRepository;
        this.departmentMemberRepository = departmentMemberRepository;
        this.partylistRepository = partylistRepository;
        this.departmentRepository = departmentRepository;
        this.storageService = storageService;
    }

    // =====================================================
    // LOOKUP
    // =====================================================

    public enum Source { CANDIDATE, PARTYLIST_MEMBER, DEPARTMENT_MEMBER }

    public static class CandidateRecord {
        public Source source;
        public UUID id;
        public String studentId;
        public String lastName;
        public String firstName;
        public String middleName;
        public String position;
        public String photoImageUrl;
        public String backgroundImageUrl;
        public String campaignImageUrl;
        public UUID partylistId;
        public UUID departmentId;

        public boolean isPresident() {
            return position != null && position.trim().equalsIgnoreCase("President");
        }
    }

    public CandidateRecord findRecordByAuthUserId(UUID authUserId) {
        if (authUserId == null) return null;
        Voter voter = voterRepository.findByAuthUserId(authUserId).orElse(null);
        if (voter == null) return null;
        return findRecordByStudentId(voter.getStudentId());
    }

    private CandidateRecord findRecordByStudentId(String studentId) {
        if (studentId == null || studentId.isBlank()) return null;

        Candidate c = candidateRepository.findAllByStudentId(studentId)
                .stream().findFirst().orElse(null);
        if (c != null) return toRecord(c);

        PartylistMember pm = partylistMemberRepository.findAllByStudentId(studentId)
                .stream().findFirst().orElse(null);
        if (pm != null) return toRecord(pm);

        DepartmentMember dm = departmentMemberRepository.findByStudentId(studentId)
                .stream().findFirst().orElse(null);
        if (dm != null) return toRecord(dm);

        return null;
    }

    private CandidateRecord toRecord(Candidate c) {
        CandidateRecord r = new CandidateRecord();
        r.source = Source.CANDIDATE;
        r.id = c.getId();
        r.studentId = c.getStudentId();
        r.lastName = c.getLastName();
        r.firstName = c.getFirstName();
        r.middleName = c.getMiddleName();
        r.position = c.getPosition();
        r.photoImageUrl = c.getPhotoImageUrl();
        r.backgroundImageUrl = c.getBackgroundImageUrl();
        r.campaignImageUrl = c.getCampaignImageUrl();
        r.partylistId = c.getPartylistId();
        r.departmentId = c.getDepartmentId();
        return r;
    }

    private CandidateRecord toRecord(PartylistMember pm) {
        CandidateRecord r = new CandidateRecord();
        r.source = Source.PARTYLIST_MEMBER;
        r.id = pm.getId();
        r.studentId = pm.getStudentId();
        r.lastName = pm.getLastName();
        r.firstName = pm.getFirstName();
        r.middleName = pm.getMiddleName();
        r.position = pm.getPosition();
        r.photoImageUrl = pm.getPhotoImageUrl();
        r.backgroundImageUrl = pm.getBackgroundImageUrl();
        r.campaignImageUrl = pm.getCampaignImageUrl();
        r.partylistId = pm.getPartylist() != null ? pm.getPartylist().getId() : null;
        return r;
    }

    private CandidateRecord toRecord(DepartmentMember dm) {
        CandidateRecord r = new CandidateRecord();
        r.source = Source.DEPARTMENT_MEMBER;
        r.id = dm.getId();
        r.studentId = dm.getStudentId();
        r.lastName = dm.getLastName();
        r.firstName = dm.getFirstName();
        r.middleName = dm.getMiddleName();
        r.position = dm.getPosition();
        r.photoImageUrl = dm.getPhotoImageUrl();
        r.backgroundImageUrl = dm.getBackgroundImageUrl();
        r.campaignImageUrl = dm.getCampaignImageUrl();
        r.departmentId = dm.getDepartment() != null ? dm.getDepartment().getId() : null;
        return r;
    }

    // =====================================================
    // PERSONAL IMAGE UPLOAD (photo / background / campaign)
    // =====================================================

    @Transactional
    public String uploadPersonalImage(UUID authUserId, String imageType, MultipartFile file) throws IOException {
        if (!List.of("photo", "background", "campaign").contains(imageType)) {
            throw new IllegalArgumentException("Invalid image type.");
        }

        Voter voter = voterRepository.findByAuthUserId(authUserId).orElse(null);
        if (voter == null) {
            throw new IllegalStateException("Voter account not found.");
        }

        String studentId = voter.getStudentId();
        String storagePath = storageService.uploadFile(file, imageType);

        boolean updatedAny = false;

        // Write to EVERY row for this studentId across all three tables,
        // not just the first one found — a student can have a row in more
        // than one place, and each is read directly by a different page.
        List<Candidate> candidates = candidateRepository.findAllByStudentId(studentId);
        for (Candidate c : candidates) {
            applyImage(c, imageType, storagePath);
        }
        if (!candidates.isEmpty()) {
            candidateRepository.saveAll(candidates);
            updatedAny = true;
        }

        List<PartylistMember> partylistMembers = partylistMemberRepository.findAllByStudentId(studentId);
        for (PartylistMember pm : partylistMembers) {
            applyImage(pm, imageType, storagePath);
        }
        if (!partylistMembers.isEmpty()) {
            partylistMemberRepository.saveAll(partylistMembers);
            updatedAny = true;
        }

        List<DepartmentMember> departmentMembers = departmentMemberRepository.findByStudentId(studentId);
        for (DepartmentMember dm : departmentMembers) {
            applyImage(dm, imageType, storagePath);
        }
        if (!departmentMembers.isEmpty()) {
            departmentMemberRepository.saveAll(departmentMembers);
            updatedAny = true;
        }

        if (!updatedAny) {
            throw new IllegalStateException("No candidate record found for this account.");
        }

        return storagePath;
    }

    private void applyImage(Candidate c, String imageType, String path) {
        switch (imageType) {
            case "photo" -> c.setPhotoImageUrl(path);
            case "background" -> c.setBackgroundImageUrl(path);
            case "campaign" -> c.setCampaignImageUrl(path);
        }
    }


    private void applyImage(PartylistMember pm, String imageType, String path) {
        switch (imageType) {
            case "photo" -> pm.setPhotoImageUrl(path);
            case "background" -> pm.setBackgroundImageUrl(path);
            case "campaign" -> pm.setCampaignImageUrl(path);
        }
    }

    private void applyImage(DepartmentMember dm, String imageType, String path) {
        switch (imageType) {
            case "photo" -> dm.setPhotoImageUrl(path);
            case "background" -> dm.setBackgroundImageUrl(path);
            case "campaign" -> dm.setCampaignImageUrl(path);
        }
    }

    // =====================================================
    // CROSS-TABLE IMAGE LOOKUP (used by PartylistService /
    // DepartmentService when seeding a new member row)
    // =====================================================

    public static class ExistingImages {
        public String photoImageUrl;
        public String backgroundImageUrl;
        public String campaignImageUrl;
    }

    public ExistingImages findExistingImages(String studentId) {
        if (studentId == null || studentId.isBlank()) return null;

        Candidate c = candidateRepository.findAllByStudentId(studentId).stream().findFirst().orElse(null);
        if (c != null && hasAnyImage(c.getPhotoImageUrl(), c.getBackgroundImageUrl(), c.getCampaignImageUrl())) {
            return toImages(c.getPhotoImageUrl(), c.getBackgroundImageUrl(), c.getCampaignImageUrl());
        }

        PartylistMember pm = partylistMemberRepository.findAllByStudentId(studentId).stream()
                .filter(m -> hasAnyImage(m.getPhotoImageUrl(), m.getBackgroundImageUrl(), m.getCampaignImageUrl()))
                .findFirst().orElse(null);
        if (pm != null) {
            return toImages(pm.getPhotoImageUrl(), pm.getBackgroundImageUrl(), pm.getCampaignImageUrl());
        }

        DepartmentMember dm = departmentMemberRepository.findByStudentId(studentId).stream()
                .filter(m -> hasAnyImage(m.getPhotoImageUrl(), m.getBackgroundImageUrl(), m.getCampaignImageUrl()))
                .findFirst().orElse(null);
        if (dm != null) {
            return toImages(dm.getPhotoImageUrl(), dm.getBackgroundImageUrl(), dm.getCampaignImageUrl());
        }

        return null;
    }

    private boolean hasAnyImage(String photo, String bg, String campaign) {
        return photo != null || bg != null || campaign != null;
    }

    private ExistingImages toImages(String photo, String bg, String campaign) {
        ExistingImages img = new ExistingImages();
        img.photoImageUrl = photo;
        img.backgroundImageUrl = bg;
        img.campaignImageUrl = campaign;
        return img;
    }


    // =====================================================
    // GROUP (PARTYLIST / DEPARTMENT) INFO
    // =====================================================

    public static class GroupMember {
        public String fullName;
        public String position;
        public String photoImageUrl;
    }

    public static class GroupInfo {
        public String groupType; // "PARTYLIST" or "DEPARTMENT"
        public UUID groupId;
        public String name;
        public String description;
        public String posterImageUrl;
        public String posterLogoUrl;
        public String schoolYear;
        public boolean canEdit;
        public List<GroupMember> members;
    }

    public GroupInfo getGroupInfo(UUID authUserId) {
        CandidateRecord record = findRecordByAuthUserId(authUserId);
        if (record == null) {
            throw new IllegalStateException("No candidate record found for this account.");
        }

        GroupInfo info = new GroupInfo();
        info.canEdit = record.isPresident();

        if (record.partylistId != null) {
            Partylist p = partylistRepository.findById(record.partylistId)
                    .orElseThrow(() -> new IllegalStateException("Partylist not found."));

            info.groupType = "PARTYLIST";
            info.groupId = p.getId();
            info.name = p.getName();
            info.description = p.getDescription();
            info.posterImageUrl = p.getPosterImageUrl();
            info.posterLogoUrl = p.getPosterLogoUrl();
            info.schoolYear = p.getSchoolYear();

            info.members = partylistMemberRepository.findByPartylistId(record.partylistId).stream()
                    .map(m -> {
                        GroupMember gm = new GroupMember();
                        gm.fullName = joinName(m.getFirstName(), m.getMiddleName(), m.getLastName());
                        gm.position = m.getPosition();
                        gm.photoImageUrl = m.getPhotoImageUrl();
                        return gm;
                    })
                    .collect(Collectors.toList());

            return info;
        }

        if (record.departmentId != null) {
            Department d = departmentRepository.findById(record.departmentId)
                    .orElseThrow(() -> new IllegalStateException("Department not found."));

            info.groupType = "DEPARTMENT";
            info.groupId = d.getId();
            info.name = d.getName();
            info.description = d.getDescription();
            info.posterImageUrl = d.getPosterImageUrl();
            info.posterLogoUrl = d.getPosterLogoUrl();
            info.schoolYear = d.getSchoolYear();

            info.members = departmentMemberRepository.findByDepartmentId(record.departmentId).stream()
                    .map(m -> {
                        GroupMember gm = new GroupMember();
                        gm.fullName = joinName(m.getFirstName(), m.getMiddleName(), m.getLastName());
                        gm.position = m.getPosition();
                        gm.photoImageUrl = m.getPhotoImageUrl();
                        return gm;
                    })
                    .collect(Collectors.toList());

            return info;
        }

        throw new IllegalStateException("This candidate is not linked to a partylist or department.");
    }

    @Transactional
    public void updateGroupInfo(UUID authUserId, String description, MultipartFile poster, MultipartFile logo) throws IOException {
        CandidateRecord record = findRecordByAuthUserId(authUserId);
        if (record == null) {
            throw new IllegalStateException("No candidate record found for this account.");
        }

        if (!record.isPresident()) {
            throw new IllegalArgumentException("Only the President can update this information.");
        }

        if (record.partylistId != null) {
            Partylist p = partylistRepository.findById(record.partylistId)
                    .orElseThrow(() -> new IllegalStateException("Partylist not found."));

            if (description != null) p.setDescription(description);
            if (poster != null && !poster.isEmpty()) p.setPosterImageUrl(storageService.uploadFile(poster, "poster"));
            if (logo != null && !logo.isEmpty()) p.setPosterLogoUrl(storageService.uploadFile(logo, "logo"));

            partylistRepository.save(p);
            return;
        }

        if (record.departmentId != null) {
            Department d = departmentRepository.findById(record.departmentId)
                    .orElseThrow(() -> new IllegalStateException("Department not found."));

            if (description != null) d.setDescription(description);
            if (poster != null && !poster.isEmpty()) d.setPosterImageUrl(storageService.uploadFile(poster, "poster"));
            if (logo != null && !logo.isEmpty()) d.setPosterLogoUrl(storageService.uploadFile(logo, "logo"));

            departmentRepository.save(d);
            return;
        }

        throw new IllegalStateException("This candidate is not linked to a partylist or department.");
    }

    private String joinName(String first, String middle, String last) {
        StringBuilder sb = new StringBuilder();
        if (first != null) sb.append(first).append(" ");
        if (middle != null && !middle.isBlank()) sb.append(middle).append(" ");
        if (last != null) sb.append(last);
        return sb.toString().trim();
    }
}