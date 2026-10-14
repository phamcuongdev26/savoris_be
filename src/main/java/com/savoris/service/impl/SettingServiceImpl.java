package com.savoris.service.impl;

import com.savoris.entity.SettingEntity;
import com.savoris.entity.StoredImageEntity;
import com.savoris.exception.AppException;
import com.savoris.exception.ErrorCode;
import com.savoris.image.Cloudinary;
import com.savoris.image.ImageFiles;
import com.savoris.repository.BranchRepository;
import com.savoris.repository.SettingRepository;
import com.savoris.repository.StoredImageRepository;
import com.savoris.service.SettingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;

/**
 * Chain-wide settings use scope 0; branch payment settings use scope = branchId so takeaway data stays isolated.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SettingServiceImpl implements SettingService {

    private static final int CHAIN_SCOPE = 0;
    private static final String VAT_PERCENT = "VAT_PERCENT";
    private static final String TRANSFER_QR_URL = "TRANSFER_QR_URL";
    private static final String BANK_CODE = "BANK_CODE";
    private static final String BANK_ACCOUNT = "BANK_ACCOUNT";
    private static final String BANK_ACCOUNT_NAME = "BANK_ACCOUNT_NAME";
    private static final String TRANSFER_QR_IMAGE_PREFIX = "TRANSFER_QR_";
    private static final int MAX_IMAGE_BYTES = 2 * 1024 * 1024;
    static final String TRANSFER_QR_PATH = "/api/v1/public/images/transfer-qr";

    private final SettingRepository settingRepository;
    private final StoredImageRepository storedImageRepository;
    private final Cloudinary cloudinary;
    private final BranchRepository branchRepository;

    @Value("${savoris.vat-percent:8}")
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
        save(staffId, CHAIN_SCOPE, VAT_PERCENT, "VAT rate applied to every order, in percent",
            percent.stripTrailingZeros().toPlainString());
        log.info("VAT changed to {}% by staff {}", percent, staffId);
        return percent;
    }

    @Override
    @Transactional(readOnly = true)
    public String getTransferQrUrl(Integer branchId) {
        checkBranch(branchId);
        return settingRepository.findByScopeKeyAndSettingKey(branchId, TRANSFER_QR_URL)
            .map(SettingEntity::getSettingValue)
            .filter(value -> !value.isBlank())
            .orElse(null);
    }

    @Override
    @Transactional
    public String updateTransferQrUrl(Integer staffId, Integer branchId, String url) {
        checkBranch(branchId);
        String value = url == null ? "" : url.trim();
        save(staffId, branchId, TRANSFER_QR_URL, "Image of the bank QR code web takeaway guests transfer to", value);
        log.info("Transfer QR image for branch {} changed by staff {}", branchId, staffId);
        return value.isEmpty() ? null : value;
    }

    @Override
    @Transactional
    public String uploadTransferQr(Integer staffId, Integer branchId, String contentType, byte[] data) {
        checkBranch(branchId);
        String type = ImageFiles.check(data, MAX_IMAGE_BYTES);
        if (cloudinary.enabled()) {
            String url = cloudinary.upload(data, type, "savoris/branches/" + branchId + "/transfer-qr");
            save(staffId, branchId, TRANSFER_QR_URL, "Image of the bank QR code web takeaway guests transfer to", url);
            log.info("Transfer QR image for branch {} uploaded to Cloudinary by staff {}", branchId, staffId);
            return url;
        }

        StoredImageEntity image = storedImageRepository.findById(imageKey(branchId)).orElseGet(StoredImageEntity::new);
        image.setImageKey(imageKey(branchId));
        image.setContentType(type);
        image.setData(data);
        image.setUpdatedBy(staffId);
        image.setUpdatedAt(LocalDateTime.now());
        storedImageRepository.save(image);

        String url = TRANSFER_QR_PATH + "/" + branchId + "?v=" + System.currentTimeMillis();
        save(staffId, branchId, TRANSFER_QR_URL, "Image of the bank QR code web takeaway guests transfer to", url);
        log.info("Transfer QR image for branch {} uploaded by staff {} ({} bytes, {})", branchId, staffId, data.length, type);
        return url;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<StoredImageEntity> getTransferQrImage(Integer branchId) {
        checkBranch(branchId);
        return storedImageRepository.findById(imageKey(branchId));
    }

    @Override
    @Transactional(readOnly = true)
    public BankAccount getBankAccount(Integer branchId) {
        checkBranch(branchId);
        String bank = value(branchId, BANK_CODE);
        String number = value(branchId, BANK_ACCOUNT);
        if (bank == null || number == null) {
            return null;
        }
        return new BankAccount(bank, number, value(branchId, BANK_ACCOUNT_NAME));
    }

    @Override
    @Transactional
    public BankAccount updateBankAccount(Integer staffId, Integer branchId, BankAccount account) {
        checkBranch(branchId);
        String bank = account == null || account.bank() == null ? "" : account.bank().trim();
        String number = account == null || account.accountNumber() == null ? "" : account.accountNumber().trim();
        String name = account == null || account.accountName() == null ? "" : account.accountName().trim();
        if (bank.isEmpty() != number.isEmpty()) {
            throw new AppException(ErrorCode.INVALID_REQUEST, "Enter both the bank and the account number, or clear both");
        }
        save(staffId, branchId, BANK_CODE, "Bank of the account web takeaway guests transfer to", bank);
        save(staffId, branchId, BANK_ACCOUNT, "Account number web takeaway guests transfer to", number);
        save(staffId, branchId, BANK_ACCOUNT_NAME, "Account holder shown to guests", name);
        log.info("Transfer bank account for branch {} changed by staff {}", branchId, staffId);
        return getBankAccount(branchId);
    }

    @Override
    @Transactional(readOnly = true)
    public boolean transferAvailable(Integer branchId) {
        return getBankAccount(branchId) != null || getTransferQrUrl(branchId) != null;
    }

    @Override
    @Transactional(readOnly = true)
    public boolean accountBelongsToBranch(Integer branchId, String accountNumber, String subAccount) {
        BankAccount account = getBankAccount(branchId);
        if (account == null || account.accountNumber() == null) {
            return false;
        }
        return account.accountNumber().equals(accountNumber) || account.accountNumber().equals(subAccount);
    }

    private String value(Integer scopeKey, String key) {
        return settingRepository.findByScopeKeyAndSettingKey(scopeKey, key)
            .map(SettingEntity::getSettingValue)
            .filter(v -> !v.isBlank())
            .orElse(null);
    }

    private void save(Integer staffId, Integer scopeKey, String key, String description, String value) {
        SettingEntity setting = settingRepository.findByScopeKeyAndSettingKey(scopeKey, key)
            .orElseGet(() -> {
                SettingEntity created = new SettingEntity();
                created.setScopeKey(scopeKey);
                created.setBranchId(scopeKey.equals(CHAIN_SCOPE) ? null : scopeKey);
                created.setSettingKey(key);
                created.setDescription(description);
                return created;
            });
        setting.setBranchId(scopeKey.equals(CHAIN_SCOPE) ? null : scopeKey);
        setting.setSettingValue(value);
        setting.setUpdatedBy(staffId);
        setting.setUpdatedAt(LocalDateTime.now());
        settingRepository.save(setting);
    }

    private void checkBranch(Integer branchId) {
        if (branchId == null || !branchRepository.existsById(branchId)) {
            throw new AppException(ErrorCode.BRANCH_NOT_FOUND);
        }
    }

    private String imageKey(Integer branchId) {
        return TRANSFER_QR_IMAGE_PREFIX + branchId;
    }
}
