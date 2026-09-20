package lccast.voting.system.dto;

public class AdminRequest {
    private String lastName;
    private String firstName;
    private String middleName;
    private String contact;
    private String email;
    private String campus;        // campus name, e.g. "College"
    private String electionType;  // "SSC" | "DEPARTMENT"
    private String department;    // department code, e.g. "BSIS" (null for SSC)
    private String password;      // required on create, optional on update

    // getters + setters
    public String getLastName() { return lastName; }
    public void setLastName(String v) { this.lastName = v; }
    public String getFirstName() { return firstName; }
    public void setFirstName(String v) { this.firstName = v; }
    public String getMiddleName() { return middleName; }
    public void setMiddleName(String v) { this.middleName = v; }
    public String getContact() { return contact; }
    public void setContact(String v) { this.contact = v; }
    public String getEmail() { return email; }
    public void setEmail(String v) { this.email = v; }
    public String getCampus() { return campus; }
    public void setCampus(String v) { this.campus = v; }
    public String getElectionType() { return electionType; }
    public void setElectionType(String v) { this.electionType = v; }
    public String getDepartment() { return department; }
    public void setDepartment(String v) { this.department = v; }
    public String getPassword() { return password; }
    public void setPassword(String v) { this.password = v; }
}