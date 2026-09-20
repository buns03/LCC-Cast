package lccast.voting.system.service;

import lccast.voting.system.model.Campus;
import lccast.voting.system.model.UserProfile;
import lccast.voting.system.model.UserRole;
import lccast.voting.system.model.Voter;
import lccast.voting.system.repository.CampusRepository;
import lccast.voting.system.repository.UserProfileRepository;
import lccast.voting.system.repository.VoterRepository;
import lccast.voting.system.model.Department;
import lccast.voting.system.repository.DepartmentRepository;

import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.UUID;

@Service
public class LoginService {

    private final SupabaseAuthService supabaseAuthService;
    private final UserProfileRepository userProfileRepository;
    private final VoterRepository voterRepository;
    private final CampusRepository campusRepository;
    private final DepartmentRepository departmentRepository;

    public LoginService(
            SupabaseAuthService supabaseAuthService,
            UserProfileRepository userProfileRepository,
            VoterRepository voterRepository,
            CampusRepository campusRepository,
            DepartmentRepository departmentRepository) {

        this.supabaseAuthService = supabaseAuthService;
        this.userProfileRepository = userProfileRepository;
        this.voterRepository = voterRepository;
        this.campusRepository = campusRepository;
        this.departmentRepository = departmentRepository;
    }

    public LoginResult login(
            String login,
            String password) {

        try {

            // =====================================================
            // 1. FIND USER PROFILE
            // =====================================================

            UserProfile userProfile =
                    userProfileRepository
                            .findBySchoolId(login)
                            .orElse(null);

            if (userProfile == null) {

                userProfile =
                        userProfileRepository
                                .findByEmail(login)
                                .orElse(null);
            }

            if (userProfile == null) {

                System.out.println(
                        "LOGIN FAILED: User not found: " + login
                );

                return invalidResult(login);
            }


            // =====================================================
            // 2. GET EMAIL
            // =====================================================

            String email = userProfile.getEmail();

            if (email == null || email.isBlank()) {

                System.out.println(
                        "LOGIN FAILED: User has no email: " + login
                );

                return invalidResult(login);
            }


            // =====================================================
            // 3. SUPABASE AUTHENTICATION
            // =====================================================

            SupabaseAuthResponse authResponse =
                    supabaseAuthService.login(
                            email,
                            password
                    );

            if (authResponse == null ||
                    authResponse.getUser() == null) {

                return invalidResult(email);
            }


            // =====================================================
            // 4. GET AUTHENTICATED SUPABASE USER
            // =====================================================

            SupabaseUser supabaseUser =
                    authResponse.getUser();


            if (userProfile.getAuthUserId() == null ||
                    !userProfile.getAuthUserId()
                            .equals(supabaseUser.getId())) {

                System.out.println(
                        "LOGIN FAILED: Auth user does not match profile."
                );

                return invalidResult(login);
            }


            // =====================================================
            // 5. GET ROLE
            // =====================================================

            UserRole userRole =
                    userProfile.getRole();

            if (userRole == null) {
                return invalidResult(email);
            }

            String role = userRole.name();


            // =====================================================
            // 6. UPDATE LOGIN INFORMATION
            // =====================================================

            userProfile.setActive(true);

            userProfile.setLastLoginAt(
                    Instant.now()
            );

            userProfileRepository.save(
                    userProfile
            );


            // =====================================================
            // 7. GET VOTER INFORMATION
            //
            // Only VOTER accounts need:
            // - Program/Course
            // - Campus
            // =====================================================

            String programCourse = null;
            String campus = null;

            if ("STUDENT".equals(role)) {

                UUID authUserId =
                        supabaseUser.getId();

                Voter voter =
                        voterRepository
                                .findByAuthUserId(authUserId)
                                .orElse(null);

                if (voter == null) {

                    System.out.println(
                            "LOGIN WARNING: No voter record found for auth user: "
                                    + authUserId
                    );

                } else {

                    programCourse =
                            voter.getProgramCourse();

                    if (voter.getCampusId() != null) {

                        Campus campusEntity =
                                campusRepository
                                        .findById(
                                                voter.getCampusId()
                                        )
                                        .orElse(null);

                        if (campusEntity != null) {

                            campus =
                                    campusEntity.getName();
                        }
                    }
                }
            } else if ("ADMIN".equals(role)) {

                if (userProfile.getCampusId() != null) {
                    Campus campusEntity =
                            campusRepository.findById(userProfile.getCampusId()).orElse(null);
                    if (campusEntity != null) {
                        campus = campusEntity.getName();
                    }
                }

                if ("DEPARTMENT".equals(userProfile.getAdminType())
                        && userProfile.getAdminDepartment() != null) {

                    programCourse = userProfile.getAdminDepartment();
                } else {
                    programCourse = "Supreme Student Council";
                }
            }


            // =====================================================
            // 8. RETURN LOGIN RESULT
            // =====================================================

            return new LoginResult(
                    "SUCCESS",
                    supabaseUser.getId().toString(),
                    supabaseUser.getEmail(),
                    userProfile.getFirstName(),
                    role,
                    userProfile.isMustChangePassword(),
                    programCourse,
                    campus
            );

        } catch (Exception e) {

            System.out.println(
                    "Login error: " + e.getMessage()
            );

            e.printStackTrace();

            return invalidResult(login);
        }
    }


    // =========================================================
    // INVALID LOGIN RESULT
    // =========================================================

    private LoginResult invalidResult(
            String login) {

        return new LoginResult(
                "INVALID",
                null,
                login,
                null,
                null,
                false,
                null,
                null
        );
    }
}

