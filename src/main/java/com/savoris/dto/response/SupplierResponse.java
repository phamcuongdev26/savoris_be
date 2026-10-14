package com.savoris.dto.response;

import com.savoris.entity.SupplierEntity;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class SupplierResponse {
    private Integer id;
    private String name;
    private String phone;
    private String email;
    private String address;
    private String taxCode;
    private Boolean isActive;

    public static SupplierResponse from(SupplierEntity entity) {
        return new SupplierResponse(entity.getId(), entity.getName(), entity.getPhone(), entity.getEmail(),
            entity.getAddress(), entity.getTaxCode(), entity.getIsActive());
    }
}
