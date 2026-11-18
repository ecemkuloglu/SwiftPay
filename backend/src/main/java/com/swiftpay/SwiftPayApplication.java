package com.swiftpay;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class SwiftPayApplication {

    public static void main(String[] args) {
        SpringApplication.run(SwiftPayApplication.class, args);
    }
}
