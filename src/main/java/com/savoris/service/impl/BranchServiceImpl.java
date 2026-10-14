package com.savoris.service.impl;

import com.savoris.constant.BranchStatus;
import com.savoris.dto.request.CreateBranchRequest;
import com.savoris.dto.request.UpdateBranchRequest;
import com.savoris.dto.response.BranchResponse;
import com.savoris.entity.BranchEntity;
import com.savoris.repository.BranchRepository;
import com.savoris.security.BranchAccessGuard;
import com.savoris.service.BranchService;
import com.savoris.exception.AppException;
import com.savoris.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class BranchServiceImpl implements BranchService {

    private final BranchRepository branchRepository;
    private final BranchAccessGuard branchAccessGuard;

    @Override
    @Transactional
    public BranchResponse createBranch(CreateBranchRequest request) {
        // Check if code already exists
        if (branchRepository.findByCode(request.getCode()).isPresent()) {
            throw new AppException(ErrorCode.DUPLICATE_CODE, "Branch code already exists: " + request.getCode());
        }

        BranchEntity branch = BranchEntity.builder()
            .code(request.getCode())
            .name(request.getName())
            .address(request.getAddress())
            .city(request.getCity())
            .phone(request.getPhone())
            .latitude(request.getLatitude())
            .longitude(request.getLongitude())
            .openTime(request.getOpenTime())
            .closeTime(request.getCloseTime())
            .managerId(request.getManagerId())
            .status(BranchStatus.ACTIVE)
            .build();

        BranchEntity saved = branchRepository.save(branch);
        log.info("Created branch: {} ({})", saved.getName(), saved.getCode());

        return BranchResponse.from(saved);
    }

    @Override
    public BranchResponse getBranch(Integer branchId) {
        BranchEntity branch = branchRepository.findById(branchId)
            .orElseThrow(() -> new AppException(ErrorCode.BRANCH_NOT_FOUND, "Branch not found: " + branchId));
        return BranchResponse.from(branch);
    }

    @Override
    @Transactional
    public BranchResponse updateBranch(Integer staffId, Integer branchId, UpdateBranchRequest request) {
        branchAccessGuard.check(staffId, branchId);
        BranchEntity branch = branchRepository.findById(branchId)
            .orElseThrow(() -> new AppException(ErrorCode.BRANCH_NOT_FOUND, "Branch not found: " + branchId));

        branch.setName(request.getName());
        branch.setAddress(request.getAddress());
        branch.setCity(request.getCity());
        branch.setPhone(request.getPhone());
        branch.setLatitude(request.getLatitude());
        branch.setLongitude(request.getLongitude());
        branch.setOpenTime(request.getOpenTime());
        branch.setCloseTime(request.getCloseTime());
        branch.setManagerId(request.getManagerId());
        branch.setKitchenCapacity(request.getKitchenCapacity());
        branch.setUpdatedAt(LocalDateTime.now());

        BranchEntity updated = branchRepository.save(branch);
        log.info("Updated branch: {} ({})", updated.getName(), updated.getCode());

        return BranchResponse.from(updated);
    }

    @Override
    @Transactional
    public void deleteBranch(Integer branchId) {
        BranchEntity branch = branchRepository.findById(branchId)
            .orElseThrow(() -> new AppException(ErrorCode.BRANCH_NOT_FOUND, "Branch not found: " + branchId));

        branch.setStatus(BranchStatus.INACTIVE);
        branch.setUpdatedAt(LocalDateTime.now());
        branchRepository.save(branch);

        log.info("Deactivated branch: {} ({})", branch.getName(), branch.getCode());
    }

    @Override
    public List<BranchResponse> getAllBranches() {
        return branchRepository.findByStatus(BranchStatus.ACTIVE)
            .stream()
            .map(BranchResponse::from)
            .toList();
    }

    @Override
    public List<BranchResponse> searchBranches(String name) {
        return branchRepository.findByNameContainingIgnoreCase(name)
            .stream()
            .filter(b -> b.getStatus() == BranchStatus.ACTIVE)
            .map(BranchResponse::from)
            .toList();
    }
}
