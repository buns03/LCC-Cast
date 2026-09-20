package lccast.voting.system.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

@Configuration
public class SupabaseConfig {

    @Value("${supabase.url}")
    private String supabaseUrl;

    @Value("${supabase.anon-key}")
    private String supabaseAnonKey;

    public String getSupabaseUrl() {
        return supabaseUrl;
    }

    public String getSupabaseAnonKey() {
        return supabaseAnonKey;
    }

    @Value("${supabase.service-role-key}")
    private String supabaseServiceRoleKey;

    public String getSupabaseServiceRoleKey() {
        return supabaseServiceRoleKey;
    }
}