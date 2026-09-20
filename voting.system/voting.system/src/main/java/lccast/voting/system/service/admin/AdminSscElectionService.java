package lccast.voting.system.service.admin;

import jakarta.servlet.http.HttpServletRequest;
import lccast.voting.system.dto.superadmin.ElectionResponse;
import lccast.voting.system.model.Election;
import lccast.voting.system.model.ElectionCategory;
import lccast.voting.system.repository.CampusRepository;
import lccast.voting.system.service.superadmin.ElectionService;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class AdminSscElectionService {

    private final ElectionService electionService;
    private final CampusRepository campusRepository;

    public AdminSscElectionService(ElectionService electionService, CampusRepository campusRepository) {
        this.electionService = electionService;
        this.campusRepository = campusRepository;
    }

    public List<ElectionResponse> getActiveForCampus(UUID campusId) {
        return electionService.getByCampus(campusId).stream()
                .filter(e -> e.getCategory() == ElectionCategory.SSC)
                .map(electionService::toResponse)
                .toList();
    }

    public ElectionResponse getById(UUID id, UUID campusId) {
        Election election = electionService.getById(id);
        assertOwnedByCampus(election, campusId);
        return electionService.toResponse(election);
    }

    public ElectionResponse create(
            String title,
            UUID campusId,
            String schoolYear,
            Instant startAt,
            Instant endAt,
            UUID createdBy,
            List<UUID> partylistIds,
            HttpServletRequest request
    ) {
        requireCampus(campusId);

        if (partylistIds == null || partylistIds.size() < 2) {
            throw new IllegalArgumentException("SSC election requires at least two partylists.");
        }

        Election election = electionService.create(
                title,
                ElectionCategory.SSC,
                campusId,
                schoolYear,
                startAt,
                endAt,
                createdBy,
                partylistIds,
                null, // no departments, ever
                request
        );

        return electionService.toResponse(election);
    }

    public ElectionResponse update(
            UUID id,
            String title,
            UUID campusId,
            String schoolYear,
            Instant startAt,
            Instant endAt,
            List<UUID> partylistIds,
            HttpServletRequest request
    ) {
        Election existing = electionService.getById(id);
        assertOwnedByCampus(existing, campusId);

        if (partylistIds == null || partylistIds.size() < 2) {
            throw new IllegalArgumentException("SSC election requires at least two partylists.");
        }

        Election election = electionService.update(
                id,
                title,
                ElectionCategory.SSC,
                campusId,
                schoolYear,
                startAt,
                endAt,
                partylistIds,
                null,
                request
        );

        return electionService.toResponse(election);
    }

    public void archive(UUID id, UUID campusId, HttpServletRequest request) {
        assertOwnedByCampus(electionService.getById(id), campusId);
        electionService.archive(id, request);
    }

    public void delete(UUID id, UUID campusId, HttpServletRequest request) {
        assertOwnedByCampus(electionService.getById(id), campusId);
        electionService.delete(id, request);
    }

    private void requireCampus(UUID campusId) {
        campusRepository.findById(campusId)
                .orElseThrow(() -> new RuntimeException("Your campus could not be found."));
    }

    private void assertOwnedByCampus(Election election, UUID campusId) {
        if (election.getCampusId() == null || !election.getCampusId().equals(campusId)) {
            throw new RuntimeException("You are not authorized to access this election.");
        }
        if (election.getCategory() != ElectionCategory.SSC) {
            throw new RuntimeException("Only SSC elections can be managed here.");
        }
    }
}