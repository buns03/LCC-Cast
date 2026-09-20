package lccast.voting.system.repository;

import lccast.voting.system.model.ArchiveRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.List;
import java.util.UUID;

public interface ArchiveRecordRepository
        extends JpaRepository<ArchiveRecord, UUID>,
        JpaSpecificationExecutor<ArchiveRecord> {

    List<ArchiveRecord> findByEntityTypeAndRestoredFalse(String entityType);
}