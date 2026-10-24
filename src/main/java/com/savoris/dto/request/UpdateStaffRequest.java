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
public class UpdateStaffRequest {
    @NotBlank(message = "Full name is required")
    private String fullName;

    @NotBlank(message = "Email is required")
    @Email
    private String email;

    @NotBlank(message = "Phone is required")
    @Pattern(regexp = "^[0-9\\-+\\s()]+$")
    private String phone;

    @NotNull(message = "Role is required")
    private StaffRole role;

    private Integer branchId;

    @AssertTrue(message = "ADMIN không thuộc chi nhánh; các vai trò khác bắt buộc chọn chi nhánh")
    public boolean isBranchAssignmentValid() {
        return role == null || (role == StaffRole.ADMIN ? branchId == null : branchId != null);
    }

    @NotNull(message = "Hourly rate is required")
    @DecimalMin("0")
    private Double hourlyRate;
}
