package lccast.voting.system.dto;

import lccast.voting.system.model.TrashRecord;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

public class TrashRecordDTO {

    private static final int RETENTION_DAYS = 30;

    private UUID id;
    private String entityType;
    private UUID entityId;
    private String entityName;
    private String deletedBy;
    private Instant deletedAt;
    private long daysRemaining;

    public static TrashRecordDTO from(TrashRecord rec, String deletedByName) {
        TrashRecordDTO dto = new TrashRecordDTO();
        dto.id = rec.getId();
        dto.entityType = rec.getEntityType();
        dto.entityId = rec.getEntityId();
        dto.entityName = rec.getEntityName();
        dto.deletedBy = deletedByName;
        dto.deletedAt = rec.getDeletedAt();

        Instant expiresAt = rec.getDeletedAt().plus(Duration.ofDays(RETENTION_DAYS));
        long remaining = Duration.between(Instant.now(), expiresAt).toDays();
        dto.daysRemaining = Math.max(remaining, 0);

        return dto;
    }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public String getEntityType() { return entityType; }
    public void setEntityType(String entityType) { this.entityType = entityType; }

    public UUID getEntityId() { return entityId; }
    public void setEntityId(UUID entityId) { this.entityId = entityId; }

    public String getEntityName() { return entityName; }
    public void setEntityName(String entityName) { this.entityName = entityName; }

    public String getDeletedBy() { return deletedBy; }
    public void setDeletedBy(String deletedBy) { this.deletedBy = deletedBy; }

    public Instant getDeletedAt() { return deletedAt; }
    public void setDeletedAt(Instant deletedAt) { this.deletedAt = deletedAt; }

    public long getDaysRemaining() { return daysRemaining; }
    public void setDaysRemaining(long daysRemaining) { this.daysRemaining = daysRemaining; }
}