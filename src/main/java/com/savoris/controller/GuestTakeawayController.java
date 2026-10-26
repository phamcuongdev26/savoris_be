package com.savoris.controller;

import com.savoris.dto.request.WebTakeawayRequest;
import com.savoris.dto.request.TakeawayEstimateRequest;
import com.savoris.dto.response.GuestTableResponse;
import com.savoris.dto.response.PublicBranchResponse;
import com.savoris.dto.response.WebTakeawayResponse;
import com.savoris.dto.response.TakeawayEstimateResponse;
import com.savoris.service.GuestTakeawayService;
import com.savoris.service.SettingService;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Duration;
import java.util.List;

// Under /public: guests order takeaway from the website without an account
@RestController
@RequestMapping("/public")
@RequiredArgsConstructor
@Tag(name = "Guest takeaway", description = "Khách đặt món mang về trên web, trả tiền trước rồi bếp mới nấu")
public class GuestTakeawayController {

    private final GuestTakeawayService guestTakeawayService;
    private final SettingService settingService;

    @GetMapping("/images/transfer-qr/{branchId}")
    @Operation(summary = "Ảnh mã QR chuyển khoản", description = "Ảnh chủ chuỗi đã tải lên; 404 khi chưa có")
    public ResponseEntity<byte[]> getTransferQrImage(@PathVariable Integer branchId) {
        return settingService.getTransferQrImage(branchId)
            .map(image -> ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(image.getContentType()))
                // The link carries a version (?v=…), so the image under one link never changes
                .cacheControl(CacheControl.maxAge(Duration.ofDays(30)).cachePublic())
                .body(image.getData()))
            .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @GetMapping("/branches")
    @Operation(summary = "Chi nhánh nhận đơn mang về", description = "acceptingTakeaway=false khi bếp chi nhánh đang quá tải")
    public ResponseEntity<List<PublicBranchResponse>> getBranches() {
        return ResponseEntity.ok(guestTakeawayService.getBranches());
    }

    @GetMapping("/branches/{branchId}/menu")
    @Operation(summary = "Menu mang về của chi nhánh", description = "Giá riêng và món hết của chi nhánh")
    public ResponseEntity<GuestTableResponse> getBranchMenu(@PathVariable Integer branchId) {
        return ResponseEntity.ok(guestTakeawayService.getBranchMenu(branchId));
    }

    @PostMapping("/takeaway-orders/estimate")
    @Operation(summary = "Ước tính giờ nhận món mang về theo tải bếp và các món đã chọn")
    public ResponseEntity<TakeawayEstimateResponse> estimatePickup(@Valid @RequestBody TakeawayEstimateRequest request) {
        return ResponseEntity.ok(guestTakeawayService.estimatePickup(request.branchId(), request.items()));
    }

    @PostMapping("/takeaway-orders")
    @Operation(summary = "Đặt món mang về", description = "payMethod CASH (trả tại quầy) hoặc BANK_TRANSFER (chuyển khoản theo mã QR). "
        + "Đơn chờ thanh toán, phục vụ thu tiền / xác nhận chuyển khoản xong bếp mới nấu")
    public ResponseEntity<WebTakeawayResponse> placeOrder(@Valid @RequestBody WebTakeawayRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(guestTakeawayService.placeOrder(request));
    }

    public record PushKeys(@NotBlank String p256dh, @NotBlank String auth) {
    }

    public record PushSubscriptionRequest(@NotBlank @Size(max = 1000) String endpoint, @NotNull @Valid PushKeys keys) {
    }

    public record PushKey(String publicKey) {
    }

    @PostMapping("/takeaway-orders/{token}/transferred")
    @Operation(summary = "Khách báo đã chuyển khoản", description = "Đơn không bị tự huỷ vì quá hạn nữa; phục vụ kiểm tra tiền về rồi xác nhận")
    public ResponseEntity<WebTakeawayResponse> claimTransfer(@PathVariable String token) {
        return ResponseEntity.ok(guestTakeawayService.claimTransfer(token));
    }

    @GetMapping("/push/public-key")
    @Operation(summary = "Khoá công khai Web Push (VAPID)", description = "Trình duyệt dùng để đăng ký nhận thông báo")
    public ResponseEntity<PushKey> pushPublicKey() {
        return ResponseEntity.ok(new PushKey(guestTakeawayService.pushPublicKey()));
    }

    @PostMapping("/takeaway-orders/{token}/push-subscriptions")
    @Operation(summary = "Nhận thông báo khi món xong", description = "PushSubscription của trình duyệt (endpoint + keys)")
    public ResponseEntity<Void> subscribe(@PathVariable String token, @Valid @RequestBody PushSubscriptionRequest request) {
        guestTakeawayService.subscribe(token, request.endpoint(), request.keys().p256dh(), request.keys().auth());
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/takeaway-orders/{token}")
    @Operation(summary = "Theo dõi đơn mang về", description = "Trạng thái thanh toán, món trong bếp, ảnh QR chuyển khoản khi còn chờ chuyển")
    public ResponseEntity<WebTakeawayResponse> getOrder(@PathVariable String token) {
        return ResponseEntity.ok(guestTakeawayService.getOrder(token));
    }
}
