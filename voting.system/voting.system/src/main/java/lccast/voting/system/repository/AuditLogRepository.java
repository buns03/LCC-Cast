package lccast.voting.system.repository;

import lccast.voting.system.model.AuditAction;
import lccast.voting.system.model.AuditLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import lccast.voting.system.model.AuditAction;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface AuditLogRepository
        extends JpaRepository<AuditLog, UUID>, JpaSpecificationExecutor<AuditLog> {

    List<AuditLog> findAllByOrderByCreatedAtDesc();

    List<AuditLog> findByEntityTypeAndEntityIdOrderByCreatedAtDesc(
            String entityType,
            UUID entityId
    );

    List<AuditLog> findByActionOrderByCreatedAtDesc(
            AuditAction action
    );

    @Query("SELECT DISTINCT a.action FROM AuditLog a WHERE a.action IS NOT NULL")
    List<AuditAction> findDistinctActions();

    @Query("SELECT DISTINCT a.action FROM AuditLog a WHERE a.userId = :userId AND a.action IS NOT NULL")
    List<AuditAction> findDistinctActionsByUserId(@Param("userId") UUID userId);
}

