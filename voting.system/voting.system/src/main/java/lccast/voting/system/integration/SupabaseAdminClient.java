package lccast.voting.system.integration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.Map;
import java.util.UUID;

/**
 * Talks to Supabase's Admin REST API to update a user's password directly in
 * auth.users. Requires the SERVICE ROLE key (never the anon key) - keep it
 * server-side only, e.g. in an env var, and never log it.
 *
 * Docs: PUT {SUPABASE_URL}/auth/v1/admin/users/{user_id}
 *
 * Add to application.properties (or application.yml):
 *   supabase.url=https://<your-project-ref>.supabase.co
 *   supabase.service-role-key=${SUPABASE_SERVICE_ROLE_KEY}
 */
@Component
public class SupabaseAdminClient {

    private final RestTemplate restTemplate;
    private final String supabaseUrl;
    private final String serviceRoleKey;

    public SupabaseAdminClient(
            @Value("${supabase.url}") String supabaseUrl,
            @Value("${supabase.service-role-key}") String serviceRoleKey) {
        this.restTemplate = new RestTemplate();
        this.supabaseUrl = supabaseUrl;
        this.serviceRoleKey = serviceRoleKey;
    }

    public void updatePassword(UUID authUserId, String newPassword) {
        String url = supabaseUrl + "/auth/v1/admin/users/" + authUserId;

        HttpHeaders headers = new HttpHeaders();
        headers.set("apikey", serviceRoleKey);
        headers.setBearerAuth(serviceRoleKey);
        headers.setContentType(MediaType.APPLICATION_JSON);

        Map<String, Object> body = Map.of("password", newPassword);
        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);

        ResponseEntity<String> response =
                restTemplate.exchange(url, HttpMethod.PUT, entity, String.class);

        if (!response.getStatusCode().is2xxSuccessful()) {
            throw new IllegalStateException(
                    "Supabase admin password update failed: " + response.getStatusCode());
        }
    }
}
