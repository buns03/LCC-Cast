package lccast.voting.system.util;

/**
 * Masks an email address for display, e.g. "firstlast@gmail.com" -> "f********t@gmail.com".
 * Only the first and last character of the local part (before the @) stay visible.
 */
public final class EmailMaskUtil {

    private EmailMaskUtil() {
    }

    public static String mask(String email) {
        if (email == null) {
            return null;
        }

        int at = email.indexOf('@');
        if (at <= 0) {
            return email;
        }

        String local = email.substring(0, at);
        String domain = email.substring(at);

        if (local.length() == 1) {
            return local + domain;
        }

        if (local.length() == 2) {
            return local.charAt(0) + "*" + domain;
        }

        String masked = local.charAt(0)
                + "*".repeat(local.length() - 2)
                + local.charAt(local.length() - 1);

        return masked + domain;
    }
}
