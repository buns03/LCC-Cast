package lccast.voting.system.model;

public enum ElectionPhase {
    NOT_VISIBLE,   // more than 5 days before start, or already concluded
    UPCOMING,      // within 5 days of start, not yet started — read-only
    ONGOING,       // between start and end — voting enabled
    CONCLUDED,      // past end
    UNSCHEDULED
}