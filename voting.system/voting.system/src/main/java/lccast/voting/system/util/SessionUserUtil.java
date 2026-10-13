package lccast.voting.system.util;

import jakarta.servlet.http.HttpSession;
import java.util.UUID;

public final class SessionUserUtil {

    private SessionUserUtil() {}

    public static UUID getAuthUserId(HttpSession session) {
        if (session == null) return null;

        for (String key : new String[] { "userId", "authUserId" }) {
            Object value = session.getAttribute(key);
            if (value instanceof UUID uuid) return uuid;
            if (value != null) {
                try {
                    return UUID.fromString(value.toString());
                } catch (IllegalArgumentException ignored) { }
            }
        }
        return null;
    }
}