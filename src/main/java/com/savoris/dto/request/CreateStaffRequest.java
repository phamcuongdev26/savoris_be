package com.savoris.dto.request;

import com.savoris.constant.StaffRole;
import jakarta.validation.constraints.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class CreateStaffRequest {
    @NotBlank(message = "Staff code is required")
    private String code;

    @NotBlank(message = "Full name is required")
    private String fullName;

    @NotBlank(message = "Username is required")
    @Size(min = 3, max = 100)
    private String username;

    @NotBlank(message = "Email is required")
    @Email
    private String email;

    @NotBlank(message = "Phone is required")
    @Pattern(regexp = "^[0-9\\-+\\s()]+$")
    private String phone;

    // Initial password, handed to the employee who should change it via PUT /auth/me/password
    @NotBlank(message = "Password is required")
    @Size(min = 5, message = "Password must be at least 5 characters")
    private String password;

    @NotNull(message = "Role is required")
    private StaffRole role;

    @NotNull(message = "Branch ID is required")
    private Integer branchId;

    @NotNull(message = "Hourly rate is required")
    @DecimalMin("0")
    private Double hourlyRate;
}
