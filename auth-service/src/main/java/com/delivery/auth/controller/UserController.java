package com.delivery.auth.controller;

import com.delivery.auth.dto.LoginRequest;
import com.delivery.auth.dto.UserRequest;
import com.delivery.auth.dto.UserResponse;
import com.delivery.auth.dto.UserProfile;
import com.delivery.auth.entity.User;
import com.delivery.auth.repository.UserRepository;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@RestController
@RequestMapping("/users")
public class UserController {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    public UserController(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @PostMapping
    public UserResponse createUser(@Valid @RequestBody UserRequest request) {
        String email = request.email().trim();
        if (userRepository.existsByEmailIgnoreCase(email)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Account with this email already exists");
        }
        User user = User.builder()
                .firstName(request.firstName().trim())
                .lastName(request.lastName().trim())
                .email(email)
                .phone(request.phone().trim())
                .passwordHash(passwordEncoder.encode(request.password()))
                .role(request.role())
                .build();
        return UserResponse.from(userRepository.save(user));
    }

    @PostMapping("/login")
    public UserResponse login(@Valid @RequestBody LoginRequest request) {
        User user = userRepository.findByEmailIgnoreCase(request.email().trim())
                .filter(u -> passwordEncoder.matches(request.password(), u.getPasswordHash()))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid email or password"));
        return UserResponse.from(user);
    }

    @GetMapping
    public List<UserResponse> getUsers() {
        return userRepository.findAll().stream().map(UserResponse::from).toList();
    }

    @GetMapping("/profile")
    public UserProfile getProfile(@RequestHeader("X-User-Id") @Positive long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User profile not found"));
        return UserProfile.from(user);
    }

    @PutMapping("/profile")
    @Transactional
    public UserProfile updateProfile(@RequestHeader("X-User-Id") @Positive long userId,
                                     @Valid @RequestBody UserProfile profile) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User profile not found"));
        user.setFirstName(profile.firstName().trim());
        user.setLastName(profile.lastName().trim());
        user.setEmail(profile.email().trim());
        user.setPhone(profile.phone().trim());
        return UserProfile.from(userRepository.saveAndFlush(user));
    }

    @GetMapping("/{id}")
    public UserResponse getUser(@PathVariable Long id) {
        return UserResponse.from(userRepository.findById(id).orElseThrow());
    }
}
