package com.savoris.dto.response;

import com.savoris.entity.CustomerEntity;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class CustomerResponse {
    private Integer id;
    private String fullName;
    private String phone;
    private String email;
    private LocalDateTime birthDate;
    private Integer points;
    private BigDecimal totalSpent;
    private LocalDateTime createdAt;

    public static CustomerResponse from(CustomerEntity entity) {
        CustomerResponse response = new CustomerResponse();
        response.setId(entity.getId());
        response.setFullName(entity.getFullName());
        response.setPhone(entity.getPhone());
        response.setEmail(entity.getEmail());
        response.setBirthDate(entity.getBirthDate());
        response.setPoints(entity.getPoints());
        response.setTotalSpent(entity.getTotalSpent());
        response.setCreatedAt(entity.getCreatedAt());
        return response;
    }
}
