package lccast.voting.system.service;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lccast.voting.system.dto.ApiResponse;
import lccast.voting.system.dto.VerifyEmailResponse;
import lccast.voting.system.integration.SupabaseAdminClient;
import lccast.voting.system.model.EmailOtp;
import lccast.voting.system.model.UserProfile;
import lccast.voting.system.model.Voter;
import lccast.voting.system.repository.EmailOtpRepository;
import lccast.voting.system.repository.UserProfileRepository;
import lccast.voting.system.repository.VoterRepository;
import lccast.voting.system.util.EmailMaskUtil;
import lccast.voting.system.util.OtpGenerator;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.UUID;

@Service
public class ForgotPasswordService {

    private static final int OTP_LENGTH = 6;
    private static final int OTP_EXPIRY_MINUTES = 10;
    private static final int VERIFIED_WINDOW_MINUTES = 15;
    private static final int MAX_ATTEMPTS = 5;

    private final VoterRepository voterRepository;
    private final UserProfileRepository userProfileRepository;
    private final EmailOtpRepository emailOtpRepository;
    private final PasswordEncoder passwordEncoder;
    private final SupabaseAdminClient supabaseAdminClient;
    private final JavaMailSender mailSender;
    private final TemplateEngine templateEngine;

    public ForgotPasswordService(
            VoterRepository voterRepository,
            UserProfileRepository userProfileRepository,
            EmailOtpRepository emailOtpRepository,
            PasswordEncoder passwordEncoder,
            SupabaseAdminClient supabaseAdminClient,
            JavaMailSender mailSender,
            TemplateEngine templateEngine) {
        this.voterRepository = voterRepository;
        this.userProfileRepository = userProfileRepository;
        this.emailOtpRepository = emailOtpRepository;
        this.passwordEncoder = passwordEncoder;
        this.supabaseAdminClient = supabaseAdminClient;
        this.mailSender = mailSender;
        this.templateEngine = templateEngine;
    }

    /**
     * Step 1: look the email up in `voters` first (students), then fall back
     * to `user_profiles` (SSC/department admins). This is the piece that
     * makes voter emails resolve against the voters table instead of the
     * Supabase auth user record.
     */
    public VerifyEmailResponse verifyEmail(String rawEmail) {
        if (rawEmail == null || rawEmail.isBlank()) {
            return VerifyEmailResponse.failure("Email is required.");
        }
        String email = rawEmail.trim().toLowerCase();

        UUID authUserId = resolveAuthUserId(email);
        if (authUserId == null) {
            // Deliberately generic - don't reveal whether the email exists.
            return VerifyEmailResponse.failure("The email could not be verified.");
        }

        String otp = OtpGenerator.generateNumeric(OTP_LENGTH);

        EmailOtp record = new EmailOtp();
        record.setAuthUserId(authUserId);
        record.setEmail(email);
        record.setOtpHash(passwordEncoder.encode(otp));
        record.setPurpose(EmailOtp.Purpose.FORGOT_PASSWORD);
        record.setExpiresAt(Instant.now().plus(OTP_EXPIRY_MINUTES, ChronoUnit.MINUTES));
        record.setAttempts(0);
        emailOtpRepository.save(record);

        sendOtpEmail(email, otp);

        return VerifyEmailResponse.success(EmailMaskUtil.mask(email));
    }

    public ApiResponse verifyOtp(String rawEmail, String otp) {
        if (rawEmail == null || otp == null) {
            return ApiResponse.failure("Invalid or expired OTP.");
        }
        String email = rawEmail.trim().toLowerCase();

        Optional<EmailOtp> maybe = emailOtpRepository
                .findTopByEmailIgnoreCaseAndPurposeOrderByCreatedAtDesc(
                        email, EmailOtp.Purpose.FORGOT_PASSWORD);

        if (maybe.isEmpty()) {
            return ApiResponse.failure("Please request a new OTP.");
        }

        EmailOtp record = maybe.get();

        if (record.getExpiresAt().isBefore(Instant.now())) {
            return ApiResponse.failure("This OTP has expired. Please request a new one.");
        }

        if (record.getAttempts() >= MAX_ATTEMPTS) {
            return ApiResponse.failure("Too many incorrect attempts. Please request a new OTP.");
        }

        if (!passwordEncoder.matches(otp, record.getOtpHash())) {
            record.setAttempts(record.getAttempts() + 1);
            emailOtpRepository.save(record);
            return ApiResponse.failure("Invalid or expired OTP.");
        }

        record.setVerifiedAt(Instant.now());
        emailOtpRepository.save(record);

        return ApiResponse.success("OTP verified.");
    }

    public ApiResponse resetPassword(String rawEmail, String otp, String newPassword) {
        if (rawEmail == null) {
            return ApiResponse.failure("Unable to change the password.");
        }
        String email = rawEmail.trim().toLowerCase();

        Optional<EmailOtp> maybe = emailOtpRepository
                .findTopByEmailIgnoreCaseAndPurposeOrderByCreatedAtDesc(
                        email, EmailOtp.Purpose.FORGOT_PASSWORD);

        if (maybe.isEmpty() || maybe.get().getVerifiedAt() == null) {
            return ApiResponse.failure("Please verify the OTP before changing your password.");
        }

        EmailOtp record = maybe.get();

        if (record.getVerifiedAt().isBefore(
                Instant.now().minus(VERIFIED_WINDOW_MINUTES, ChronoUnit.MINUTES))) {
            return ApiResponse.failure("This reset session has expired. Please start again.");
        }

        if (newPassword == null || newPassword.length() < 8) {
            return ApiResponse.failure("Password must be at least 8 characters.");
        }

        try {
            supabaseAdminClient.updatePassword(record.getAuthUserId(), newPassword);
        } catch (Exception e) {
            return ApiResponse.failure("Unable to change the password right now.");
        }

        // Staff accounts also track must_change_password - clear it if this
        // account has a user_profiles row. Voters don't have this flag.
        userProfileRepository.findByAuthUserId(record.getAuthUserId()).ifPresent(profile -> {
            profile.setMustChangePassword(false);
            userProfileRepository.save(profile);
        });

        emailOtpRepository.delete(record);

        return ApiResponse.success("Password changed successfully.");
    }

    private UUID resolveAuthUserId(String email) {
        Optional<Voter> voter = voterRepository.findByEmailIgnoreCase(email);
        if (voter.isPresent()) {
            return voter.get().getAuthUserId();
        }

        Optional<UserProfile> profile = userProfileRepository.findByEmailIgnoreCase(email);
        return profile.map(UserProfile::getAuthUserId).orElse(null);
    }

    private void sendOtpEmail(String toEmail, String otp) {
        Context context = new Context();
        context.setVariable("otpCode", otp);
        context.setVariable("expiresMinutes", OTP_EXPIRY_MINUTES);

        // Renders templates/otp-email.html (Thymeleaf) - see that file for
        // the design, matching the election-notice email.
        String html = templateEngine.process("otp-email", context);

        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            helper.setTo(toEmail);
            helper.setSubject("Your LCC Cast Password Reset Code");
            helper.setText(html, true);
            mailSender.send(message);
        } catch (MessagingException e) {
            throw new IllegalStateException("Failed to send OTP email", e);
        }
    }
}
