package com.savoris.payment;

import com.savoris.service.SettingService.BankAccount;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * SePay watches the chain's bank account and calls our webhook for every incoming transfer.
 * Each web takeaway order gets a VietQR with its amount and its code as the transfer content,
 * so the webhook can tell which order the money is for.
 */
@Component
public class Sepay {

    // Order codes look like B1-261010-0005. Banks may drop or replace the dashes and glue words to the code
    // (THANHTOANB12610100005CHUYENKHOAN), so only a digit right after the code rules a match out
    private static final Pattern ORDER_CODE = Pattern.compile("B(\\d+)[^A-Z0-9]*(\\d{6})[^A-Z0-9]*(\\d{4})(?!\\d)");

    private final String apiKey;

    public Sepay(@Value("${savoris.sepay.api-key:}") String apiKey) {
        this.apiKey = apiKey == null ? "" : apiKey.trim();
    }

    // Without an API key the webhook is off and the cashier confirms transfers by hand
    public boolean enabled() {
        return !apiKey.isEmpty();
    }

    // SePay sends "Authorization: Apikey <key>"
    public boolean authorized(String header) {
        if (!enabled() || header == null) {
            return false;
        }
        byte[] expected = ("Apikey " + apiKey).getBytes(StandardCharsets.UTF_8);
        return MessageDigest.isEqual(expected, header.trim().getBytes(StandardCharsets.UTF_8));
    }

    public static String qrUrl(BankAccount account, BigDecimal amount, String content) {
        return "https://qr.sepay.vn/img?acc=" + encode(account.accountNumber())
            + "&bank=" + encode(account.bank())
            + "&amount=" + amount.toBigInteger()
            + "&des=" + encode(content)
            + "&template=compact";
    }

    // Every order code written in a transfer content, in the form the order table stores
    public static Set<String> orderCodesIn(String content) {
        Set<String> codes = new LinkedHashSet<>();
        if (content == null) {
            return codes;
        }
        Matcher m = ORDER_CODE.matcher(content.toUpperCase());
        while (m.find()) {
            codes.add("B" + Integer.parseInt(m.group(1)) + "-" + m.group(2) + "-" + m.group(3));
        }
        return codes;
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
