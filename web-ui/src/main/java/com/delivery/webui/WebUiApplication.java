package com.delivery.webui;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class WebUiApplication {

    public static void main(String[] args) {
        SpringApplication.run(WebUiApplication.class, args);
    }
}