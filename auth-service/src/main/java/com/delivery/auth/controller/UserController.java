package com.delivery.auth.controller;

import com.delivery.auth.dto.LoginRequest;
import com.delivery.auth.dto.UserRequest;
import com.delivery.auth.dto.UserResponse;
import com.delivery.auth.dto.UserProfile;
import com.delivery.auth.service.UserService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/users")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @PostMapping
    public UserResponse createUser(@Valid @RequestBody UserRequest request) {
        return userService.createUser(request);
    }

    @PostMapping("/login")
    public UserResponse login(@Valid @RequestBody LoginRequest request) {
        return userService.login(request);
    }

    @GetMapping
    public List<UserResponse> getUsers() {
        return userService.getUsers();
    }

    @GetMapping("/profile")
    public UserProfile getProfile(@RequestHeader("X-User-Id") @Positive long userId) {
        return userService.getProfile(userId);
    }

    @PutMapping("/profile")
    public UserProfile updateProfile(@RequestHeader("X-User-Id") @Positive long userId,
                                     @Valid @RequestBody UserProfile profile) {
        return userService.updateProfile(userId, profile);
    }

    @GetMapping("/{id}")
    public UserResponse getUser(@PathVariable Long id) {
        return userService.getUser(id);
    }
}
