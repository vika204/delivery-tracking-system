package com.delivery.auth.controller;

import com.delivery.auth.dto.UserRequest;
import com.delivery.auth.dto.UserResponse;
import com.delivery.auth.entity.User;
import com.delivery.auth.repository.UserRepository;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/users")
public class UserController {

    private final UserRepository userRepository;

    public UserController(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @PostMapping
    public UserResponse createUser(@RequestBody UserRequest request) {
        User user = User.builder()
                .firstName(request.firstName())
                .lastName(request.lastName())
                .email(request.email())
                .phone(request.phone())
                .passwordHash(request.passwordHash())
                .role(request.role())
                .build();
        return UserResponse.from(userRepository.save(user));
    }

    @GetMapping
    public List<UserResponse> getUsers() {
        return userRepository.findAll().stream().map(UserResponse::from).toList();
    }

    @GetMapping("/{id}")
    public UserResponse getUser(@PathVariable Long id) {
        return UserResponse.from(userRepository.findById(id).orElseThrow());
    }
}
