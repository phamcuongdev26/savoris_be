package com.savoris.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import java.time.LocalDateTime;

@Entity
@Table(name = "customer_support_message")
@Getter @Setter @NoArgsConstructor
public class CustomerSupportMessageEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(name = "ticket_id", nullable = false) private Long ticketId;
    @Column(name = "sender_type", nullable = false, length = 10) private String senderType;
    @Column(name = "sender_staff_id") private Integer senderStaffId;
    @Column(nullable = false, columnDefinition = "text") private String body;
    @CreationTimestamp @Column(name = "created_at", nullable = false, updatable = false) private LocalDateTime createdAt;
}
