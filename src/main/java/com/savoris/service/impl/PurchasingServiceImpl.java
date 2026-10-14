package com.savoris.service.impl;

import com.savoris.constant.PurchaseOrderStatus;
import com.savoris.constant.StockMovementType;
import com.savoris.dto.request.CreateIngredientRequest;
import com.savoris.dto.request.CreatePurchaseOrderRequest;
import com.savoris.dto.request.CreateSupplierRequest;
import com.savoris.dto.request.ReceivePurchaseOrderRequest;
import com.savoris.dto.response.IngredientResponse;
import com.savoris.dto.response.PurchaseOrderResponse;
import com.savoris.dto.response.SupplierResponse;
import com.savoris.entity.IngredientEntity;
import com.savoris.entity.PurchaseOrderEntity;
import com.savoris.entity.PurchaseOrderItemEntity;
import com.savoris.entity.StockMovementEntity;
import com.savoris.entity.SupplierEntity;
import com.savoris.exception.AppException;
import com.savoris.exception.ErrorCode;
import com.savoris.repository.BranchInventoryRepository;
import com.savoris.repository.BranchRepository;
import com.savoris.repository.IngredientRepository;
import com.savoris.repository.PurchaseOrderItemRepository;
import com.savoris.repository.PurchaseOrderRepository;
import com.savoris.repository.StockMovementRepository;
import com.savoris.repository.SupplierRepository;
import com.savoris.security.BranchAccessGuard;
import com.savoris.service.OrderCodeGenerator;
import com.savoris.service.PurchasingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Buying ingredients: a purchase order is drafted (DRAFT), approved and sent to the supplier (ORDERED),
 * and when the goods arrive (RECEIVED) the branch stock goes up and the import is kept in the stock history.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PurchasingServiceImpl implements PurchasingService {

    private final SupplierRepository supplierRepository;
    private final IngredientRepository ingredientRepository;
    private final PurchaseOrderRepository purchaseOrderRepository;
    private final PurchaseOrderItemRepository purchaseOrderItemRepository;
    private final BranchRepository branchRepository;
    private final BranchInventoryRepository branchInventoryRepository;
    private final StockMovementRepository stockMovementRepository;
    private final BranchAccessGuard branchAccessGuard;
    private final OrderCodeGenerator codeGenerator;

    // ---------------------------------------------------------------- suppliers

    @Override
    @Transactional
    public SupplierResponse createSupplier(CreateSupplierRequest request) {
        SupplierEntity supplier = new SupplierEntity();
        supplier.setIsActive(true);
        return saveSupplier(supplier, request);
    }

    @Override
    @Transactional
    public SupplierResponse updateSupplier(Integer supplierId, CreateSupplierRequest request) {
        return saveSupplier(findSupplier(supplierId), request);
    }

    private SupplierResponse saveSupplier(SupplierEntity supplier, CreateSupplierRequest request) {
        supplier.setName(request.getName());
        supplier.setPhone(request.getPhone());
        supplier.setEmail(request.getEmail());
        supplier.setAddress(request.getAddress());
        supplier.setTaxCode(request.getTaxCode());
        return SupplierResponse.from(supplierRepository.save(supplier));
    }

    @Override
    @Transactional
    public SupplierResponse deactivateSupplier(Integer supplierId) {
        SupplierEntity supplier = findSupplier(supplierId);
        supplier.setIsActive(false);
        return SupplierResponse.from(supplierRepository.save(supplier));
    }

    @Override
    @Transactional(readOnly = true)
    public List<SupplierResponse> getSuppliers() {
        return supplierRepository.findByIsActiveTrueOrderByName().stream().map(SupplierResponse::from).toList();
    }

    // ---------------------------------------------------------------- ingredients

    @Override
    @Transactional
    public IngredientResponse createIngredient(CreateIngredientRequest request) {
        if (ingredientRepository.findByCode(request.getCode()).isPresent()) {
            throw new AppException(ErrorCode.DUPLICATE_CODE, "Ingredient code already exists: " + request.getCode());
        }
        IngredientEntity ingredient = new IngredientEntity();
        ingredient.setCode(request.getCode());
        ingredient.setName(request.getName());
        ingredient.setUnit(request.getUnit());
        ingredient.setMinStock(request.getMinStock() != null ? request.getMinStock() : BigDecimal.ZERO);
        return IngredientResponse.from(ingredientRepository.save(ingredient));
    }

    @Override
    @Transactional(readOnly = true)
    public List<IngredientResponse> getIngredients() {
        return ingredientRepository.findAll().stream().map(IngredientResponse::from).toList();
    }

    // ---------------------------------------------------------------- purchase orders

    @Override
    @Transactional
    public PurchaseOrderResponse createPurchaseOrder(Integer staffId, CreatePurchaseOrderRequest request) {
        branchAccessGuard.check(staffId, request.getBranchId());
        if (!branchRepository.existsById(request.getBranchId())) {
            throw new AppException(ErrorCode.BRANCH_NOT_FOUND);
        }
        SupplierEntity supplier = findSupplier(request.getSupplierId());
        if (!Boolean.TRUE.equals(supplier.getIsActive())) {
            throw new AppException(ErrorCode.INVALID_REQUEST, "Supplier " + supplier.getName() + " is no longer active");
        }
        List<Integer> ingredientIds = request.getItems().stream().map(CreatePurchaseOrderRequest.Item::getIngredientId).toList();
        if (ingredientIds.stream().distinct().count() != ingredientIds.size()) {
            throw new AppException(ErrorCode.INVALID_REQUEST, "Each ingredient may appear only once in a purchase order");
        }
        Map<Integer, IngredientEntity> ingredients = ingredientsById(ingredientIds);
        if (ingredients.size() != ingredientIds.size()) {
            throw new AppException(ErrorCode.NOT_FOUND, "Unknown ingredient in " + ingredientIds);
        }

        PurchaseOrderEntity order = new PurchaseOrderEntity();
        order.setCode(codeGenerator.nextPurchase(request.getBranchId()));
        order.setBranchId(request.getBranchId());
        order.setSupplierId(supplier.getId());
        order.setStatus(PurchaseOrderStatus.DRAFT);
        order.setNote(request.getNote());
        order.setCreatedBy(staffId);
        order.setTotalAmount(request.getItems().stream()
            .map(i -> i.getQuantity().multiply(i.getUnitPrice()))
            .reduce(BigDecimal.ZERO, BigDecimal::add)
            .setScale(0, RoundingMode.HALF_UP));
        PurchaseOrderEntity saved = purchaseOrderRepository.save(order);

        List<PurchaseOrderItemEntity> items = new ArrayList<>();
        for (CreatePurchaseOrderRequest.Item line : request.getItems()) {
            PurchaseOrderItemEntity item = new PurchaseOrderItemEntity();
            item.setPurchaseOrderId(saved.getId());
            item.setIngredientId(line.getIngredientId());
            item.setQuantity(line.getQuantity());
            item.setUnitPrice(line.getUnitPrice());
            items.add(item);
        }
        purchaseOrderItemRepository.saveAll(items);
        log.info("Drafted purchase order {} for branch {} from {}", saved.getCode(), saved.getBranchId(), supplier.getName());
        return toResponse(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public PurchaseOrderResponse getPurchaseOrder(Integer staffId, Integer purchaseOrderId) {
        PurchaseOrderEntity order = purchaseOrderRepository.findById(purchaseOrderId)
            .orElseThrow(() -> notFound(purchaseOrderId));
        branchAccessGuard.check(staffId, order.getBranchId());
        return toResponse(order);
    }

    @Override
    @Transactional(readOnly = true)
    public List<PurchaseOrderResponse> getPurchaseOrders(Integer staffId, Integer branchId) {
        branchAccessGuard.check(staffId, branchId);
        return purchaseOrderRepository.findByBranchIdOrderByCreatedAtDesc(branchId).stream().map(this::toResponse).toList();
    }

    @Override
    @Transactional
    public PurchaseOrderResponse placeOrder(Integer staffId, Integer purchaseOrderId) {
        PurchaseOrderEntity order = lockForChange(staffId, purchaseOrderId, PurchaseOrderStatus.DRAFT);
        order.setStatus(PurchaseOrderStatus.ORDERED);
        order.setOrderedAt(LocalDateTime.now());
        purchaseOrderRepository.save(order);
        return toResponse(order);
    }

    @Override
    @Transactional
    public PurchaseOrderResponse receive(Integer staffId, Integer purchaseOrderId, ReceivePurchaseOrderRequest request) {
        PurchaseOrderEntity order = lockForChange(staffId, purchaseOrderId, PurchaseOrderStatus.ORDERED);
        List<PurchaseOrderItemEntity> items = purchaseOrderItemRepository.findByPurchaseOrderId(order.getId());

        Map<Integer, BigDecimal> received = request != null && request.getItems() != null
            ? request.getItems().stream().collect(Collectors.toMap(ReceivePurchaseOrderRequest.Item::getItemId,
                ReceivePurchaseOrderRequest.Item::getReceivedQuantity, (a, b) -> b))
            : Map.of();
        List<Integer> itemIds = items.stream().map(PurchaseOrderItemEntity::getId).toList();
        if (!itemIds.containsAll(received.keySet())) {
            throw new AppException(ErrorCode.INVALID_REQUEST, "Item ids must belong to purchase order " + order.getCode());
        }

        LocalDateTime now = LocalDateTime.now();
        BigDecimal total = BigDecimal.ZERO;
        for (PurchaseOrderItemEntity item : items) {
            BigDecimal quantity = received.getOrDefault(item.getId(), item.getQuantity());
            item.setReceivedQuantity(quantity);
            total = total.add(quantity.multiply(item.getUnitPrice()));
            if (quantity.signum() == 0) {
                continue;
            }
            branchInventoryRepository.createIfAbsent(order.getBranchId(), item.getIngredientId(), now);
            branchInventoryRepository.add(order.getBranchId(), item.getIngredientId(), quantity, now);

            StockMovementEntity movement = new StockMovementEntity();
            movement.setBranchId(order.getBranchId());
            movement.setIngredientId(item.getIngredientId());
            movement.setType(StockMovementType.IMPORT);
            movement.setQuantity(quantity);
            movement.setUnitCost(item.getUnitPrice());
            movement.setPurchaseOrderId(order.getId());
            movement.setNote("Received " + order.getCode());
            movement.setCreatedBy(staffId);
            stockMovementRepository.save(movement);
        }
        purchaseOrderItemRepository.saveAll(items);

        // What is owed to the supplier is what actually arrived
        order.setTotalAmount(total.setScale(0, RoundingMode.HALF_UP));
        order.setStatus(PurchaseOrderStatus.RECEIVED);
        order.setReceivedAt(now);
        purchaseOrderRepository.save(order);
        log.info("Received purchase order {} at branch {}", order.getCode(), order.getBranchId());
        return toResponse(order);
    }

    @Override
    @Transactional
    public PurchaseOrderResponse cancel(Integer staffId, Integer purchaseOrderId) {
        PurchaseOrderEntity order = purchaseOrderRepository.findByIdForUpdate(purchaseOrderId)
            .orElseThrow(() -> notFound(purchaseOrderId));
        branchAccessGuard.check(staffId, order.getBranchId());
        if (order.getStatus() != PurchaseOrderStatus.DRAFT && order.getStatus() != PurchaseOrderStatus.ORDERED) {
            throw new AppException(ErrorCode.INVALID_STATE, "Purchase order " + order.getCode() + " is already " + order.getStatus());
        }
        order.setStatus(PurchaseOrderStatus.CANCELLED);
        purchaseOrderRepository.save(order);
        return toResponse(order);
    }

    private PurchaseOrderEntity lockForChange(Integer staffId, Integer purchaseOrderId, PurchaseOrderStatus expected) {
        PurchaseOrderEntity order = purchaseOrderRepository.findByIdForUpdate(purchaseOrderId)
            .orElseThrow(() -> notFound(purchaseOrderId));
        branchAccessGuard.check(staffId, order.getBranchId());
        if (order.getStatus() != expected) {
            throw new AppException(ErrorCode.INVALID_STATE,
                "Purchase order " + order.getCode() + " is " + order.getStatus() + ", expected " + expected);
        }
        return order;
    }

    private PurchaseOrderResponse toResponse(PurchaseOrderEntity order) {
        List<PurchaseOrderItemEntity> items = purchaseOrderItemRepository.findByPurchaseOrderId(order.getId());
        return PurchaseOrderResponse.from(order, items,
            ingredientsById(items.stream().map(PurchaseOrderItemEntity::getIngredientId).toList()));
    }

    private Map<Integer, IngredientEntity> ingredientsById(Collection<Integer> ids) {
        return ingredientRepository.findAllById(ids).stream()
            .collect(Collectors.toMap(IngredientEntity::getId, Function.identity()));
    }

    private SupplierEntity findSupplier(Integer supplierId) {
        return supplierRepository.findById(supplierId)
            .orElseThrow(() -> new AppException(ErrorCode.NOT_FOUND, "Supplier not found: " + supplierId));
    }

    private static AppException notFound(Integer purchaseOrderId) {
        return new AppException(ErrorCode.NOT_FOUND, "Purchase order not found: " + purchaseOrderId);
    }
}
