package lccast.voting.system.service;

import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Service
public class RealtimeBroadcastService {

    private final SimpMessagingTemplate messagingTemplate;

    public RealtimeBroadcastService(SimpMessagingTemplate messagingTemplate) {
        this.messagingTemplate = messagingTemplate;
    }

    private Object payload(UUID campusId, String extraKey, Object extraVal) {
        Map<String, Object> m = new HashMap<>();
        m.put("campusId", campusId);
        m.put("ts", System.currentTimeMillis());
        if (extraKey != null) m.put(extraKey, extraVal);
        return m; // returned as Object — kills the overload ambiguity at every call site below
    }

    public void electionsChanged(UUID campusId) {
        messagingTemplate.convertAndSend("/topic/elections", payload(campusId, null, null));
        if (campusId != null) messagingTemplate.convertAndSend("/topic/elections/campus/" + campusId, payload(campusId, null, null));
    }

    public void partylistsChanged(UUID campusId) {
        messagingTemplate.convertAndSend("/topic/partylists", payload(campusId, null, null));
        if (campusId != null) messagingTemplate.convertAndSend("/topic/partylists/campus/" + campusId, payload(campusId, null, null));
    }

    public void departmentsChanged(UUID campusId) {
        messagingTemplate.convertAndSend("/topic/departments", payload(campusId, null, null));
        if (campusId != null) messagingTemplate.convertAndSend("/topic/departments/campus/" + campusId, payload(campusId, null, null));
    }

    public void dashboardChanged(UUID campusId, String programCode) {
        messagingTemplate.convertAndSend("/topic/dashboard", payload(campusId, "program", programCode));
        if (campusId != null) {
            String topic = (programCode == null || programCode.isBlank())
                    ? "/topic/dashboard/" + campusId
                    : "/topic/dashboard/" + campusId + "/" + programCode;
            messagingTemplate.convertAndSend(topic, payload(campusId, "program", programCode));
        }
    }

    public void historyChanged(String section) {
        messagingTemplate.convertAndSend("/topic/history/" + section, payload(null, null, null));
    }
}