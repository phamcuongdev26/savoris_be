package com.savoris.dto.request;

import jakarta.validation.constraints.Email;
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
public class CreateSupplierRequest {
    @NotBlank(message = "Supplier name is required")
    private String name;

    @Pattern(regexp = "^[0-9\\-+\\s()]*$", message = "Phone number is invalid")
    private String phone;

    @Email
    private String email;

    @Size(max = 500)
    private String address;

    @Size(max = 20)
    private String taxCode;
}
