package lccast.voting.system.event;

import org.springframework.context.ApplicationEvent;

public class VoteCastEvent extends ApplicationEvent {
    public VoteCastEvent(Object source) {
        super(source);
    }
}