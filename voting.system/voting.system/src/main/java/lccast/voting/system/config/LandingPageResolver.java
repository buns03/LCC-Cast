package lccast.voting.system.security;

import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.stereotype.Component;

import java.util.Set;

@Component
public class LandingPageResolver {

    public String resolve(Authentication auth) {
        if (auth == null || auth instanceof AnonymousAuthenticationToken || !auth.isAuthenticated()) {
            return "/login";
        }
        Set<String> roles = AuthorityUtils.authorityListToSet(auth.getAuthorities());

        if (roles.contains("ROLE_SUPERADMIN"))        return "/superadmin/dashboard";
        if (roles.contains("ROLE_ADMIN_SSC"))         return "/admin-ssc/dashboard";
        if (roles.contains("ROLE_ADMIN_DEPARTMENT"))  return "/admin-dept/dashboard";
        if (roles.contains("ROLE_CANDIDATE"))         return "/candidate/personal-information";
        if (roles.contains("ROLE_STUDENT"))           return "/voter/ssc-election";
        return "/login";
    }
}