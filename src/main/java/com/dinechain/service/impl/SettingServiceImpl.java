package com.dinechain.service.impl;

import com.dinechain.entity.SettingEntity;
import com.dinechain.entity.StoredImageEntity;
import com.dinechain.exception.AppException;
import com.dinechain.exception.ErrorCode;
import com.dinechain.image.Cloudinary;
import com.dinechain.image.ImageFiles;
import com.dinechain.repository.SettingRepository;
import com.dinechain.repository.StoredImageRepository;
import com.dinechain.service.SettingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;

/**
 * Chain-wide settings kept in the database so the owner can change them without a redeploy.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SettingServiceImpl implements SettingService {

    // scope_key 0 = whole chain
    private static final int CHAIN_SCOPE = 0;
    private static final String VAT_PERCENT = "VAT_PERCENT";
    private static final String TRANSFER_QR_URL = "TRANSFER_QR_URL";
    private static final String BANK_CODE = "BANK_CODE";
    private static final String BANK_ACCOUNT = "BANK_ACCOUNT";
    private static final String BANK_ACCOUNT_NAME = "BANK_ACCOUNT_NAME";

    private final SettingRepository settingRepository;
    private final StoredImageRepository storedImageRepository;
    private final Cloudinary cloudinary;

    private static final String TRANSFER_QR_IMAGE = "TRANSFER_QR";
    private static final int MAX_IMAGE_BYTES = 2 * 1024 * 1024;
    // Served by GuestTakeawayController; relative so it works through any host (Wi-Fi IP, ngrok)
    static final String TRANSFER_QR_PATH = "/api/v1/public/images/transfer-qr";

    // Used until the owner stores a rate (8% = reduced VAT for restaurants in force until end of 2026)
    @Value("${dinechain.vat-percent:8}")
    private BigDecimal defaultVatPercent;

    @Override
    @Transactional(readOnly = true)
    public BigDecimal getVatPercent() {
        return settingRepository.findByScopeKeyAndSettingKey(CHAIN_SCOPE, VAT_PERCENT)
            .map(setting -> new BigDecimal(setting.getSettingValue()))
            .orElse(defaultVatPercent);
    }

    @Override
    @Transactional
    public BigDecimal updateVatPercent(Integer staffId, BigDecimal percent) {
        save(staffId, VAT_PERCENT, "VAT rate applied to every order, in percent", percent.stripTrailingZeros().toPlainString());
        log.info("VAT changed to {}% by staff {}", percent, staffId);
        return percent;
    }

    @Override
    @Transactional(readOnly = true)
    public String getTransferQrUrl() {
        return settingRepository.findByScopeKeyAndSettingKey(CHAIN_SCOPE, TRANSFER_QR_URL)
            .map(SettingEntity::getSettingValue)
            .filter(value -> !value.isBlank())
            .orElse(null);
    }

    @Override
    @Transactional
    public String updateTransferQrUrl(Integer staffId, String url) {
        String value = url == null ? "" : url.trim();
        save(staffId, TRANSFER_QR_URL, "Image of the bank QR code web takeaway guests transfer to", value);
        log.info("Transfer QR image changed by staff {}", staffId);
        return value.isEmpty() ? null : value;
    }

    @Override
    @Transactional
    public String uploadTransferQr(Integer staffId, String contentType, byte[] data) {
        String type = ImageFiles.check(data, MAX_IMAGE_BYTES);
        if (cloudinary.enabled()) {
            String url = cloudinary.upload(data, type, "dinechain/transfer-qr");
            save(staffId, TRANSFER_QR_URL, "Image of the bank QR code web takeaway guests transfer to", url);
            log.info("Transfer QR image uploaded to Cloudinary by staff {}", staffId);
            return url;
        }
        // Without Cloudinary the picture is kept in the database and served by the API
        StoredImageEntity image = storedImageRepository.findById(TRANSFER_QR_IMAGE).orElseGet(StoredImageEntity::new);
        image.setImageKey(TRANSFER_QR_IMAGE);
        image.setContentType(type);
        image.setData(data);
        image.setUpdatedBy(staffId);
        image.setUpdatedAt(LocalDateTime.now());
        storedImageRepository.save(image);

        // A new version number makes phones that cached the old QR fetch the new one
        String url = TRANSFER_QR_PATH + "?v=" + System.currentTimeMillis();
        save(staffId, TRANSFER_QR_URL, "Image of the bank QR code web takeaway guests transfer to", url);
        log.info("Transfer QR image uploaded by staff {} ({} bytes, {})", staffId, data.length, type);
        return url;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<StoredImageEntity> getTransferQrImage() {
        return storedImageRepository.findById(TRANSFER_QR_IMAGE);
    }

    @Override
    @Transactional(readOnly = true)
    public BankAccount getBankAccount() {
        String bank = value(BANK_CODE);
        String number = value(BANK_ACCOUNT);
        if (bank == null || number == null) {
            return null;
        }
        return new BankAccount(bank, number, value(BANK_ACCOUNT_NAME));
    }

    @Override
    @Transactional
    public BankAccount updateBankAccount(Integer staffId, BankAccount account) {
        String bank = account == null || account.bank() == null ? "" : account.bank().trim();
        String number = account == null || account.accountNumber() == null ? "" : account.accountNumber().trim();
        String name = account == null || account.accountName() == null ? "" : account.accountName().trim();
        if (bank.isEmpty() != number.isEmpty()) {
            throw new AppException(ErrorCode.INVALID_REQUEST, "Enter both the bank and the account number, or clear both");
        }
        save(staffId, BANK_CODE, "Bank of the account web takeaway guests transfer to", bank);
        save(staffId, BANK_ACCOUNT, "Account number web takeaway guests transfer to", number);
        save(staffId, BANK_ACCOUNT_NAME, "Account holder shown to guests", name);
        log.info("Transfer bank account changed by staff {}", staffId);
        return getBankAccount();
    }

    @Override
    @Transactional(readOnly = true)
    public boolean transferAvailable() {
        return getBankAccount() != null || getTransferQrUrl() != null;
    }

    private String value(String key) {
        return settingRepository.findByScopeKeyAndSettingKey(CHAIN_SCOPE, key)
            .map(SettingEntity::getSettingValue)
            .filter(v -> !v.isBlank())
            .orElse(null);
    }

    private void save(Integer staffId, String key, String description, String value) {
        SettingEntity setting = settingRepository.findByScopeKeyAndSettingKey(CHAIN_SCOPE, key)
            .orElseGet(() -> {
                SettingEntity created = new SettingEntity();
                created.setScopeKey(CHAIN_SCOPE);
                created.setSettingKey(key);
                created.setDescription(description);
                return created;
            });
        setting.setSettingValue(value);
        setting.setUpdatedBy(staffId);
        setting.setUpdatedAt(LocalDateTime.now());
        settingRepository.save(setting);
    }
}
