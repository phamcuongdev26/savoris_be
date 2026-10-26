package com.savoris.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.savoris.ai.AiModelClient;
import com.savoris.constant.StaffRole;
import com.savoris.constant.StaffStatus;
import com.savoris.dto.response.BranchLoadResponse;
import com.savoris.dto.response.KitchenForecastResponse;
import com.savoris.entity.BranchEntity;
import com.savoris.exception.AppException;
import com.savoris.exception.ErrorCode;
import com.savoris.repository.BranchRepository;
import com.savoris.repository.StaffRepository;
import com.savoris.security.BranchAccessGuard;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class KitchenForecastService {
    private static final List<String> RISKS = List.of("LOW", "NORMAL", "MEDIUM", "HIGH", "OVERLOADED");
    private static final String SYSTEM = "Bạn là chuyên gia phân tích vận hành bếp nhà hàng. Phân tích dữ liệu được cung cấp, "
        + "đưa ra khuyến nghị ngắn gọn và thực tế bằng tiếng Việt. Chỉ trả JSON đúng format yêu cầu, không giải thích thêm. "
        + "Không khẳng định số liệu ngoài dữ liệu đầu vào; active chefs là số tài khoản đầu bếp ACTIVE, không phải xác nhận ca làm.";

    private final AiModelClient ai;
    private final BranchRepository branchRepository;
    private final StaffRepository staffRepository;
    private final BranchAccessGuard branchAccessGuard;
    private final KitchenService kitchenService;
    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;

    public KitchenForecastResponse forecast(Integer staffId, Integer branchId) {
        if (!ai.isConfigured()) throw new AppException(ErrorCode.AI_NOT_CONFIGURED);
        branchAccessGuard.check(staffId, branchId);
        BranchEntity branch = branchRepository.findById(branchId).orElseThrow(() -> new AppException(ErrorCode.BRANCH_NOT_FOUND));
        BranchLoadResponse load = kitchenService.computeLoad(branch);
        int chefs = (int) staffRepository.findByBranchIdAndStatus(branchId, StaffStatus.ACTIVE).stream()
            .filter(staff -> staff.getRole() == StaffRole.CHEF).count();
        LocalDateTime now = LocalDateTime.now();
        List<HistoryRow> history = history(branchId, now, load.getKitchenCapacity());
        LocalDateTime firstHour = now.plusHours(1).withMinute(0).withSecond(0).withNano(0);
        List<HourStat> nextHours = new ArrayList<>();
        for (int i = 0; i < 2; i++) {
            LocalDateTime hour = firstHour.plusHours(i);
            HourStat stat = history.stream().filter(row -> row.hour == hour.getHour()).findFirst()
                .map(row -> new HourStat(hour, row.orders, row.portions, row.load)).orElse(new HourStat(hour, 0, 0, 0));
            nextHours.add(stat);
        }
        String weekday = now.getDayOfWeek().getDisplayName(TextStyle.FULL, Locale.forLanguageTag("vi-VN"));
        String userPrompt = "Chi nhánh: " + branch.getName() + "\nThời điểm hiện tại: " + now
            + "\nSức chứa bếp: " + load.getKitchenCapacity() + " món/giờ\nSố đầu bếp ACTIVE: " + chefs
            + " (số tài khoản, không phải xác nhận đang có mặt)\nĐang chờ nấu: " + load.getPendingPortions()
            + " món\nĐang nấu: " + load.getCookingPortions() + " món\nTải hiện tại: " + load.getLoadPercent()
            + "%\nLịch sử thứ " + weekday + " trong 4 tuần gần nhất, trung bình theo giờ: " + nextHours
            + "\nDự báo tải 2 giờ kế tiếp. risk chỉ thuộc LOW, NORMAL, MEDIUM, HIGH, OVERLOADED. predictedLoad là phần trăm. "
            + "Trả JSON: {\"forecast\":[{\"hour\":\"19h\",\"predictedLoad\":95,\"risk\":\"HIGH\"},"
            + "{\"hour\":\"20h\",\"predictedLoad\":110,\"risk\":\"OVERLOADED\"}],\"alert\":\"...\","
            + "\"actions\":[\"...\"],\"suggestedPauseAt\":null} .";
        AiModelClient.AiCompletion completion = ai.complete(List.of(
            Map.of("role", "system", "content", SYSTEM), Map.of("role", "user", "content", userPrompt)), List.of(), true);
        try {
            JsonNode root = mapper.readTree(completion.content());
            JsonNode forecasts = root.path("forecast");
            if (!forecasts.isArray() || forecasts.size() != 2) throw new IllegalArgumentException();
            List<KitchenForecastResponse.ForecastHour> output = new ArrayList<>();
            for (JsonNode item : forecasts) {
                String hour = bounded(item.path("hour").asText(), 12);
                JsonNode loadValue = item.path("predictedLoad");
                if (!loadValue.isIntegralNumber()) throw new IllegalArgumentException();
                int predictedLoad = loadValue.asInt(-1);
                String risk = item.path("risk").asText();
                if (hour.isBlank() || !hour.matches("(?:[01]?\\d|2[0-3])h") || predictedLoad < 0 || predictedLoad > 500 || !RISKS.contains(risk)) throw new IllegalArgumentException();
                output.add(new KitchenForecastResponse.ForecastHour(hour, predictedLoad, risk));
            }
            String alert = bounded(root.path("alert").asText(), 500);
            if (alert.isBlank()) throw new IllegalArgumentException();
            JsonNode actionsNode = root.path("actions");
            if (!actionsNode.isArray() || actionsNode.size() < 1 || actionsNode.size() > 2) throw new IllegalArgumentException();
            List<String> actions = new ArrayList<>();
            actionsNode.forEach(value -> {
                String action = bounded(value.asText(), 250);
                if (action.isBlank()) throw new IllegalArgumentException();
                actions.add(action);
            });
            String pause = root.path("suggestedPauseAt").isNull() ? null : bounded(root.path("suggestedPauseAt").asText(), 20);
            if (pause != null && !pause.matches("(?:[01]?\\d|2[0-3]):[0-5]\\d")) throw new IllegalArgumentException();
            List<KitchenForecastResponse.HistoricalHour> historyOutput = history.stream().map(row ->
                new KitchenForecastResponse.HistoricalHour(String.format("%02dh", row.hour), row.orders, row.portions, row.load)).toList();
            return new KitchenForecastResponse(branchId, branch.getName(), load.getKitchenCapacity(), chefs,
                load.getPendingPortions(), load.getCookingPortions(), load.getLoadPercent(), historyOutput,
                output, alert, actions, pause, "Lịch sử là trung bình bốn ngày cùng thứ trong 28 ngày trước; giờ không có đơn được tính 0. "
                    + "Đầu bếp ACTIVE là tài khoản, không phải dữ liệu chấm công.");
        } catch (Exception ex) {
            if (ex instanceof AppException app) throw app;
            throw new AppException(ErrorCode.AI_PROVIDER_FAILED);
        }
    }

    private List<HistoryRow> history(Integer branchId, LocalDateTime now, int capacity) {
        List<Map<String, Object>> rows = jdbc.queryForList("""
            WITH daily AS (
              SELECT EXTRACT(HOUR FROM o.opened_at)::int AS hour,
                     DATE(o.opened_at) AS day,
                     COUNT(DISTINCT o.id)::double precision AS orders,
                     COALESCE(SUM(oi.quantity), 0)::double precision AS portions
              FROM orders o JOIN order_items oi ON oi.order_id = o.id
              WHERE o.branch_id = ? AND o.opened_at >= ? AND o.opened_at < ?
                AND o.status NOT IN ('CANCELLED', 'AWAITING_PAYMENT')
                AND oi.needs_kitchen = true AND oi.status <> 'CANCELLED'
                AND EXTRACT(DOW FROM o.opened_at) = ?
              GROUP BY EXTRACT(HOUR FROM o.opened_at), DATE(o.opened_at)
            )
            SELECT hour, SUM(orders) / 4.0 AS average_orders, SUM(portions) / 4.0 AS average_portions
            FROM daily GROUP BY hour ORDER BY hour
            """, branchId, now.minusDays(28).toLocalDate().atStartOfDay(), now.toLocalDate().atStartOfDay(), now.getDayOfWeek().getValue() % 7);
        return rows.stream().map(row -> {
            int hour = ((Number) row.get("hour")).intValue();
            double orders = ((Number) row.get("average_orders")).doubleValue();
            double portions = ((Number) row.get("average_portions")).doubleValue();
            double load = capacity <= 0 ? 0 : portions / capacity * 100;
            return new HistoryRow(hour, round(orders), round(portions), round(load));
        }).toList();
    }
    private static String bounded(String value, int max) { if (value == null) throw new IllegalArgumentException(); String trimmed = value.trim(); if (trimmed.length() > max) throw new IllegalArgumentException(); return trimmed; }
    private static double round(double value) { return Math.round(value * 10.0) / 10.0; }
    private record HistoryRow(int hour, double orders, double portions, double load) {}
    private record HourStat(LocalDateTime at, double orders, double portions, double load) {}
}
