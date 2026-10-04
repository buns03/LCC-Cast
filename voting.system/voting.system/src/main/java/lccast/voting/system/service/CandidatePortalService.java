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
    // CANDIDATE TYPE — a student can be BOTH at once, and
    // their images are never shared across the two.
    // =====================================================

    public enum CandidateType { SSC, DEPARTMENT }

    public static class CandidacyStatus {
        public boolean ssc;
        public boolean department;
    }

    public CandidacyStatus getCandidacyStatus(UUID authUserId) {
        CandidacyStatus status = new CandidacyStatus();
        Voter voter = voterRepository.findByAuthUserId(authUserId).orElse(null);
        if (voter == null) return status;

        status.ssc = findRecordByStudentId(voter.getStudentId(), CandidateType.SSC) != null;
        status.department = findRecordByStudentId(voter.getStudentId(), CandidateType.DEPARTMENT) != null;
        return status;
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

    public CandidateRecord findRecordByAuthUserId(UUID authUserId, CandidateType type) {
        if (authUserId == null) return null;
        Voter voter = voterRepository.findByAuthUserId(authUserId).orElse(null);
        if (voter == null) return null;
        return findRecordByStudentId(voter.getStudentId(), type);
    }

    public String findAvatarStoragePath(UUID authUserId) {
        if (authUserId == null) return null;

        CandidateRecord record = findRecordByAuthUserId(authUserId, CandidateType.SSC);
        if (record == null || record.photoImageUrl == null) {
            CandidateRecord deptRecord = findRecordByAuthUserId(authUserId, CandidateType.DEPARTMENT);
            if (deptRecord != null && deptRecord.photoImageUrl != null) {
                record = deptRecord;
            }
        }

        return (record != null) ? record.photoImageUrl : null;
    }

    private CandidateRecord findRecordByStudentId(String studentId, CandidateType type) {
        if (studentId == null || studentId.isBlank()) return null;

        if (type == CandidateType.SSC) {
            PartylistMember pm = partylistMemberRepository.findAllByStudentId(studentId)
                    .stream().findFirst().orElse(null);
            if (pm != null) return toRecord(pm);

            Candidate c = candidateRepository.findAllByStudentId(studentId).stream()
                    .filter(x -> x.getPartylistId() != null)
                    .findFirst().orElse(null);
            if (c != null) return toRecord(c);

            return null;
        }

        // DEPARTMENT
        DepartmentMember dm = departmentMemberRepository.findByStudentId(studentId)
                .stream().findFirst().orElse(null);
        if (dm != null) return toRecord(dm);

        Candidate c = candidateRepository.findAllByStudentId(studentId).stream()
                .filter(x -> x.getDepartmentId() != null)
                .findFirst().orElse(null);
        if (c != null) return toRecord(c);

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
    // Scoped to ONE candidate type only — SSC uploads never
    // touch department rows, and vice versa.
    // =====================================================

    @Transactional
    public String uploadPersonalImage(UUID authUserId, CandidateType type, String imageType, MultipartFile file) throws IOException {
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

        if (type == CandidateType.SSC) {
            List<PartylistMember> partylistMembers = partylistMemberRepository.findAllByStudentId(studentId);
            for (PartylistMember pm : partylistMembers) {
                applyImage(pm, imageType, storagePath);
            }
            if (!partylistMembers.isEmpty()) {
                partylistMemberRepository.saveAll(partylistMembers);
                updatedAny = true;
            }

            List<Candidate> sscCandidates = candidateRepository.findAllByStudentId(studentId).stream()
                    .filter(c -> c.getPartylistId() != null)
                    .collect(Collectors.toList());
            for (Candidate c : sscCandidates) {
                applyImage(c, imageType, storagePath);
            }
            if (!sscCandidates.isEmpty()) {
                candidateRepository.saveAll(sscCandidates);
                updatedAny = true;
            }
        } else {
            List<DepartmentMember> departmentMembers = departmentMemberRepository.findByStudentId(studentId);
            for (DepartmentMember dm : departmentMembers) {
                applyImage(dm, imageType, storagePath);
            }
            if (!departmentMembers.isEmpty()) {
                departmentMemberRepository.saveAll(departmentMembers);
                updatedAny = true;
            }

            List<Candidate> deptCandidates = candidateRepository.findAllByStudentId(studentId).stream()
                    .filter(c -> c.getDepartmentId() != null)
                    .collect(Collectors.toList());
            for (Candidate c : deptCandidates) {
                applyImage(c, imageType, storagePath);
            }
            if (!deptCandidates.isEmpty()) {
                candidateRepository.saveAll(deptCandidates);
                updatedAny = true;
            }
        }

        if (!updatedAny) {
            throw new IllegalStateException(
                    "No " + (type == CandidateType.SSC ? "SSC partylist" : "department") +
                            " candidate record found for this account.");
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
    // DepartmentService when seeding a new member row) —
    // now scoped by type so an SSC image can never seed a
    // department row, and vice versa.
    // =====================================================

    public static class ExistingImages {
        public String photoImageUrl;
        public String backgroundImageUrl;
        public String campaignImageUrl;
    }

    public ExistingImages findExistingImages(String studentId, CandidateType type) {
        if (studentId == null || studentId.isBlank()) return null;

        if (type == CandidateType.SSC) {
            PartylistMember pm = partylistMemberRepository.findAllByStudentId(studentId).stream()
                    .filter(m -> hasAnyImage(m.getPhotoImageUrl(), m.getBackgroundImageUrl(), m.getCampaignImageUrl()))
                    .findFirst().orElse(null);
            if (pm != null) {
                return toImages(pm.getPhotoImageUrl(), pm.getBackgroundImageUrl(), pm.getCampaignImageUrl());
            }

            Candidate c = candidateRepository.findAllByStudentId(studentId).stream()
                    .filter(x -> x.getPartylistId() != null)
                    .filter(x -> hasAnyImage(x.getPhotoImageUrl(), x.getBackgroundImageUrl(), x.getCampaignImageUrl()))
                    .findFirst().orElse(null);
            if (c != null) {
                return toImages(c.getPhotoImageUrl(), c.getBackgroundImageUrl(), c.getCampaignImageUrl());
            }

            return null;
        }

        DepartmentMember dm = departmentMemberRepository.findByStudentId(studentId).stream()
                .filter(m -> hasAnyImage(m.getPhotoImageUrl(), m.getBackgroundImageUrl(), m.getCampaignImageUrl()))
                .findFirst().orElse(null);
        if (dm != null) {
            return toImages(dm.getPhotoImageUrl(), dm.getBackgroundImageUrl(), dm.getCampaignImageUrl());
        }

        Candidate c = candidateRepository.findAllByStudentId(studentId).stream()
                .filter(x -> x.getDepartmentId() != null)
                .filter(x -> hasAnyImage(x.getPhotoImageUrl(), x.getBackgroundImageUrl(), x.getCampaignImageUrl()))
                .findFirst().orElse(null);
        if (c != null) {
            return toImages(c.getPhotoImageUrl(), c.getBackgroundImageUrl(), c.getCampaignImageUrl());
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
    // GROUP (PARTYLIST / DEPARTMENT) INFO — scoped by type
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

    public GroupInfo getGroupInfo(UUID authUserId, CandidateType type) {
        CandidateRecord record = findRecordByAuthUserId(authUserId, type);
        if (record == null) {
            throw new IllegalStateException(
                    type == CandidateType.SSC
                            ? "You are not currently part of any SSC partylist."
                            : "You are not currently part of any department group.");
        }

        GroupInfo info = new GroupInfo();
        info.canEdit = record.isPresident();

        if (type == CandidateType.SSC) {
            if (record.partylistId == null) {
                throw new IllegalStateException("This SSC candidate is not linked to a partylist.");
            }

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

        // DEPARTMENT
        if (record.departmentId == null) {
            throw new IllegalStateException("This candidate is not linked to a department.");
        }

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

    @Transactional
    public void updateGroupInfo(UUID authUserId, CandidateType type, String description, MultipartFile poster, MultipartFile logo) throws IOException {
        CandidateRecord record = findRecordByAuthUserId(authUserId, type);
        if (record == null) {
            throw new IllegalStateException(
                    type == CandidateType.SSC
                            ? "You are not currently part of any SSC partylist."
                            : "You are not currently part of any department group.");
        }

        if (!record.isPresident()) {
            throw new IllegalArgumentException("Only the President can update this information.");
        }

        if (type == CandidateType.SSC) {
            if (record.partylistId == null) {
                throw new IllegalStateException("This SSC candidate is not linked to a partylist.");
            }

            Partylist p = partylistRepository.findById(record.partylistId)
                    .orElseThrow(() -> new IllegalStateException("Partylist not found."));

            if (description != null) p.setDescription(description);
            if (poster != null && !poster.isEmpty()) p.setPosterImageUrl(storageService.uploadFile(poster, "poster"));
            if (logo != null && !logo.isEmpty()) p.setPosterLogoUrl(storageService.uploadFile(logo, "logo"));

            partylistRepository.save(p);
            return;
        }

        // DEPARTMENT
        if (record.departmentId == null) {
            throw new IllegalStateException("This candidate is not linked to a department.");
        }

        Department d = departmentRepository.findById(record.departmentId)
                .orElseThrow(() -> new IllegalStateException("Department not found."));

        if (description != null) d.setDescription(description);
        if (poster != null && !poster.isEmpty()) d.setPosterImageUrl(storageService.uploadFile(poster, "poster"));
        if (logo != null && !logo.isEmpty()) d.setPosterLogoUrl(storageService.uploadFile(logo, "logo"));

        departmentRepository.save(d);
    }

    private String joinName(String first, String middle, String last) {
        StringBuilder sb = new StringBuilder();
        if (first != null) sb.append(first).append(" ");
        if (middle != null && !middle.isBlank()) sb.append(middle).append(" ");
        if (last != null) sb.append(last);
        return sb.toString().trim();
    }
}