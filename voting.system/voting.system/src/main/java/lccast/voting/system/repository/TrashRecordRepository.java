package lccast.voting.system.repository;

import lccast.voting.system.model.TrashRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.List;
import java.util.UUID;

public interface TrashRecordRepository
        extends JpaRepository<TrashRecord, UUID>,
        JpaSpecificationExecutor<TrashRecord> {

    List<TrashRecord> findByEntityTypeAndRestoredFalse(String entityType);
}