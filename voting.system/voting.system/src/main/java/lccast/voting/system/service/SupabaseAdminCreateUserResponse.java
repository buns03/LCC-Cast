package lccast.voting.system.service;

import java.util.UUID;

public class SupabaseAdminCreateUserResponse {

    private UUID id;
    private String email;

    public SupabaseAdminCreateUserResponse() {
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }
}