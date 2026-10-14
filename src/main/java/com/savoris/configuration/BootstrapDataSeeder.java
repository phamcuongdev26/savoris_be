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
 * Starting point of an empty database: the 4 branches of the chain (CN01–CN04, renamed later from the owner
 * screen) and one login per role so the first people can sign in. No menu, tables, stock, customers or
 * promotions are created: that is real data the chain enters itself.
 *
 * Accounts (all with savoris.bootstrap.password): admin (whole chain); quanly, thungan, phucvu, daubep, kho (CN01).
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
        if (branchRepository.count() == 0) {
            for (int i = 1; i <= BRANCHES; i++) {
                BranchEntity branch = new BranchEntity();
                branch.setCode("CN%02d".formatted(i));
                branch.setName("Chi nhánh %02d".formatted(i));
                branch.setAddress("Chưa cập nhật");
                branch.setCity("Chưa cập nhật");
                branch.setStatus(BranchStatus.ACTIVE);
                branchRepository.save(branch);
            }
            log.info("Created branches CN01–CN{}", "%02d".formatted(BRANCHES));
        }

        if (staffRepository.count() == 0) {
            Integer firstBranch = branchRepository.findByCode("CN01").map(BranchEntity::getId).orElseThrow();
            String hash = passwordEncoder.encode(password);
            account("admin", "Quản trị chuỗi", StaffRole.ADMIN, null, hash);
            account("quanly", "Quản lý CN01", StaffRole.BRANCH_MANAGER, firstBranch, hash);
            account("thungan", "Thu ngân CN01", StaffRole.CASHIER, firstBranch, hash);
            account("phucvu", "Phục vụ CN01", StaffRole.WAITER, firstBranch, hash);
            account("daubep", "Đầu bếp CN01", StaffRole.CHEF, firstBranch, hash);
            account("kho", "Kho CN01", StaffRole.WAREHOUSE, firstBranch, hash);
            log.info("Created role accounts admin, quanly, thungan, phucvu, daubep, kho");
        }
    }

    private void account(String username, String fullName, StaffRole role, Integer branchId, String passwordHash) {
        StaffEntity staff = new StaffEntity();
        staff.setCode(username.toUpperCase());
        staff.setUsername(username);
        staff.setFullName(fullName);
        staff.setPasswordHash(passwordHash);
        staff.setRole(role);
        staff.setStatus(StaffStatus.ACTIVE);
        staff.setBranchId(branchId);
        staffRepository.save(staff);
    }
}
