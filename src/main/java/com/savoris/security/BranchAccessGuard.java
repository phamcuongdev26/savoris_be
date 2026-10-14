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
        StaffEntity staff = staffRepository.findById(staffId)
            .orElseThrow(() -> new AppException(ErrorCode.UNAUTHORIZED));
        if (staff.getRole() != StaffRole.ADMIN && !Objects.equals(staff.getBranchId(), branchId)) {
            throw new AppException(ErrorCode.FORBIDDEN, "Staff does not belong to branch " + branchId);
        }
        return staff;
    }
}
