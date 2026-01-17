package com.example.backend.config;

import com.example.backend.util.JwtUtil;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AppConfig {

    @Bean
    public JwtUtil jwtUtil(
            @Value("${app.jwt.secret}") String secret,
            @Value("${app.jwt.issuer:lol-smart-stats}") String issuer
    ) {
        return new JwtUtil(secret, issuer);
    }
}
