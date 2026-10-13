package lccast.voting.system.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    private final ProfilePictureInterceptor profilePictureInterceptor;

    public WebMvcConfig(ProfilePictureInterceptor profilePictureInterceptor) {
        this.profilePictureInterceptor = profilePictureInterceptor;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(profilePictureInterceptor)
                .addPathPatterns("/superadmin/**")
                .excludePathPatterns("/superadmin/api/**");
    }
}