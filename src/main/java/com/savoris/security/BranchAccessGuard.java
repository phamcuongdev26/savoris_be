package com.savoris.security;

import com.savoris.constant.StaffRole;
import com.savoris.entity.StaffEntity;
import com.savoris.exception.AppException;
import com.savoris.exception.ErrorCode;
import com.savoris.repository.StaffRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Objects;

/**
 * Staff may only work on their own branch; ADMIN (chain owner) may work on every branch.
 */
@Component
@RequiredArgsConstructor
public class BranchAccessGuard {

    private final StaffRepository staffRepository;

    public StaffEntity check(Integer staffId, Integer branchId) {
        StaffEntity staff = currentUser(staffId);
        if (staff.getRole() != StaffRole.ADMIN && !Objects.equals(staff.getBranchId(), branchId)) {
            throw new AppException(ErrorCode.FORBIDDEN, "Không có quyền truy cập chi nhánh này");
        }
        return staff;
    }

    public StaffEntity currentUser(Integer staffId) {
        return staffRepository.findById(staffId)
            .orElseThrow(() -> new AppException(ErrorCode.UNAUTHORIZED));
    }

    public StaffEntity check(StaffEntity currentUser, Integer branchId) {
        if (currentUser.getRole() != StaffRole.ADMIN && !Objects.equals(currentUser.getBranchId(), branchId)) {
            throw new AppException(ErrorCode.FORBIDDEN, "Không có quyền truy cập chi nhánh này");
        }
        return currentUser;
    }
}
