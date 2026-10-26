package com.savoris;

import com.savoris.constant.StaffRole;
import com.savoris.constant.StaffStatus;
import com.savoris.entity.StaffEntity;
import com.savoris.repository.RefreshTokenRepository;
import com.savoris.repository.StaffRepository;
import com.savoris.security.BranchAccessGuard;
import com.savoris.service.impl.StaffServiceImpl;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class StaffReactivationTest {
    @Mock StaffRepository staffRepository;
    @Mock PasswordEncoder passwordEncoder;
    @Mock BranchAccessGuard branchAccessGuard;
    @Mock RefreshTokenRepository refreshTokenRepository;
    @InjectMocks StaffServiceImpl staffService;

    @Test
    void adminCanReactivateLockedStaff() {
        StaffEntity admin = StaffEntity.builder().id(1).role(StaffRole.ADMIN).build();
        StaffEntity staff = StaffEntity.builder().id(2).branchId(1).role(StaffRole.STAFF).status(StaffStatus.LOCKED).build();
        when(staffRepository.findById(2)).thenReturn(Optional.of(staff));
        when(staffRepository.findById(1)).thenReturn(Optional.of(admin));
        when(staffRepository.save(staff)).thenReturn(staff);

        staffService.activateStaff(1, 2);

        assertEquals(StaffStatus.ACTIVE, staff.getStatus());
        verify(staffRepository).save(staff);
    }
}
