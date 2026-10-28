package com.savoris.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.savoris.ai.AiModelClient;
import com.savoris.constant.StaffRole;
import com.savoris.constant.StaffStatus;
import com.savoris.dto.response.BranchLoadResponse;
import com.savoris.dto.response.KitchenForecastResponse;
import com.savoris.dto.request.KitchenForecastSampleRequest;
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
import java.time.format.DateTimeFormatter;
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
        if (!sampleData && !ai.isConfigured()) throw new AppException(ErrorCode.AI_NOT_CONFIGURED);
        branchAccessGuard.check(staffId, branchId);
        BranchEntity branch = branchRepository.findById(branchId).orElseThrow(() -> new AppException(ErrorCode.BRANCH_NOT_FOUND));
        SampleProfile sample = sampleData ? sampleProfile(branchId) : null;
        BranchLoadResponse load = sampleData ? null : kitchenService.computeLoad(branch);
        int capacity = sampleData ? sample.capacity : load.getKitchenCapacity();
        long pending = sampleData ? sample.pending : load.getPendingPortions();
        long cooking = sampleData ? sample.cooking : load.getCookingPortions();
        int currentLoad = sampleData ? sample.currentLoad : load.getLoadPercent();
        int chefs = sampleData ? sample.chefs : (int) staffRepository.findByBranchIdAndStatus(branchId, StaffStatus.ACTIVE).stream()
            .filter(staff -> staff.getRole() == StaffRole.CHEF).count();
        String pendingByItem = sampleData ? sample.pendingByItem
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
        List<HistoryRow> history = sampleData ? sampleHistory(now, capacity, sample)
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
        if (sampleData) return fallbackForecast(branchId, branch.getName(), capacity, chefs, pending, cooking,
            currentLoad, history, nextHours, true);
        AiModelClient.AiCompletion completion;
        try {
            completion = ai.complete(List.of(
                Map.of("role", "system", "content", SYSTEM), Map.of("role", "user", "content", userPrompt)), List.of(), true);
        } catch (AppException ex) {
            if (ex.getErrorCode() != ErrorCode.AI_PROVIDER_FAILED) throw ex;
            log.warn("AI provider failed for kitchen forecast (branchId={}); using history-based forecast", branchId);
            return fallbackForecast(branchId, branch.getName(), capacity, chefs, pending, cooking,
                currentLoad, history, nextHours, false);
        }
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
            return fallbackForecast(branchId, branch.getName(), capacity, chefs, pending, cooking,
                currentLoad, history, nextHours, false);
        }
    }

    public KitchenForecastResponse forecastFromSample(Integer staffId, Integer branchId,
        KitchenForecastSampleRequest request) {
        branchAccessGuard.check(staffId, branchId);
        BranchEntity branch = branchRepository.findById(branchId)
            .orElseThrow(() -> new AppException(ErrorCode.BRANCH_NOT_FOUND));
        validateSampleRequest(request);

        int capacity = request.kitchenCapacity();
        List<HistoryRow> historyByHour = request.history().stream()
            .map(row -> new HistoryRow(row.hour(), round(row.averageOrders()), round(row.averagePortions()),
                round(row.averagePortions() / capacity * 100)))
            .sorted(java.util.Comparator.comparingInt(HistoryRow::hour)).toList();
        LocalDateTime firstHour = LocalDateTime.now(BUSINESS_ZONE).plusHours(1)
            .withMinute(0).withSecond(0).withNano(0);
        List<HourStat> nextHours = IntStream.range(0, 2).mapToObj(i -> {
            LocalDateTime hour = firstHour.plusHours(i);
            HistoryRow row = historyByHour.get(hour.getHour());
            String weekday = hour.getDayOfWeek().getDisplayName(TextStyle.FULL, Locale.forLanguageTag("vi-VN"));
            return new HourStat(hour, weekday, row.orders, row.portions, row.load);
        }).toList();
        return fallbackForecast(branchId, branch.getName(), capacity, request.activeChefAccounts(),
            request.pendingPortions(), request.cookingPortions(), request.currentLoadPercent(),
            historyByHour, nextHours, true);
    }

    private static void validateSampleRequest(KitchenForecastSampleRequest request) {
        if (request == null || request.kitchenCapacity() == null || request.kitchenCapacity() < 1
            || request.kitchenCapacity() > 500 || request.pendingPortions() == null
            || request.pendingPortions() < 0 || request.pendingPortions() > 500
            || request.cookingPortions() == null || request.cookingPortions() < 0
            || request.cookingPortions() > 500 || request.currentLoadPercent() == null
            || request.currentLoadPercent() < 0 || request.currentLoadPercent() > 500
            || request.activeChefAccounts() == null || request.activeChefAccounts() < 0
            || request.activeChefAccounts() > 100 || request.history() == null || request.history().size() != 24) {
            throw new AppException(ErrorCode.INVALID_REQUEST,
                "File demo cần sức chứa, tải hiện tại hợp lệ và đúng 24 dòng lịch sử theo giờ.");
        }
        boolean[] seenHours = new boolean[24];
        for (KitchenForecastSampleRequest.HistoryHour row : request.history()) {
            if (row == null || row.hour() == null || row.hour() < 0 || row.hour() > 23
                || seenHours[row.hour()] || !validSampleNumber(row.averageOrders())
                || !validSampleNumber(row.averagePortions())) {
                throw new AppException(ErrorCode.INVALID_REQUEST,
                    "Lịch sử cần đủ giờ 0–23, không trùng giờ và chỉ chứa số không âm.");
            }
            seenHours[row.hour()] = true;
        }
    }

    private static boolean validSampleNumber(Double value) {
        return value != null && Double.isFinite(value) && value >= 0 && value <= 1_000_000;
    }

    private KitchenForecastResponse fallbackForecast(Integer branchId, String branchName, int capacity, int chefs,
        long pending, long cooking, int currentLoad, List<HistoryRow> history, List<HourStat> nextHours,
        boolean sampleData) {
        List<KitchenForecastResponse.ForecastHour> forecast = nextHours.stream().map(hour -> {
            int predictedLoad = (int) Math.max(0, Math.min(500, Math.round(hour.load)));
            return new KitchenForecastResponse.ForecastHour(String.format("%02dh", hour.at.getHour()),
                predictedLoad, riskFor(predictedLoad));
        }).toList();
        int peakLoad = forecast.stream().mapToInt(KitchenForecastResponse.ForecastHour::predictedLoad).max().orElse(0);
        boolean busy = peakLoad >= 100;
        List<String> actions = busy
            ? List.of("Chuẩn bị sơ chế trước giờ cao điểm", "Theo dõi hàng đợi và bổ sung người khi cần")
            : List.of("Duy trì nhân sự hiện tại", "Theo dõi hàng đợi trong hai giờ tới");
        String pauseAt = peakLoad >= 140 && !nextHours.isEmpty()
            ? nextHours.get(0).at.format(DateTimeFormatter.ofPattern("HH:mm")) : null;
        String alert = sampleData
            ? "Dữ liệu mô phỏng: tải dự báo cao nhất " + peakLoad + "% trong hai giờ tới."
            : "Dự báo tự động từ lịch sử: tải dự kiến cao nhất " + peakLoad + "% trong hai giờ tới.";
        String note = sampleData
            ? "DỮ LIỆU MÔ PHỎNG: số liệu được tạo để kiểm thử, không phản ánh hoạt động thật. "
            : "AI không trả dữ liệu hợp lệ; dự báo được tính từ trung bình lịch sử theo giờ. ";
        List<KitchenForecastResponse.HistoricalHour> historyOutput = history.stream().map(row ->
            new KitchenForecastResponse.HistoricalHour(String.format("%02dh", row.hour), row.orders, row.portions, row.load)).toList();
        return new KitchenForecastResponse(branchId, branchName, capacity, chefs, pending, cooking, currentLoad,
            historyOutput, forecast, alert, actions, pauseAt,
            note + "Đầu bếp ACTIVE là số tài khoản, không xác nhận ca làm.", sampleData);
    }

    private static String riskFor(int load) {
        if (load < 50) return "LOW";
        if (load < 75) return "NORMAL";
        if (load < 100) return "MEDIUM";
        if (load < 140) return "HIGH";
        return "OVERLOADED";
    }

    private static SampleProfile sampleProfile(Integer branchId) {
        if (Integer.valueOf(2).equals(branchId)) {
            return new SampleProfile(36, 11, 4, 42, 3, "Dữ liệu mô phỏng: Gà nướng ×6, Cơm chiên ×3",
                9, 24, 13, 31);
        }
        return new SampleProfile(50, 24, 8, 64, 4, "Dữ liệu mô phỏng: Beef Steak ×9, Pasta ×6",
            15, 48, 22, 71);
    }

    private static List<HistoryRow> sampleHistory(LocalDateTime now, int capacity, SampleProfile sample) {
        int firstHour = now.plusHours(1).getHour();
        int secondHour = now.plusHours(2).getHour();
        return IntStream.range(0, 24).mapToObj(hour -> {
            double orders = 0;
            double portions = 0;
            if (hour == firstHour) { orders = sample.firstOrders; portions = sample.firstPortions; }
            else if (hour == secondHour) { orders = sample.secondOrders; portions = sample.secondPortions; }
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
    private record SampleProfile(int capacity, long pending, long cooking, int currentLoad, int chefs,
        String pendingByItem, double firstOrders, double firstPortions, double secondOrders, double secondPortions) {}
}
