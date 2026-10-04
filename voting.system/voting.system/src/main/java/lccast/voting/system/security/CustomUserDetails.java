package lccast.voting.system.security;

import lccast.voting.system.model.UserProfile;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.List;

public class CustomUserDetails implements UserDetails {

    private final UserProfile profile;

    public CustomUserDetails(UserProfile profile) {
        this.profile = profile;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof CustomUserDetails other)) return false;
        return getUsername() != null && getUsername().equals(other.getUsername());
    }

    @Override
    public int hashCode() {
        return getUsername() != null ? getUsername().hashCode() : 0;
    }

    public UserProfile getProfile() {
        return profile;
    }

    @Override
    public List<GrantedAuthority> getAuthorities() {
        List<GrantedAuthority> authorities = new java.util.ArrayList<>();
        authorities.add(new SimpleGrantedAuthority("ROLE_" + profile.getRole().name()));

        if (profile.getRole() == lccast.voting.system.model.UserRole.ADMIN
                && profile.getAdminType() != null) {
            authorities.add(new SimpleGrantedAuthority("ROLE_ADMIN_" + profile.getAdminType()));
        }

        return authorities;
    }

    @Override
    public String getPassword() {
        return null; // Supabase handles password verification, not Spring Security
    }

    @Override
    public String getUsername() {
        return profile.getSchoolId();
    }

    @Override
    public boolean isAccountNonExpired() { return true; }

    @Override
    public boolean isAccountNonLocked() { return true; }

    @Override
    public boolean isCredentialsNonExpired() { return true; }

    @Override
    public boolean isEnabled() { return profile.isActive(); }
}