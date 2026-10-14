package com.savoris.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "code_sequences", uniqueConstraints = @UniqueConstraint(columnNames = {"branch_id", "type", "seq_date"}))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CodeSequenceEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(name = "branch_id", nullable = false)
    private Integer branchId;

    @Column(nullable = false, length = 20)
    private String type;

    @Column(name = "seq_date", nullable = false)
    private LocalDateTime seqDate;

    @Column(name = "last_value", nullable = false)
    private Integer lastValue = 0;
}
