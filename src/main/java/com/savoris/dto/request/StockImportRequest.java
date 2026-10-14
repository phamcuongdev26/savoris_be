package com.savoris.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.List;

/**
 * Stock count entered by hand (one row) or read from an Excel/CSV file (many rows).
 * quantity = what is on the shelf now; unit is needed only for an ingredient the chain does not have yet.
 */
public record StockImportRequest(@NotEmpty(message = "No rows to import") @Size(max = 2000) @Valid List<Row> rows) {

    public record Row(
        @NotBlank(message = "Ingredient name is required") @Size(max = 255) String name,
        @Size(max = 20) String unit,
        @NotNull(message = "Quantity is required") @DecimalMin(value = "0", message = "Quantity must not be negative") BigDecimal quantity,
        @DecimalMin(value = "0", message = "Minimum stock must not be negative") BigDecimal minStock
    ) {
    }
}
