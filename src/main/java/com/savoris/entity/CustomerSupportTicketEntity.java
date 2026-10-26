package com.savoris.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import java.time.LocalDateTime;

@Entity
@Table(name = "customer_support_ticket")
@Getter @Setter @NoArgsConstructor
public class CustomerSupportTicketEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(name = "reference_code", nullable = false, unique = true, length = 20) private String referenceCode;
    @Column(name = "customer_name", nullable = false, length = 100) private String customerName;
    @Column(name = "customer_phone", nullable = false, length = 20) private String customerPhone;
    @Column(name = "branch_id") private Integer branchId;
    @Column(nullable = false, columnDefinition = "text") private String issue;
    @Column(name = "conversation_context", columnDefinition = "text") private String conversationContext;
    @Column(name = "customer_access_token_hash", nullable = false, length = 64) private String customerAccessTokenHash;
    @Column(nullable = false, length = 20) private String status = "OPEN";
    @Column(name = "handled_by") private Integer handledBy;
    @CreationTimestamp @Column(name = "created_at", nullable = false, updatable = false) private LocalDateTime createdAt;
    @UpdateTimestamp @Column(name = "updated_at", nullable = false) private LocalDateTime updatedAt;
}
