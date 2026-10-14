package com.savoris.service.impl;

import com.savoris.constant.DiscountType;
import com.savoris.dto.request.CreatePromotionRequest;
import com.savoris.dto.response.PromotionCheckResponse;
import com.savoris.dto.response.PromotionResponse;
import com.savoris.entity.PromotionBranchEntity;
import com.savoris.entity.PromotionEntity;
import com.savoris.exception.AppException;
import com.savoris.exception.ErrorCode;
import com.savoris.repository.BranchRepository;
import com.savoris.repository.PromotionBranchRepository;
import com.savoris.repository.PromotionRepository;
import com.savoris.security.BranchAccessGuard;
import com.savoris.service.PromotionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;

@Slf4j
@Service
@RequiredArgsConstructor
public class PromotionServiceImpl implements PromotionService {

    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    private final PromotionRepository promotionRepository;
    private final PromotionBranchRepository promotionBranchRepository;
    private final BranchRepository branchRepository;
    private final BranchAccessGuard branchAccessGuard;

    @Override
    @Transactional
    public PromotionResponse createPromotion(CreatePromotionRequest request) {
        if (promotionRepository.findByCodeIgnoreCase(request.getCode()).isPresent()) {
            throw new AppException(ErrorCode.DUPLICATE_CODE, "Promotion code already exists: " + request.getCode());
        }
        PromotionEntity promotion = new PromotionEntity();
        promotion.setCode(request.getCode().toUpperCase(Locale.ROOT));
        promotion.setUsedCount(0);
        promotion.setIsActive(true);
        return save(promotion, request);
    }

    @Override
    @Transactional
    public PromotionResponse updatePromotion(Integer promotionId, CreatePromotionRequest request) {
        PromotionEntity promotion = findPromotion(promotionId);
        if (!promotion.getCode().equalsIgnoreCase(request.getCode())) {
            throw new AppException(ErrorCode.INVALID_REQUEST, "The code of a promotion cannot be changed");
        }
        return save(promotion, request);
    }

    private PromotionResponse save(PromotionEntity promotion, CreatePromotionRequest request) {
        if (!request.getEndAt().isAfter(request.getStartAt())) {
            throw new AppException(ErrorCode.INVALID_REQUEST, "End time must be after start time");
        }
        if (request.getDiscountType() == DiscountType.PERCENT && request.getDiscountValue().compareTo(HUNDRED) > 0) {
            throw new AppException(ErrorCode.INVALID_REQUEST, "A percentage discount cannot exceed 100");
        }
        List<Integer> branchIds = request.getBranchIds() != null ? request.getBranchIds().stream().distinct().toList() : List.of();
        if (branchRepository.findAllById(branchIds).size() != branchIds.size()) {
            throw new AppException(ErrorCode.BRANCH_NOT_FOUND, "Unknown branch in " + branchIds);
        }

        promotion.setName(request.getName());
        promotion.setDiscountType(request.getDiscountType());
        promotion.setDiscountValue(request.getDiscountValue());
        promotion.setMaxDiscount(request.getMaxDiscount());
        promotion.setMinOrderAmount(request.getMinOrderAmount() != null ? request.getMinOrderAmount() : BigDecimal.ZERO);
        promotion.setStartAt(request.getStartAt());
        promotion.setEndAt(request.getEndAt());
        promotion.setUsageLimit(request.getUsageLimit());
        PromotionEntity saved = promotionRepository.save(promotion);

        promotionBranchRepository.deleteByPromotionId(saved.getId());
        for (Integer branchId : branchIds) {
            PromotionBranchEntity link = new PromotionBranchEntity();
            link.setPromotionId(saved.getId());
            link.setBranchId(branchId);
            promotionBranchRepository.save(link);
        }
        log.info("Saved promotion {} ({})", saved.getCode(), saved.getName());
        return PromotionResponse.from(saved, branchIds);
    }

    @Override
    @Transactional
    public PromotionResponse deactivatePromotion(Integer promotionId) {
        PromotionEntity promotion = findPromotion(promotionId);
        promotion.setIsActive(false);
        promotionRepository.save(promotion);
        return toResponse(promotion);
    }

