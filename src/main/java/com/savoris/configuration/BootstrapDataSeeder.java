package com.savoris.configuration;

import com.savoris.constant.BranchStatus;
import com.savoris.constant.StaffRole;
import com.savoris.constant.StaffStatus;
import com.savoris.entity.BranchEntity;
import com.savoris.entity.StaffEntity;
import com.savoris.repository.BranchRepository;
import com.savoris.repository.StaffRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Dev bootstrap identities for the four Savoris branches.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "savoris.bootstrap.enabled", havingValue = "true", matchIfMissing = true)
public class BootstrapDataSeeder implements ApplicationRunner {

    private static final int BRANCHES = 4;

    private final BranchRepository branchRepository;
    private final StaffRepository staffRepository;
    private final PasswordEncoder passwordEncoder;

    @Value("${savoris.bootstrap.password}")
    private String password;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        for (int i = 1; i <= BRANCHES; i++) {
            String code = "CN%02d".formatted(i);
            String district = switch (i) { case 1 -> "Hoàn Kiếm"; case 2 -> "Cầu Giấy"; case 3 -> "Hàng Giấy"; default -> "Hàng Mã"; };
            String slug = switch (i) { case 1 -> "hoan-kiem"; case 2 -> "cau-giay"; case 3 -> "hang-giay"; default -> "hang-ma"; };
            BranchEntity branch = branchRepository.findByCode(code).orElseGet(() -> {
                BranchEntity created = new BranchEntity();
                created.setCode(code);
                created.setAddress(district);
                created.setCity("Hà Nội");
                created.setStatus(BranchStatus.ACTIVE);
                return created;
            });
            branch.setName(district);
            branch.setSlug(slug);
            if (branch.getAddress() == null || branch.getAddress().trim().length() < 10) {
                branch.setAddress(switch (i) {
                    case 3 -> "Số 40, Phố Hàng Giấy, Hà Nội";
                    case 4 -> "Số 43, Phố Hàng Mã, Hà Nội";
                    default -> "DỮ LIỆU DEMO · Số 00, Phố Mẫu CN%02d, %s".formatted(i, district);
                });
            }
            if (branch.getCity() == null || branch.getCity().equals("Chưa cập nhật")) branch.setCity("Hà Nội");
            if (branch.getPhone() == null || branch.getPhone().isBlank()) {
                branch.setPhone(switch (i) {
                    case 1 -> "02473028181";
                    case 2 -> "02473028182";
                    case 3 -> "04727352235";
                    default -> "0384975244";
                });
            }
            if (branch.getEmail() == null || branch.getEmail().isBlank()) branch.setEmail("cn%02d@savoris.invalid".formatted(i));
            branch = branchRepository.save(branch);
        }
        log.info("Ensured branches CN01–CN04");

        String hash = passwordEncoder.encode(password);
        account("admin", "Quản trị hệ thống", StaffRole.ADMIN, null, hash);
        for (int i = 1; i <= BRANCHES; i++) {
            String code = "CN%02d".formatted(i);
            Integer branchId = branchRepository.findByCode(code).map(BranchEntity::getId).orElseThrow();
            account("manager.cn%02d".formatted(i), "Quản lý " + code, StaffRole.MANAGER, branchId, hash);
            account("staff1.cn%02d".formatted(i), "Nhân viên 1 " + code, StaffRole.STAFF, branchId, hash);
            account("staff2.cn%02d".formatted(i), "Nhân viên 2 " + code, StaffRole.STAFF, branchId, hash);
            account("chef.cn%02d".formatted(i), "Đầu bếp " + code, StaffRole.CHEF, branchId, hash);
        }
        log.info("Ensured development accounts for all four branches");
    }

    private void account(String username, String fullName, StaffRole role, Integer branchId, String passwordHash) {
        if (staffRepository.findByUsername(username).isPresent()) return;
        StaffEntity staff = new StaffEntity();
        staff.setCode(username.toUpperCase());
        staff.setUsername(username);
        staff.setFullName(fullName);
        staff.setEmail(username + "@savoris.local");
        staff.setPasswordHash(passwordHash);
        staff.setRole(role);
        staff.setStatus(StaffStatus.ACTIVE);
        staff.setBranchId(branchId);
        staffRepository.save(staff);
    }
}
