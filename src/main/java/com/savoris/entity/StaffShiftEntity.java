package com.savoris.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "staff_shifts", indexes = {
    @Index(columnList = "staff_id, work_date, shift_id", unique = true),
    @Index(columnList = "work_date")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StaffShiftEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(name = "staff_id", nullable = false)
    private Integer staffId;

    @Column(name = "shift_id", nullable = false)
    private Integer shiftId;

    @Column(name = "work_date", nullable = false)
    private LocalDate workDate;

    @Column(name = "check_in_at")
    private LocalDateTime checkInAt;

    @Column(name = "check_out_at")
    private LocalDateTime checkOutAt;

    @Column(length = 255)
    private String note;
}
