package dev.zulu.media.config;

import dev.zulu.media.api.ZuluMediaAuthenticationInterceptor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class ZuluMediaWebConfiguration implements WebMvcConfigurer {

    private final ZuluMediaAuthenticationInterceptor authenticationInterceptor;

    public ZuluMediaWebConfiguration(ZuluMediaAuthenticationInterceptor authenticationInterceptor) {
        this.authenticationInterceptor = authenticationInterceptor;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(authenticationInterceptor)
            .addPathPatterns("/plugins/zulu-media/v1/**");
    }
}
