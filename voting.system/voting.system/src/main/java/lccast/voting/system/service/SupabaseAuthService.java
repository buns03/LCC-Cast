package lccast.voting.system.service;

import lccast.voting.system.config.SupabaseConfig;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

@Service
public class SupabaseAuthService {

    private final SupabaseConfig supabaseConfig;
    private final RestClient restClient;

    public SupabaseAuthService(SupabaseConfig supabaseConfig) {

        this.supabaseConfig = supabaseConfig;

        this.restClient = RestClient.builder()
                .baseUrl(supabaseConfig.getSupabaseUrl())
                .build();
    }

    public SupabaseAuthResponse login(String email, String password) {

        SupabaseLoginRequest request = new SupabaseLoginRequest(email, password);

        try {
            return restClient
                    .post()
                    .uri("/auth/v1/token?grant_type=password")
                    .header("apikey", supabaseConfig.getSupabaseAnonKey())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(request)
                    .retrieve()
                    .body(SupabaseAuthResponse.class);
        } catch (org.springframework.web.client.HttpClientErrorException e) {
            System.err.println("SUPABASE LOGIN FAILED for " + email + ": "
                    + e.getStatusCode() + " " + e.getResponseBodyAsString());
            throw e;
        }
    }

    public SupabaseAdminCreateUserResponse createUser(
            String email,
            String password) {

        SupabaseAdminCreateUserRequest request =
                new SupabaseAdminCreateUserRequest(email, password, true);

        return restClient
                .post()
                .uri("/auth/v1/admin/users")
                .header(
                        "apikey",
                        supabaseConfig.getSupabaseServiceRoleKey()
                )
                .header(
                        "Authorization",
                        "Bearer " + supabaseConfig.getSupabaseServiceRoleKey()
                )
                .contentType(MediaType.APPLICATION_JSON)
                .body(request)
                .retrieve()
                .body(SupabaseAdminCreateUserResponse.class);
    }

    public void updatePassword(
            String authUserId,
            String newPassword) {

        SupabaseAdminUpdateUserRequest request =
                new SupabaseAdminUpdateUserRequest(newPassword);

        restClient
                .put()
                .uri("/auth/v1/admin/users/" + authUserId)
                .header(
                        "apikey",
                        supabaseConfig.getSupabaseServiceRoleKey()
                )
                .header(
                        "Authorization",
                        "Bearer " + supabaseConfig.getSupabaseServiceRoleKey()
                )
                .contentType(MediaType.APPLICATION_JSON)
                .body(request)
                .retrieve()
                .toBodilessEntity();
    }

    public void updateEmail(
            String authUserId,
            String newEmail) {

        restClient
                .put()
                .uri("/auth/v1/admin/users/" + authUserId)
                .header(
                        "apikey",
                        supabaseConfig.getSupabaseServiceRoleKey()
                )
                .header(
                        "Authorization",
                        "Bearer " + supabaseConfig.getSupabaseServiceRoleKey()
                )
                .contentType(MediaType.APPLICATION_JSON)
                .body(java.util.Map.of("email", newEmail))
                .retrieve()
                .toBodilessEntity();
    }

    public void setUserBanStatus(String authUserId, String banDuration) {
        restClient
                .put()
                .uri("/auth/v1/admin/users/" + authUserId)
                .header("apikey", supabaseConfig.getSupabaseServiceRoleKey())
                .header("Authorization", "Bearer " + supabaseConfig.getSupabaseServiceRoleKey())
                .contentType(MediaType.APPLICATION_JSON)
                .body(java.util.Map.of("ban_duration", banDuration))
                .retrieve()
                .toBodilessEntity();
    }

    public void deleteUser(String authUserId) {
        try {
            restClient
                    .delete()
                    .uri("/auth/v1/admin/users/" + authUserId)
                    .header("apikey", supabaseConfig.getSupabaseServiceRoleKey())
                    .header("Authorization", "Bearer " + supabaseConfig.getSupabaseServiceRoleKey())
                    .retrieve()
                    .toBodilessEntity();
            System.out.println("SUPABASE AUTH USER DELETED: " + authUserId);
        } catch (org.springframework.web.client.HttpClientErrorException.NotFound e) {
            System.out.println("SUPABASE AUTH USER ALREADY GONE: " + authUserId);
        } catch (Exception e) {
            System.err.println("SUPABASE DELETE USER FAILED: " + authUserId + " — " + e.getMessage());
            throw e;
        }
    }
}