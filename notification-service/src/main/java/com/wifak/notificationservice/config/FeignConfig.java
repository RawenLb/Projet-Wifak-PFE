// com/wifak/notificationservice/config/FeignConfig.java

package com.wifak.notificationservice.config;

import feign.RequestInterceptor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class FeignConfig {

    @Value("${app.internal-secret}")
    private String internalSecret;

    @Bean
    public RequestInterceptor internalSecretInterceptor() {
        return requestTemplate ->
                requestTemplate.header("X-Internal-Secret", internalSecret);
    }
}