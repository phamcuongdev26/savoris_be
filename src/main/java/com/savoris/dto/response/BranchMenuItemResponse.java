package com.savoris.dto.response;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class BranchMenuItemResponse {
    private Integer menuItemId;
    private Integer categoryId;
    private String code;
    private String name;
    private String unit;
    // Chain price
    private BigDecimal basePrice;
    // Branch's own price, null when it sells at the chain price
    private BigDecimal branchPrice;
    // What the customer pays here
    private BigDecimal price;
    private boolean available;
    // Photo of the dish from the chain menu, null when none was set
    private String imageUrl;
    // false = drink / ready-made: handed out by the floor staff, not cooked
    private boolean needsKitchen;
    // Ready-made dish whose stock at this branch cannot make one more portion (counted in available = false)
    private boolean outOfStock;
}
