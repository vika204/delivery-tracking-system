package com.delivery.webui.config;

import com.delivery.webui.controller.support.LoopbackInterceptor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
@ConditionalOnProperty(name = "web.local-dev.loopback-only", havingValue = "true", matchIfMissing = true)
public class LoopbackWebConfiguration implements WebMvcConfigurer {

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new LoopbackInterceptor());
    }
}
