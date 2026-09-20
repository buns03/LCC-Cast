package lccast.voting.system.service;

public class SupabaseAdminCreateUserRequest {

    private String email;
    private String password;
    private boolean email_confirm;

    public SupabaseAdminCreateUserRequest() {
    }

    public SupabaseAdminCreateUserRequest(String email, String password, boolean email_confirm) {
        this.email = email;
        this.password = password;
        this.email_confirm = email_confirm;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public boolean isEmail_confirm() {
        return email_confirm;
    }

    public void setEmail_confirm(boolean email_confirm) {
        this.email_confirm = email_confirm;
    }
}