package com.delivery.auth.dto;

import com.delivery.auth.entity.User;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record UserProfile(
        @NotBlank @Size(max = 70) String firstName,
        @NotBlank @Size(max = 70) String lastName,
        @NotBlank @Email @Size(max = 254) String email,
        @NotBlank @Pattern(regexp = "[+0-9() .-]{7,25}") String phone
) {
    public static UserProfile from(User user) {
        return new UserProfile(user.getFirstName(), user.getLastName(), user.getEmail(), user.getPhone());
    }
}
