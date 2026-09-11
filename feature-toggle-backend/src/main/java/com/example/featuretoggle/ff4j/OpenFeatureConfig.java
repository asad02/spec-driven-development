package com.example.featuretoggle.ff4j;

import dev.openfeature.sdk.Client;
import dev.openfeature.sdk.OpenFeatureAPI;
import org.ff4j.FF4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenFeatureConfig {

    @Bean
    OpenFeatureAPI openFeatureAPI(FF4j ff4j) {
        OpenFeatureAPI api = OpenFeatureAPI.getInstance();
        api.setProviderAndWait(new Ff4jFeatureProvider(ff4j));
        return api;
    }

    @Bean
    Client featureClient(OpenFeatureAPI api) {
        return api.getClient("feature-toggle-backend");
    }
}
