package com.savoris.dto.request;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;


@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class CreateBranchRequest {
    @NotBlank(message = "Branch code is required")
    @Pattern(regexp = "^[A-Z0-9_]+$", message = "Branch code must be uppercase alphanumeric with underscores")
    private String code;

    @NotBlank(message = "Branch name is required")
    private String name;

    @NotBlank(message = "Address is required")
    @Size(min = 10, message = "Address must include a full street address")
    private String address;

    @NotBlank(message = "City is required")
    private String city;

    @NotBlank(message = "Phone is required")
    @Pattern(regexp = "^[0-9\\-+\\s()]+$", message = "Phone number is invalid")
    private String phone;

    @NotBlank(message = "Email is required")
    @jakarta.validation.constraints.Email
    private String email;

    @DecimalMin(value = "-90", message = "Latitude must be between -90 and 90")
    @DecimalMax(value = "90", message = "Latitude must be between -90 and 90")
    private Double latitude;

    @DecimalMin(value = "-180", message = "Longitude must be between -180 and 180")
    @DecimalMax(value = "180", message = "Longitude must be between -180 and 180")
    private Double longitude;

    private Integer managerId;
}
