package lccast.voting.system.model;

import jakarta.persistence.*;
import java.util.UUID;

@Entity
@Table(name = "security_settings")
public class SecuritySetting {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(name = "require_strong_password", nullable = false)
    private boolean requireStrongPassword = true;

    @Column(name = "auto_logout_enabled", nullable = false)
    private boolean autoLogoutEnabled = true;

    @Column(name = "auto_logout_minutes", nullable = false)
    private int autoLogoutMinutes = 30;

    public UUID getId() {
        return id;
    }

    public boolean isRequireStrongPassword() {
        return requireStrongPassword;
    }

    public void setRequireStrongPassword(boolean requireStrongPassword) {
        this.requireStrongPassword = requireStrongPassword;
    }

    public boolean isAutoLogoutEnabled() {
        return autoLogoutEnabled;
    }

    public void setAutoLogoutEnabled(boolean autoLogoutEnabled) {
        this.autoLogoutEnabled = autoLogoutEnabled;
    }

    public int getAutoLogoutMinutes() {
        return autoLogoutMinutes;
    }

    public void setAutoLogoutMinutes(int autoLogoutMinutes) {
        this.autoLogoutMinutes = autoLogoutMinutes;
    }
}