package lccast.voting.system.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import java.net.InetAddress;
import java.net.UnknownHostException;

import lccast.voting.system.model.AuditAction;
import lccast.voting.system.model.AuditLog;
import lccast.voting.system.model.UserRole;
import lccast.voting.system.repository.AuditLogRepository;
import lccast.voting.system.repository.UserProfileRepository;
import lccast.voting.system.model.UserProfile;
import lccast.voting.system.dto.AuditLogDTO;
import org.springframework.messaging.simp.SimpMessagingTemplate;


import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.UUID;

@Service
public class AuditLogService {

    private final AuditLogRepository auditLogRepository;
    private final ObjectMapper objectMapper;
    private final UserProfileRepository userProfileRepository;
    private final SimpMessagingTemplate messagingTemplate;


    public AuditLogService(
            AuditLogRepository auditLogRepository,
            ObjectMapper objectMapper,
            UserProfileRepository userProfileRepository,
            SimpMessagingTemplate messagingTemplate
    ) {
        this.auditLogRepository = auditLogRepository;
        this.objectMapper = objectMapper;
        this.userProfileRepository = userProfileRepository;
        this.messagingTemplate = messagingTemplate;
    }

    public void log(
            HttpServletRequest request,
            AuditAction action,
            String entityType,
            UUID entityId,
            String description,
            Map<String, Object> metadata
    ) {

        AuditLog auditLog = new AuditLog();

        HttpSession session = request.getSession(false);

        if (session != null) {

            Object userId =
                    session.getAttribute("userId");

            if (userId != null) {
                try {
                    auditLog.setUserId(
                            UUID.fromString(
                                    userId.toString()
                            )
                    );
                } catch (IllegalArgumentException ignored) {
                    // Leave userId null if session value is invalid.
                }
            }

            Object role =
                    session.getAttribute("role");

            if (role != null) {
                try {
                    auditLog.setRole(
                            UserRole.valueOf(
                                    role.toString()
                            )
                    );
                } catch (IllegalArgumentException ignored) {
                    // Leave role null if invalid.
                }
            }
        }

        UUID loggedUserId = auditLog.getUserId();

        String userName = getUserName(loggedUserId);

        auditLog.setAction(action);
        auditLog.setEntityType(entityType);
        auditLog.setEntityId(entityId);
        auditLog.setDescription(
                userName + " " + description
        );

        if (metadata != null) {
            try {
                auditLog.setMetadata(
                        objectMapper.writeValueAsString(
                                metadata
                        )
                );
            } catch (JsonProcessingException e) {
                throw new RuntimeException(
                        "Failed to create audit metadata.",
                        e
                );
            }
        }

        if (request != null) {

            try {
                auditLog.setIpAddress(
                        InetAddress.getByName(
                                getClientIp(request)
                        )
                );
            } catch (UnknownHostException e) {
                throw new RuntimeException(
                        "Invalid client IP address.",
                        e
                );
            }

            auditLog.setUserAgent(
                    request.getHeader("User-Agent")
            );
        }

        AuditLog saved = auditLogRepository.save(auditLog);

        broadcastAction(saved, userName);
    }

    private void broadcastAction(AuditLog saved, String userName) {
        try {
            AuditLogDTO dto = AuditLogDTO.from(saved, userName, objectMapper);
            messagingTemplate.convertAndSend("/topic/history/actions", dto);
        } catch (Exception e) {
            // Never let a broadcast failure roll back or fail the audit write itself.
        }
    }

    public String getUserName(UUID userId) {

        if (userId == null) {
            return "Unknown User";
        }

        return userProfileRepository
                .findByAuthUserId(userId)
                .map(user -> {

                    String firstName = user.getFirstName() != null
                            ? user.getFirstName()
                            : "";

                    String lastName = user.getLastName() != null
                            ? user.getLastName()
                            : "";

                    String fullName = (firstName + " " + lastName).trim();

                    return fullName.isBlank()
                            ? "Unknown User"
                            : fullName;
                })
                .orElse("Unknown User");
    }

    private String getClientIp(
            HttpServletRequest request
    ) {

        String forwarded =
                request.getHeader("X-Forwarded-For");

        if (
                forwarded != null &&
                        !forwarded.isBlank()
        ) {
            return forwarded.split(",")[0].trim();
        }

        String realIp =
                request.getHeader("X-Real-IP");

        if (
                realIp != null &&
                        !realIp.isBlank()
        ) {
            return realIp;
        }

        return request.getRemoteAddr();
    }
}

