package com.example.users.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    private final FeatureAccessInterceptor featureAccessInterceptor;

    public WebMvcConfig(FeatureAccessInterceptor featureAccessInterceptor) {
        this.featureAccessInterceptor = featureAccessInterceptor;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        // Only the user-data endpoints. Login must stay reachable for every operator,
        // and the routing subrequest is anonymous by design.
        registry.addInterceptor(featureAccessInterceptor).addPathPatterns("/api/v1/users/**", "/api/v1/users");
    }
}
