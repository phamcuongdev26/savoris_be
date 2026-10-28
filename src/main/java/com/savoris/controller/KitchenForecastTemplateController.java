package com.savoris.controller;

import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.util.Map;

@RestController
public class KitchenForecastTemplateController {
    private static final String XLSX = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
    private static final Map<String, String> TEMPLATES = Map.of(
        "tai-thap.xlsx", "Dự báo tải thấp.xlsx",
        "cao-diem.xlsx", "Dự báo cao điểm.xlsx",
        "qua-tai.xlsx", "Dự báo quá tải.xlsx"
    );

    @GetMapping("/demo/kitchen-forecast/{fileName:.+}")
    public ResponseEntity<Resource> download(@PathVariable String fileName) {
        String downloadName = TEMPLATES.get(fileName);
        if (downloadName == null) return ResponseEntity.notFound().build();
        Resource resource = new ClassPathResource("demo-data/kitchen-forecast/" + fileName);
        if (!resource.exists()) return ResponseEntity.notFound().build();
        return ResponseEntity.ok()
            .contentType(MediaType.parseMediaType(XLSX))
            .header(HttpHeaders.CONTENT_DISPOSITION,
                ContentDisposition.attachment().filename(downloadName, StandardCharsets.UTF_8).build().toString())
            .body(resource);
    }
}
