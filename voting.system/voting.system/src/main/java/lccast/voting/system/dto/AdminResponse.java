package lccast.voting.system.dto;

import java.time.format.DateTimeFormatter;
import java.time.ZoneId;
import lccast.voting.system.model.UserProfile;

public class AdminResponse {
    public String id;
    public String lastName;
    public String firstName;
    public String middleName;
    public String contact;
    public String email;
    public String campus;
    public String electionType;
    public String department;
    public String status;
    public String dateAssigned;
    public String lastUpdated;
    public String profilePictureUrl;

    private static final DateTimeFormatter FMT =
            DateTimeFormatter.ofPattern("MMMM d, yyyy");

    public static AdminResponse from(UserProfile p, String campusName, String departmentCode) {
        AdminResponse r = new AdminResponse();
        r.id = p.getId().toString();
        r.lastName = p.getLastName();
        r.firstName = p.getFirstName();
        r.middleName = p.getMiddleName();
        r.contact = p.getContactNumber();
        r.email = p.getEmail();
        r.campus = campusName;
        r.electionType = p.getAdminType();
        r.department = departmentCode;
        r.status = p.isActive() ? "Active" : "Inactive";
        r.dateAssigned = p.getCreatedAt() != null
                ? FMT.format(p.getCreatedAt().atZone(ZoneId.systemDefault())) : "";
        r.lastUpdated = p.getUpdatedAt() != null
                ? FMT.format(p.getUpdatedAt().atZone(ZoneId.systemDefault())) : "";
        r.profilePictureUrl = p.getProfilePictureUrl() != null
                ? "/files/" + p.getProfilePictureUrl() : null;
        // NOTE: password intentionally not returned. See frontend note below.
        return r;
    }
}