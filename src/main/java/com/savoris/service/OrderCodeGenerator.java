package com.savoris.service;

import com.savoris.repository.CodeSequenceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Generates codes like B3-261007-0042: branch 3, 7 Oct 2026, 42nd order of the day at that branch.
 */
@Component
@RequiredArgsConstructor
public class OrderCodeGenerator {

    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyMMdd");

    private final CodeSequenceRepository codeSequenceRepository;

    @Transactional(propagation = Propagation.MANDATORY)
    public String next(Integer branchId) {
        return next(branchId, "ORDER", "B");
    }

    // Runs inside the caller's transaction: the branch's counter row stays locked until it commits,
    // so two cashiers ordering at the same time never get the same number
    private String next(Integer branchId, String type, String prefix) {
        LocalDate today = LocalDate.now();
        LocalDateTime seqDate = today.atStartOfDay();
        codeSequenceRepository.createIfAbsent(branchId, type, seqDate);
        codeSequenceRepository.increment(branchId, type, seqDate);
        int value = codeSequenceRepository.findLastValue(branchId, type, seqDate);
        return "%s%d-%s-%04d".formatted(prefix, branchId, today.format(DATE_FORMAT), value);
    }
}
