package com.savoris.controller;

import com.savoris.security.CurrentUser;
import com.savoris.security.Roles;
import com.savoris.payment.Sepay;
import com.savoris.service.SettingService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.http.MediaType;

import java.io.IOException;

import java.math.BigDecimal;

@RestController
@RequestMapping("/settings")
@RequiredArgsConstructor
@Tag(name = "Setting", description = "Cài đặt chung của chuỗi")
public class SettingController {

    private final SettingService settingService;
    private final Sepay sepay;

    private static final String WEBHOOK_PATH = "/api/v1/public/sepay/webhook";

    public record VatSetting(@NotNull @DecimalMin("0") @DecimalMax("100") BigDecimal percent) {
    }

    // Empty or null url = no transfer QR: web takeaway guests can then only pay cash at the counter
    public record TransferQrSetting(
        @jakarta.validation.constraints.Pattern(regexp = "^$|^https?://\\S+$|^/api/v1/public/images/\\S+$", message = "Link must start with http:// or https://")
        @jakarta.validation.constraints.Size(max = 1000) String url) {
    }

    public record BankAccountRequest(
        @jakarta.validation.constraints.Pattern(regexp = "^[A-Za-z0-9]{0,30}$", message = "Bank: its short name, letters and digits only (MBBank, Vietcombank...)") String bank,
        @jakarta.validation.constraints.Pattern(regexp = "^\\d{0,20}$", message = "Account number: digits only") String accountNumber,
        @jakarta.validation.constraints.Size(max = 100) String accountName) {
    }

    // autoConfirm: SEPAY_API_KEY is set; webhookPath: where to point SePay's webhook
    public record BankAccountSetting(String bank, String accountNumber, String accountName, boolean autoConfirm, String webhookPath) {
    }

    @GetMapping("/branches/{branchId}/bank-account")
    @PreAuthorize(Roles.ADMIN)
    @Operation(summary = "Tài khoản nhận chuyển khoản", description = "Mỗi đơn mang về có mã QR riêng (số tiền + mã đơn). autoConfirm = đã đặt SEPAY_API_KEY")
    @SecurityRequirement(name = "bearerAuth")
    public ResponseEntity<BankAccountSetting> getBankAccount(@PathVariable Integer branchId) {
        return ResponseEntity.ok(bankAccountSetting(settingService.getBankAccount(branchId)));
    }

    @PutMapping("/branches/{branchId}/bank-account")
    @PreAuthorize(Roles.ADMIN)
    @Operation(summary = "Đổi tài khoản nhận chuyển khoản", description = "{\"bank\": \"MBBank\", \"accountNumber\": \"0123456789\", \"accountName\": \"...\"}; để trống cả hai = bỏ tài khoản")
    @SecurityRequirement(name = "bearerAuth")
    public ResponseEntity<BankAccountSetting> updateBankAccount(@CurrentUser Integer staffId, @PathVariable Integer branchId,
                                                                @Valid @RequestBody BankAccountRequest request) {
        return ResponseEntity.ok(bankAccountSetting(settingService.updateBankAccount(staffId, branchId,
            new SettingService.BankAccount(request.bank(), request.accountNumber(), request.accountName()))));
    }

    private BankAccountSetting bankAccountSetting(SettingService.BankAccount account) {
        return account == null
            ? new BankAccountSetting(null, null, null, sepay.enabled(), WEBHOOK_PATH)
            : new BankAccountSetting(account.bank(), account.accountNumber(), account.accountName(), sepay.enabled(), WEBHOOK_PATH);
    }

    @GetMapping("/branches/{branchId}/transfer-qr")
    @PreAuthorize(Roles.ADMIN)
    @Operation(summary = "Link ảnh mã QR chuyển khoản", description = "Khách đặt mang về chọn chuyển khoản sẽ thấy ảnh này")
    @SecurityRequirement(name = "bearerAuth")
    public ResponseEntity<TransferQrSetting> getTransferQr(@PathVariable Integer branchId) {
        return ResponseEntity.ok(new TransferQrSetting(settingService.getTransferQrUrl(branchId)));
    }

    @PutMapping("/branches/{branchId}/transfer-qr")
    @PreAuthorize(Roles.ADMIN)
    @Operation(summary = "Đổi link ảnh mã QR chuyển khoản", description = "{\"url\": \"https://...png\"}; để trống = tắt chuyển khoản")
    @SecurityRequirement(name = "bearerAuth")
    public ResponseEntity<TransferQrSetting> updateTransferQr(@CurrentUser Integer staffId, @PathVariable Integer branchId,
                                                              @Valid @RequestBody TransferQrSetting request) {
        return ResponseEntity.ok(new TransferQrSetting(settingService.updateTransferQrUrl(staffId, branchId, request.url())));
    }

    @PutMapping(value = "/branches/{branchId}/transfer-qr/image", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize(Roles.ADMIN)
    @Operation(summary = "Tải ảnh mã QR chuyển khoản lên", description = "Form-data field \"file\": ảnh PNG, JPEG hoặc WebP, tối đa 2MB. Thay link QR bằng ảnh vừa tải")
    @SecurityRequirement(name = "bearerAuth")
    public ResponseEntity<TransferQrSetting> uploadTransferQr(@CurrentUser Integer staffId, @PathVariable Integer branchId,
                                                              @RequestParam("file") MultipartFile file) throws IOException {
        return ResponseEntity.ok(new TransferQrSetting(settingService.uploadTransferQr(staffId, branchId, file.getContentType(), file.getBytes())));
    }

    @GetMapping("/vat")
    @Operation(summary = "Thuế VAT hiện tại (%)")
    @SecurityRequirement(name = "bearerAuth")
    public ResponseEntity<VatSetting> getVat() {
        return ResponseEntity.ok(new VatSetting(settingService.getVatPercent()));
    }

    @PutMapping("/vat")
    @PreAuthorize(Roles.ADMIN)
    @Operation(summary = "Đổi thuế VAT (%)", description = "Áp dụng cho các đơn tạo sau khi đổi; đơn cũ giữ nguyên")
    @SecurityRequirement(name = "bearerAuth")
    public ResponseEntity<VatSetting> updateVat(@CurrentUser Integer staffId, @Valid @RequestBody VatSetting request) {
        return ResponseEntity.ok(new VatSetting(settingService.updateVatPercent(staffId, request.percent())));
    }
}
