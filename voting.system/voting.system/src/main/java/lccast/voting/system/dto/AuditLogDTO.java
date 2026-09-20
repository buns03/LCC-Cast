package lccast.voting.system.dto;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lccast.voting.system.model.AuditAction;
import lccast.voting.system.model.AuditLog;
import lccast.voting.system.model.UserRole;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class AuditLogDTO {

    private UUID id;
    private String user;
    private String initials;
    private UUID userId;
    private String role;
    private String roleClass;
    private String action;
    private String actionClass;
    private String icon;
    private String description;
    private List<AuditDetailDTO> details;
    private Instant createdAt;

    private static final Map<UserRole, String> ROLE_CLASS = Map.of(
            UserRole.STUDENT, "student",
            UserRole.CANDIDATE, "candidate",
            UserRole.ADMIN, "admin",
            UserRole.SUPERADMIN, "superadmin"
    );

    private static final Map<AuditAction, String> ACTION_CLASS = Map.ofEntries(
            Map.entry(AuditAction.LOGIN, "login"),
            Map.entry(AuditAction.LOGOUT, "login"),
            Map.entry(AuditAction.PASSWORD_CHANGED, "update"),
            Map.entry(AuditAction.CREATE, "create"),
            Map.entry(AuditAction.UPDATE, "update"),
            Map.entry(AuditAction.DELETE, "delete"),
            Map.entry(AuditAction.ARCHIVE, "archive"),
            Map.entry(AuditAction.RESTORE, "restore"),
            Map.entry(AuditAction.VOTE, "vote"),
            Map.entry(AuditAction.IMPORT, "create"),
            Map.entry(AuditAction.EXPORT, "update"),
            Map.entry(AuditAction.ACTIVATE, "update"),
            Map.entry(AuditAction.DEACTIVATE, "update"),
            Map.entry(AuditAction.OTHER, "update")
    );

    private static final Map<AuditAction, String> ACTION_ICON = Map.ofEntries(
            Map.entry(AuditAction.LOGIN, "bi-box-arrow-in-right"),
            Map.entry(AuditAction.LOGOUT, "bi-box-arrow-right"),
            Map.entry(AuditAction.PASSWORD_CHANGED, "bi-key"),
            Map.entry(AuditAction.CREATE, "bi-plus-circle"),
            Map.entry(AuditAction.UPDATE, "bi-pencil-square"),
            Map.entry(AuditAction.DELETE, "bi-trash3"),
            Map.entry(AuditAction.ARCHIVE, "bi-archive"),
            Map.entry(AuditAction.RESTORE, "bi-arrow-counterclockwise"),
            Map.entry(AuditAction.VOTE, "bi-check2-square"),
            Map.entry(AuditAction.IMPORT, "bi-upload"),
            Map.entry(AuditAction.EXPORT, "bi-download"),
            Map.entry(AuditAction.ACTIVATE, "bi-toggle-on"),
            Map.entry(AuditAction.DEACTIVATE, "bi-toggle-off"),
            Map.entry(AuditAction.OTHER, "bi-activity")
    );

    public static AuditLogDTO from(AuditLog log, String userName, ObjectMapper objectMapper) {
        AuditLogDTO dto = new AuditLogDTO();
        dto.id = log.getId();
        dto.user = userName;
        dto.initials = initialsOf(userName);
        dto.userId = log.getUserId();
        dto.role = log.getRole() != null ? log.getRole().name() : "UNKNOWN";
        dto.roleClass = log.getRole() != null
                ? ROLE_CLASS.getOrDefault(log.getRole(), "student")
                : "student";
        dto.action = humanizeAction(log.getAction());
        dto.actionClass = ACTION_CLASS.getOrDefault(log.getAction(), "update");
        dto.icon = ACTION_ICON.getOrDefault(log.getAction(), "bi-activity");
        dto.description = log.getDescription();
        dto.createdAt = log.getCreatedAt();
        dto.details = parseDetails(log.getMetadata(), objectMapper);
        return dto;
    }

    private static String humanizeAction(AuditAction action) {
        if (action == null) return "Action";
        String[] words = action.name().toLowerCase().split("_");
        StringBuilder sb = new StringBuilder();
        for (String w : words) {
            sb.append(Character.toUpperCase(w.charAt(0))).append(w.substring(1)).append(" ");
        }
        return sb.toString().trim();
    }

    private static String initialsOf(String name) {
        if (name == null || name.isBlank()) return "??";
        String[] parts = name.trim().split("\\s+");
        String first = parts.length > 0 ? parts[0].substring(0, 1) : "";
        String last = parts.length > 1 ? parts[parts.length - 1].substring(0, 1) : "";
        return (first + last).toUpperCase();
    }

    /**
     * Reads the "details" array out of the audit_logs.metadata jsonb column.
     * Expects metadata shaped like: {"details": [{"target":"...","field":"...","from":"...","to":"..."}]}
     * Any metadata written without that "details" key just yields an empty list —
     * the frontend already handles zero details (renders "—" instead of the View Details button).
     */
    private static List<AuditDetailDTO> parseDetails(String metadataJson, ObjectMapper objectMapper) {
        List<AuditDetailDTO> result = new ArrayList<>();
        if (metadataJson == null || metadataJson.isBlank()) return result;

        try {
            JsonNode root = objectMapper.readTree(metadataJson);
            JsonNode detailsNode = root.get("details");
            if (detailsNode == null || !detailsNode.isArray()) return result;

            for (JsonNode d : detailsNode) {
                result.add(new AuditDetailDTO(
                        textOrNull(d, "target"),
                        textOrNull(d, "field"),
                        textOrNull(d, "from"),
                        textOrNull(d, "to")
                ));
            }
        } catch (Exception ignored) {
            // malformed metadata -> treat as no details rather than failing the whole list
        }
        return result;
    }

    private static String textOrNull(JsonNode node, String field) {
        JsonNode v = node.get(field);
        return v == null || v.isNull() ? null : v.asText();
    }

    // getters + setters

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public String getUser() { return user; }
    public void setUser(String user) { this.user = user; }

    public String getInitials() { return initials; }
    public void setInitials(String initials) { this.initials = initials; }

    public UUID getUserId() { return userId; }
    public void setUserId(UUID userId) { this.userId = userId; }

    public String getRole() { return role; }
    public void setRole(String role) { this.role = role; }

    public String getRoleClass() { return roleClass; }
    public void setRoleClass(String roleClass) { this.roleClass = roleClass; }

    public String getAction() { return action; }
    public void setAction(String action) { this.action = action; }

    public String getActionClass() { return actionClass; }
    public void setActionClass(String actionClass) { this.actionClass = actionClass; }

    public String getIcon() { return icon; }
    public void setIcon(String icon) { this.icon = icon; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public List<AuditDetailDTO> getDetails() { return details; }
    public void setDetails(List<AuditDetailDTO> details) { this.details = details; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}