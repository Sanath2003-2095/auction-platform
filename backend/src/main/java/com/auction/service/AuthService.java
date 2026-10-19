package com.auction.service;

import com.auction.domain.Role;
import com.auction.domain.User;
import com.auction.dto.Dtos.AuthResponse;
import com.auction.dto.Dtos.LoginRequest;
import com.auction.dto.Dtos.RegisterRequest;
import com.auction.dto.Dtos.UserDto;
import com.auction.dto.Mappers;
import com.auction.exception.ApiException;
import com.auction.repository.UserRepository;
import com.auction.security.JwtService;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

    private final UserRepository users;
    private final PasswordEncoder encoder;
    private final JwtService jwt;

    public AuthService(UserRepository users, PasswordEncoder encoder, JwtService jwt) {
        this.users = users;
        this.encoder = encoder;
        this.jwt = jwt;
    }

    @Transactional
    public AuthResponse register(RegisterRequest req) {
        if (users.existsByUsernameIgnoreCase(req.username())) {
            throw ApiException.conflict("Username is already taken");
        }
        if (users.existsByEmailIgnoreCase(req.email())) {
            throw ApiException.conflict("Email is already registered");
        }
        User user = new User(req.username(), req.email().toLowerCase(), encoder.encode(req.password()), Role.USER);
        try {
            users.saveAndFlush(user);
        } catch (DataIntegrityViolationException e) {
            throw ApiException.conflict("Username or email is already in use");
        }
        return new AuthResponse(jwt.generate(user), Mappers.toDto(user));
    }

    @Transactional(readOnly = true)
    public AuthResponse login(LoginRequest req) {
        User user = users.findByUsernameIgnoreCaseOrEmailIgnoreCase(req.username(), req.username())
                .orElseThrow(() -> ApiException.unauthorized("Invalid username or password"));
        if (!encoder.matches(req.password(), user.getPasswordHash())) {
            throw ApiException.unauthorized("Invalid username or password");
        }
        if (!user.isEnabled()) {
            throw ApiException.forbidden("This account has been disabled");
        }
        return new AuthResponse(jwt.generate(user), Mappers.toDto(user));
    }

    @Transactional(readOnly = true)
    public UserDto me(Long userId) {
        return users.findById(userId).map(Mappers::toDto).orElseThrow(() -> ApiException.notFound("User not found"));
    }
}
