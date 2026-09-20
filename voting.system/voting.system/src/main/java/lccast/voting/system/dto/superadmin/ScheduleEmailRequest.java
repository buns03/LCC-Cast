package lccast.voting.system.dto.superadmin;

import java.time.Instant;

public class ScheduleEmailRequest {
    private Instant scheduledAt;

    public Instant getScheduledAt() { return scheduledAt; }
    public void setScheduledAt(Instant scheduledAt) { this.scheduledAt = scheduledAt; }
}