    @Override
    @Transactional(readOnly = true)
    public List<PromotionResponse> getPromotions() {
        return promotionRepository.findAllByOrderByStartAtDesc().stream().map(this::toResponse).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public PromotionCheckResponse check(Integer staffId, String code, Integer branchId, BigDecimal orderAmount) {
        branchAccessGuard.check(staffId, branchId);
        PromotionEntity promotion = validate(code, branchId, orderAmount);
        return new PromotionCheckResponse(promotion.getCode(), promotion.getName(), orderAmount, discountFor(promotion, orderAmount));
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public PromotionEntity redeem(String code, Integer branchId, BigDecimal orderAmount) {
        PromotionEntity promotion = validate(code, branchId, orderAmount);
        if (promotionRepository.takeUse(promotion.getId()) == 0) {
            throw new AppException(ErrorCode.PROMOTION_NOT_APPLICABLE, "Code " + promotion.getCode() + " has been used up");
        }
        return promotion;
    }

    @Override
    public BigDecimal discountFor(PromotionEntity promotion, BigDecimal orderAmount) {
        BigDecimal discount = promotion.getDiscountType() == DiscountType.PERCENT
            ? orderAmount.multiply(promotion.getDiscountValue()).divide(HUNDRED, 0, RoundingMode.HALF_UP)
            : promotion.getDiscountValue();
        if (promotion.getMaxDiscount() != null) {
            discount = discount.min(promotion.getMaxDiscount());
        }
        return discount.min(orderAmount);
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void release(Integer promotionId) {
        promotionRepository.releaseUse(promotionId);
    }

    @Override
    @Transactional(readOnly = true)
    public PromotionEntity get(Integer promotionId) {
        return findPromotion(promotionId);
    }

    private PromotionEntity validate(String code, Integer branchId, BigDecimal orderAmount) {
        PromotionEntity promotion = promotionRepository.findByCodeIgnoreCase(code)
            .orElseThrow(() -> new AppException(ErrorCode.PROMOTION_NOT_APPLICABLE, "Unknown promotion code: " + code));
        LocalDateTime now = LocalDateTime.now();
        if (!Boolean.TRUE.equals(promotion.getIsActive()) || now.isBefore(promotion.getStartAt()) || now.isAfter(promotion.getEndAt())) {
            throw new AppException(ErrorCode.PROMOTION_NOT_APPLICABLE, "Code " + promotion.getCode() + " is not running now");
        }
        List<PromotionBranchEntity> branches = promotionBranchRepository.findByPromotionId(promotion.getId());
        if (!branches.isEmpty() && branches.stream().noneMatch(b -> b.getBranchId().equals(branchId))) {
            throw new AppException(ErrorCode.PROMOTION_NOT_APPLICABLE, "Code " + promotion.getCode() + " is not valid at this branch");
        }
        if (orderAmount.compareTo(promotion.getMinOrderAmount()) < 0) {
            throw new AppException(ErrorCode.PROMOTION_NOT_APPLICABLE,
                "Code " + promotion.getCode() + " needs an order of at least " + promotion.getMinOrderAmount().toPlainString());
        }
        if (promotion.getUsageLimit() != null && promotion.getUsedCount() >= promotion.getUsageLimit()) {
            throw new AppException(ErrorCode.PROMOTION_NOT_APPLICABLE, "Code " + promotion.getCode() + " has been used up");
        }
        return promotion;
    }

    private PromotionEntity findPromotion(Integer promotionId) {
        return promotionRepository.findById(promotionId)
            .orElseThrow(() -> new AppException(ErrorCode.NOT_FOUND, "Promotion not found: " + promotionId));
    }

    private PromotionResponse toResponse(PromotionEntity promotion) {
        return PromotionResponse.from(promotion, promotionBranchRepository.findByPromotionId(promotion.getId())
            .stream().map(PromotionBranchEntity::getBranchId).toList());
    }
}
