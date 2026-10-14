package com.savoris.configuration;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import org.springframework.context.annotation.Configuration;

/**
 * Swagger UI at /api/v1/swagger-ui/index.html: log in via POST /auth/login, then paste the accessToken into "Authorize".
 */
@Configuration
@OpenAPIDefinition(info = @Info(title = "Savoris API", version = "v1",
    description = "Quản lý chuỗi nhà hàng: đặt món, bếp, giờ cao điểm, thanh toán tiền mặt, kho, đặt bàn"))
@SecurityScheme(name = "bearerAuth", type = SecuritySchemeType.HTTP, scheme = "bearer", bearerFormat = "JWT")
public class OpenApiConfig {
}
