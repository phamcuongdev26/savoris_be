package com.savoris.service.impl;

import com.savoris.constant.StaffRole;
import com.savoris.constant.StaffStatus;
import com.savoris.dto.request.CreateStaffRequest;
import com.savoris.dto.request.UpdateStaffRequest;
import com.savoris.dto.response.StaffDetailResponse;
import com.savoris.entity.StaffEntity;
import com.savoris.repository.RefreshTokenRepository;
import com.savoris.repository.StaffRepository;
import com.savoris.security.BranchAccessGuard;
import com.savoris.service.StaffService;
import com.savoris.exception.AppException;
import com.savoris.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class StaffServiceImpl implements StaffService {

    private static final Set<StaffRole> BRANCH_MANAGED_ROLES =
        EnumSet.of(StaffRole.STAFF, StaffRole.CHEF);

    private final StaffRepository staffRepository;
    private final PasswordEncoder passwordEncoder;
    private final BranchAccessGuard branchAccessGuard;
    private final RefreshTokenRepository refreshTokenRepository;

    @Override
    @Transactional
    public StaffDetailResponse createStaff(Integer actorId, CreateStaffRequest request) {
        checkCanManage(findStaff(actorId), request.getBranchId(), request.getRole());
        if (staffRepository.findByUsername(request.getUsername()).isPresent()) {
            throw new AppException(ErrorCode.DUPLICATE_USERNAME, "Tên đăng nhập đã được sử dụng");
        }
        if (staffRepository.findByEmail(request.getEmail()).isPresent()) {
            throw new AppException(ErrorCode.DUPLICATE_EMAIL, "Email đã được sử dụng");
        }
        if (request.getRole() == StaffRole.ADMIN && request.getBranchId() != null) {
            throw new AppException(ErrorCode.INVALID_REQUEST, "Tài khoản ADMIN không thuộc chi nhánh");
        }
        if (request.getRole() != StaffRole.ADMIN && request.getBranchId() == null) {
            throw new AppException(ErrorCode.INVALID_REQUEST, "Tài khoản phải thuộc một chi nhánh");
        }

        StaffEntity staff = new StaffEntity();
        staff.setCode(request.getCode());
        staff.setFullName(request.getFullName());
        staff.setUsername(request.getUsername());
        staff.setEmail(request.getEmail());
        staff.setPhone(request.getPhone());
        staff.setRole(request.getRole());
        staff.setBranchId(request.getBranchId());
        if (request.getHourlyRate() != null) {
            staff.setHourlyRate(java.math.BigDecimal.valueOf(request.getHourlyRate()));
        }
        staff.setPasswordHash(passwordEncoder.encode(request.getPassword()));
        staff.setStatus(StaffStatus.ACTIVE);
        staff.setHiredAt(LocalDateTime.now());

        StaffEntity saved = staffRepository.save(staff);
        log.info("Created staff: {} ({})", saved.getFullName(), saved.getCode());
        return StaffDetailResponse.from(saved);
    }

    @Override
    public StaffDetailResponse getStaff(Integer actorId, Integer staffId) {
        StaffEntity staff = findStaff(staffId);
        // Everyone may see their own profile; managers see their branch; the owner sees everybody
        if (!actorId.equals(staffId)) {
            StaffEntity actor = findStaff(actorId);
            if (actor.getRole() != StaffRole.ADMIN
                && !(actor.getRole() == StaffRole.MANAGER && Objects.equals(actor.getBranchId(), staff.getBranchId()))) {
                throw new AppException(ErrorCode.FORBIDDEN, "Bạn không có quyền xem tài khoản này");
            }
        }
        return StaffDetailResponse.from(staff);
    }

    @Override
    @Transactional
    public StaffDetailResponse updateStaff(Integer actorId, Integer staffId, UpdateStaffRequest request) {
        StaffEntity staff = findStaff(staffId);
        StaffEntity actor = findStaff(actorId);
        // Both the current and the new role/branch must be within what the actor may manage (no self-promotion).
        checkCanManage(actor, staff.getBranchId(), staff.getRole());
        checkCanManage(actor, request.getBranchId(), request.getRole());
        staffRepository.findByEmail(request.getEmail()).filter(existing -> !existing.getId().equals(staffId))
            .ifPresent(existing -> { throw new AppException(ErrorCode.DUPLICATE_EMAIL, "Email đã được sử dụng"); });

        staff.setFullName(request.getFullName());
        staff.setEmail(request.getEmail());
        staff.setPhone(request.getPhone());
        staff.setRole(request.getRole());
        staff.setBranchId(request.getBranchId());
        if (request.getHourlyRate() != null) {
            staff.setHourlyRate(java.math.BigDecimal.valueOf(request.getHourlyRate()));
        }
        staff.setUpdatedAt(LocalDateTime.now());

        StaffEntity updated = staffRepository.save(staff);
        log.info("Updated staff: {} ({})", updated.getFullName(), updated.getCode());
        return StaffDetailResponse.from(updated);
    }

    @Override
    @Transactional
    public void deleteStaff(Integer actorId, Integer staffId) {
        StaffEntity staff = findStaff(staffId);
        checkCanManage(findStaff(actorId), staff.getBranchId(), staff.getRole());

        staff.setStatus(StaffStatus.LOCKED);
        staff.setUpdatedAt(LocalDateTime.now());
        staffRepository.save(staff);
        log.info("Deactivated staff: {} ({})", staff.getFullName(), staff.getCode());
    }

    @Override
    @Transactional
    public void activateStaff(Integer actorId, Integer staffId) {
        StaffEntity staff = findStaff(staffId);
        checkCanManage(findStaff(actorId), staff.getBranchId(), staff.getRole());

        staff.setStatus(StaffStatus.ACTIVE);
        staff.setUpdatedAt(LocalDateTime.now());
        staffRepository.save(staff);
        log.info("Reactivated staff: {} ({})", staff.getFullName(), staff.getCode());
    }

    // For staff who forgot their password; every session they had is ended
    @Override
    @Transactional
    public void resetPassword(Integer actorId, Integer staffId, String newPassword) {
        StaffEntity staff = findStaff(staffId);
        checkCanManage(findStaff(actorId), staff.getBranchId(), staff.getRole());
        staff.setPasswordHash(passwordEncoder.encode(newPassword));
        staff.setUpdatedAt(LocalDateTime.now());
        staffRepository.save(staff);
        refreshTokenRepository.revokeAllTokensByStaffId(staffId, LocalDateTime.now());
        log.info("Password of {} ({}) reset by staff {}", staff.getFullName(), staff.getCode(), actorId);
    }

    @Override
    public List<StaffDetailResponse> getStaffByBranch(Integer actorId, Integer branchId) {
        branchAccessGuard.check(actorId, branchId);
        return staffRepository.findByBranchId(branchId)
            .stream()
            .map(StaffDetailResponse::from)
            .toList();
    }

    @Override
    public List<StaffDetailResponse> searchStaff(String fullName) {
        return staffRepository.findByFullNameContainingIgnoreCase(fullName).stream()
            .map(StaffDetailResponse::from)
            .toList();
    }

    @Override
    public List<StaffDetailResponse> getAllStaff() {
        return staffRepository.findAll().stream().map(StaffDetailResponse::from).toList();
    }

    private StaffEntity findStaff(Integer staffId) {
        return staffRepository.findById(staffId)
            .orElseThrow(() -> new AppException(ErrorCode.STAFF_NOT_FOUND, "Staff not found: " + staffId));
    }

    // ADMIN manages everyone. A manager only manages STAFF and CHEF accounts of
    // their own branch, so nobody can create or promote an account above their own level
    private void checkCanManage(StaffEntity actor, Integer branchId, StaffRole role) {
        if (actor.getRole() == StaffRole.ADMIN) {
            return;
        }
        if (actor.getRole() != StaffRole.MANAGER || !Objects.equals(actor.getBranchId(), branchId)) {
            throw new AppException(ErrorCode.FORBIDDEN, "Không có quyền quản lý nhân viên chi nhánh này");
        }
        if (!BRANCH_MANAGED_ROLES.contains(role)) {
            throw new AppException(ErrorCode.FORBIDDEN, "Quản lý chỉ được quản lý nhân viên và đầu bếp của chi nhánh mình");
        }
    }
}
