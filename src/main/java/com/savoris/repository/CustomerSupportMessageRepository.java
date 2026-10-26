package com.savoris.repository;

import com.savoris.entity.CustomerSupportMessageEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface CustomerSupportMessageRepository extends JpaRepository<CustomerSupportMessageEntity, Long> {
    List<CustomerSupportMessageEntity> findByTicketIdOrderByCreatedAtAscIdAsc(Long ticketId);
}
