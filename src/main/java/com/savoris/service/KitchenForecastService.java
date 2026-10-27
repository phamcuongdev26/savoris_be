package com.savoris.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.savoris.ai.AiModelClient;
import com.savoris.constant.StaffRole;
import com.savoris.constant.StaffStatus;
import com.savoris.dto.response.BranchLoadResponse;
import com.savoris.dto.response.KitchenForecastResponse;
import com.savoris.entity.BranchEntity;
import com.savoris.constant.OrderItemStatus;
import com.savoris.exception.AppException;
import com.savoris.exception.ErrorCode;
import com.savoris.repository.BranchRepository;
import com.savoris.repository.StaffRepository;
import com.savoris.security.BranchAccessGuard;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

@Service
@RequiredArgsConstructor
@Slf4j
public class KitchenForecastService {
    private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
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
        return forecast(staffId, branchId, false);
    }

    public KitchenForecastResponse forecast(Integer staffId, Integer branchId, boolean sampleData) {
        if (!ai.isConfigured()) throw new AppException(ErrorCode.AI_NOT_CONFIGURED);
        branchAccessGuard.check(staffId, branchId);
        BranchEntity branch = branchRepository.findById(branchId).orElseThrow(() -> new AppException(ErrorCode.BRANCH_NOT_FOUND));
        BranchLoadResponse load = sampleData ? null : kitchenService.computeLoad(branch);
        int capacity = sampleData ? 50 : load.getKitchenCapacity();
        long pending = sampleData ? 24 : load.getPendingPortions();
        long cooking = sampleData ? 8 : load.getCookingPortions();
        int currentLoad = sampleData ? 64 : load.getLoadPercent();
        int chefs = sampleData ? 4 : (int) staffRepository.findByBranchIdAndStatus(branchId, StaffStatus.ACTIVE).stream()
            .filter(staff -> staff.getRole() == StaffRole.CHEF).count();
        String pendingByItem = sampleData ? "Dữ liệu mô phỏng: Beef Steak ×9, Pasta ×6"
            : kitchenService.getQueue(staffId, branchId).stream()
                .filter(ticket -> ticket.getStatus() == OrderItemStatus.PENDING)
                .collect(Collectors.groupingBy(
                    ticket -> ticket.getItemName() == null ? "Món chưa có tên" : ticket.getItemName(),
                    TreeMap::new,
                    Collectors.summingLong(ticket -> ticket.getQuantity() == null ? 0 : ticket.getQuantity())))
                .entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                .limit(5)
                .map(entry -> entry.getKey() + " ×" + entry.getValue())
                .collect(Collectors.joining(", "));
        if (pendingByItem.isBlank()) pendingByItem = "Không có món đang chờ nấu";
        LocalDateTime now = LocalDateTime.now(BUSINESS_ZONE);
        int databaseToBusinessShiftMinutes = databaseToBusinessShiftMinutes();
        Map<java.time.LocalDate, List<HistoryRow>> historyByBusinessDate = new HashMap<>();
        List<HistoryRow> history = sampleData ? sampleHistory(now, capacity)
            : history(branchId, now, capacity, databaseToBusinessShiftMinutes);
        if (!sampleData) historyByBusinessDate.put(now.toLocalDate(), history);
        LocalDateTime firstHour = now.plusHours(1).withMinute(0).withSecond(0).withNano(0);
        List<HourStat> nextHours = new ArrayList<>();
        for (int i = 0; i < 2; i++) {
            LocalDateTime hour = firstHour.plusHours(i);
            List<HistoryRow> sameWeekdayHistory = sampleData ? history : historyByBusinessDate.computeIfAbsent(hour.toLocalDate(),
                ignored -> history(branchId, hour, capacity, databaseToBusinessShiftMinutes));
            String forecastWeekday = hour.getDayOfWeek().getDisplayName(TextStyle.FULL, Locale.forLanguageTag("vi-VN"));
            HourStat stat = sameWeekdayHistory.stream().filter(row -> row.hour == hour.getHour()).findFirst()
                .map(row -> new HourStat(hour, forecastWeekday, row.orders, row.portions, row.load))
                .orElse(new HourStat(hour, forecastWeekday, 0, 0, 0));
            nextHours.add(stat);
        }
        String userPrompt = "Chi nhánh: " + branch.getName() + (sampleData ? " (kịch bản dữ liệu mô phỏng)" : "") + "\nThời điểm hiện tại: " + now
            + "\nSức chứa bếp: " + capacity + " món/giờ\nSố đầu bếp ACTIVE: " + chefs
            + " (số tài khoản, không phải xác nhận đang có mặt)\nĐang chờ nấu: " + pending
            + " món\nĐang nấu: " + cooking + " món\nTải hiện tại: " + currentLoad
            + "%\nMón chờ theo tên và số phần, đã sắp từ nhiều đến ít: " + pendingByItem
            + "\nLịch sử bốn tuần gần nhất theo đúng thứ của từng giờ dự báo, trung bình theo giờ: " + nextHours
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
            String note = sampleData
                ? "DỮ LIỆU MÔ PHỎNG: số liệu được gửi cho AI chỉ để thử nghiệm, không phản ánh hoạt động thật. "
                : "Bảng lịch sử là trung bình bốn ngày cùng thứ với ngày hiện tại trong 28 ngày trước; giờ không có đơn được tính 0. ";
            return new KitchenForecastResponse(branchId, branch.getName(), capacity, chefs,
                pending, cooking, currentLoad, historyOutput,
                output, alert, actions, pause, note
                    + "Dữ liệu gửi model được lọc theo đúng thứ địa phương của từng giờ dự báo. Đầu bếp ACTIVE là tài khoản, không phải dữ liệu chấm công.",
                sampleData);
        } catch (Exception ex) {
            if (ex instanceof AppException app) throw app;
            log.warn("Kitchen forecast response from AI was invalid (branchId={}, sampleData={}, error={})",
                branchId, sampleData, ex.getClass().getSimpleName());
            throw new AppException(ErrorCode.AI_PROVIDER_FAILED);
        }
    }

    private static List<HistoryRow> sampleHistory(LocalDateTime now, int capacity) {
        int firstHour = now.plusHours(1).getHour();
        int secondHour = now.plusHours(2).getHour();
        return IntStream.range(0, 24).mapToObj(hour -> {
            double orders = 0;
            double portions = 0;
            if (hour == firstHour) { orders = 15; portions = 48; }
            else if (hour == secondHour) { orders = 22; portions = 71; }
            else if (hour == Math.floorMod(firstHour - 1, 24)) { orders = 8; portions = 24; }
            else if (hour == Math.floorMod(secondHour + 1, 24)) { orders = 18; portions = 55; }
            return new HistoryRow(hour, orders, portions, capacity <= 0 ? 0 : round(portions / capacity * 100));
        }).toList();
    }

    private List<HistoryRow> history(Integer branchId, LocalDateTime businessTime, int capacity, int databaseToBusinessShiftMinutes) {
        LocalDateTime businessStart = businessTime.minusDays(28).toLocalDate().atStartOfDay();
        LocalDateTime businessEnd = businessTime.toLocalDate().atStartOfDay();
        List<Map<String, Object>> rows = jdbc.queryForList("""
            WITH local_orders AS (
              SELECT o.id, o.status AS order_status, oi.quantity, oi.needs_kitchen, oi.status AS item_status,
                     o.opened_at + (? * INTERVAL '1 minute') AS business_opened_at
              FROM orders o JOIN order_items oi ON oi.order_id = o.id
              WHERE o.branch_id = ? AND o.opened_at >= ? AND o.opened_at < ?
            ), daily AS (
              SELECT EXTRACT(HOUR FROM business_opened_at)::int AS hour,
                     DATE(business_opened_at) AS day,
                     COUNT(DISTINCT id)::double precision AS orders,
                     COALESCE(SUM(quantity), 0)::double precision AS portions
              FROM local_orders
              WHERE order_status NOT IN ('CANCELLED', 'AWAITING_PAYMENT')
                AND needs_kitchen = true AND item_status <> 'CANCELLED'
                AND EXTRACT(DOW FROM business_opened_at) = ?
              GROUP BY EXTRACT(HOUR FROM business_opened_at), DATE(business_opened_at)
            )
            SELECT hour, SUM(orders) / 4.0 AS average_orders, SUM(portions) / 4.0 AS average_portions
            FROM daily GROUP BY hour ORDER BY hour
            """, databaseToBusinessShiftMinutes, branchId,
                businessStart.minusMinutes(databaseToBusinessShiftMinutes),
                businessEnd.minusMinutes(databaseToBusinessShiftMinutes), businessTime.getDayOfWeek().getValue() % 7);
        Map<Integer, HistoryRow> rowsByHour = rows.stream().map(row -> {
            int hour = ((Number) row.get("hour")).intValue();
            double orders = ((Number) row.get("average_orders")).doubleValue();
            double portions = ((Number) row.get("average_portions")).doubleValue();
            double load = capacity <= 0 ? 0 : portions / capacity * 100;
            return new HistoryRow(hour, round(orders), round(portions), round(load));
        }).collect(java.util.stream.Collectors.toMap(HistoryRow::hour, row -> row));
        return IntStream.range(0, 24).mapToObj(hour -> rowsByHour.getOrDefault(hour, new HistoryRow(hour, 0, 0, 0))).toList();
    }
    private static int databaseToBusinessShiftMinutes() {
        Instant now = Instant.now();
        ZoneOffset databaseOffset = ZoneId.systemDefault().getRules().getOffset(now);
        ZoneOffset businessOffset = BUSINESS_ZONE.getRules().getOffset(now);
        return (businessOffset.getTotalSeconds() - databaseOffset.getTotalSeconds()) / 60;
    }
    private static String bounded(String value, int max) { if (value == null) throw new IllegalArgumentException(); String trimmed = value.trim(); if (trimmed.length() > max) throw new IllegalArgumentException(); return trimmed; }
    private static double round(double value) { return Math.round(value * 10.0) / 10.0; }
    private record HistoryRow(int hour, double orders, double portions, double load) {}
    private record HourStat(LocalDateTime at, String weekday, double orders, double portions, double load) {}
}
