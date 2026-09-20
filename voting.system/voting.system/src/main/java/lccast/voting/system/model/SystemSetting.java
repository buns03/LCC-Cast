package lccast.voting.system.model;

import jakarta.persistence.*;
import java.util.UUID;

@Entity
@Table(name = "system_settings")
public class SystemSetting {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(name = "system_name", nullable = false)
    private String systemName;

    @Column(name = "academic_year", nullable = false)
    private String academicYear;

    @Column(name = "description", columnDefinition = "text")
    private String description;

    @Column(name = "logo_path")
    private String logoPath;

    public UUID getId() {
        return id;
    }

    public String getSystemName() {
        return systemName;
    }

    public void setSystemName(String systemName) {
        this.systemName = systemName;
    }

    public String getAcademicYear() {
        return academicYear;
    }

    public void setAcademicYear(String academicYear) {
        this.academicYear = academicYear;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getLogoPath() {
        return logoPath;
    }

    public void setLogoPath(String logoPath) {
        this.logoPath = logoPath;
    }
}