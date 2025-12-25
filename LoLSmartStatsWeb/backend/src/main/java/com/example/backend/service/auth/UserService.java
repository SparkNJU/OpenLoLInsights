package com.example.backend.service.auth;

import com.example.backend.entity.User;
import com.example.backend.exception.BizException;
import com.example.backend.repository.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.UUID;

@Service
public class UserService {
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public UserService(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    public User register(String email, String rawPassword, String nickname) {
        if (userRepository.existsByEmail(email)) {
            throw new BizException("CONFLICT", "邮箱已注册");
        }
        User u = new User();
        u.setId("u_" + UUID.randomUUID().toString().replace("-", ""));
        u.setEmail(email);
        u.setPasswordHash(passwordEncoder.encode(rawPassword));
        u.setNickname(nickname);
        u.setAvatar(null);
        u.setCreatedAt(LocalDateTime.now());
        return userRepository.save(u);
    }

    public User checkLogin(String email, String rawPassword) {
        User u = userRepository.findByEmail(email)
                .orElseThrow(() -> new BizException("UNAUTHORIZED", "邮箱或密码错误"));
        if (!passwordEncoder.matches(rawPassword, u.getPasswordHash())) {
            throw new BizException("UNAUTHORIZED", "邮箱或密码错误");
        }
        return u;
    }

    public User getById(String userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new BizException("UNAUTHORIZED", "用户不存在或未登录"));
    }
}

