package com.novakai.orchestrator.config;

// @author Siddhant Choudhary

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ViewControllerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    @Override
    public void addViewControllers(ViewControllerRegistry registry) {
        registry.addViewController("/").setViewName("forward:/browser/index.html");
        registry.addViewController("/{spring:[^\\.]*}")
            .setViewName("forward:/browser/{spring:[^\\.]*}");
    }
}
