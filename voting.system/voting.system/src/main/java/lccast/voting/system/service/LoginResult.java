
package lccast.voting.system.service;

public class LoginResult {

    private String status;
    private String userId;
    private String email;
    private String firstName;
    private String role;
    private boolean mustChangePassword;

    private String programCourse;
    private String campus;

    public LoginResult() {
    }

    public LoginResult(
            String status,
            String userId,
            String email,
            String firstName,
            String role,
            boolean mustChangePassword,
            String programCourse,
            String campus) {

        this.status = status;
        this.userId = userId;
        this.email = email;
        this.firstName = firstName;
        this.role = role;
        this.mustChangePassword = mustChangePassword;
        this.programCourse = programCourse;
        this.campus = campus;
    }

    public String getStatus() {
        return status;
    }

    public String getUserId() {
        return userId;
    }

    public String getEmail() {
        return email;
    }

    public String getFirstName() {
        return firstName;
    }

    public String getRole() {
        return role;
    }

    public boolean isMustChangePassword() {
        return mustChangePassword;
    }

    public String getProgramCourse() {
        return programCourse;
    }

    public String getCampus() {
        return campus;
    }
}

