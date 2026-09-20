package lccast.voting.system.dto;

import lccast.voting.system.model.ArchiveRecord;

import java.time.Instant;
import java.util.UUID;

public class ArchiveRecordDTO {

    private UUID id;
    private String entityType;
    private UUID entityId;
    private String entityName;
    private String archivedBy;
    private Instant archivedAt;

    public static ArchiveRecordDTO from(ArchiveRecord rec, String archivedByName) {
        ArchiveRecordDTO dto = new ArchiveRecordDTO();
        dto.id = rec.getId();
        dto.entityType = rec.getEntityType();
        dto.entityId = rec.getEntityId();
        dto.entityName = rec.getEntityName();
        dto.archivedBy = archivedByName;
        dto.archivedAt = rec.getArchivedAt();
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

    public String getArchivedBy() { return archivedBy; }
    public void setArchivedBy(String archivedBy) { this.archivedBy = archivedBy; }

    public Instant getArchivedAt() { return archivedAt; }
    public void setArchivedAt(Instant archivedAt) { this.archivedAt = archivedAt; }
}