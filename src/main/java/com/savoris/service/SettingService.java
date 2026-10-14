package com.savoris.service;

import com.savoris.entity.StoredImageEntity;

import java.math.BigDecimal;
import java.util.Optional;

public interface SettingService {
    BigDecimal getVatPercent();
    BigDecimal updateVatPercent(Integer staffId, BigDecimal percent);
    // Link to the image of the branch's bank QR code that web takeaway guests transfer to; null when not set
    String getTransferQrUrl(Integer branchId);
    String updateTransferQrUrl(Integer staffId, Integer branchId, String url);
    // Stores an uploaded QR image and points the transfer QR at it; returns the new link
    String uploadTransferQr(Integer staffId, Integer branchId, String contentType, byte[] data);
    // The uploaded QR image, if any: [contentType, bytes]
    Optional<StoredImageEntity> getTransferQrImage(Integer branchId);
    // The branch's account guests transfer to; each order then gets its own QR with amount and order code. null when not set
    BankAccount getBankAccount(Integer branchId);
    BankAccount updateBankAccount(Integer staffId, Integer branchId, BankAccount account);
    // Guests can pick bank transfer when there is a bank account or a QR image
    boolean transferAvailable(Integer branchId);
    boolean accountBelongsToBranch(Integer branchId, String accountNumber, String subAccount);

    // bank: the bank's short name as SePay/VietQR know it (MBBank, Vietcombank, ACB...)
    record BankAccount(String bank, String accountNumber, String accountName) {
    }
}
