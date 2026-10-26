package com.savoris.repository;

import com.savoris.entity.CustomerSupportTicketEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface CustomerSupportTicketRepository extends JpaRepository<CustomerSupportTicketEntity, Long> {
    List<CustomerSupportTicketEntity> findAllByOrderByUpdatedAtDesc();
    List<CustomerSupportTicketEntity> findByBranchIdOrderByUpdatedAtDesc(Integer branchId);
    Optional<CustomerSupportTicketEntity> findByReferenceCode(String referenceCode);
}
