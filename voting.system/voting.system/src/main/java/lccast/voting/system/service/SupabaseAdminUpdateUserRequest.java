package lccast.voting.system.service;

public class SupabaseAdminUpdateUserRequest {

    private String password;

    public SupabaseAdminUpdateUserRequest() {
    }

    public SupabaseAdminUpdateUserRequest(String password) {
        this.password = password;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }
}