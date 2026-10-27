package com.savoris.service;

import com.savoris.dto.response.BranchMenuItemResponse;
import com.savoris.dto.response.GuestTableResponse;
import com.savoris.dto.response.PublicBranchResponse;
import com.savoris.dto.response.SlotAvailabilityResponse;
import org.springframework.stereotype.Service;

import java.text.Normalizer;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.regex.Pattern;

/** Answers the documented, high-volume guest questions without a model round trip. */
@Service
public class AiFaqService {
    private static final Pattern MARKS = Pattern.compile("\\p{M}+");
    private static final List<String> BRANCH_TERMS = List.of("chi nhanh", "dia chi", "gio mo cua", "mo cua", "so dien thoai", "dang mo", "nhan don");
    private static final List<String> MENU_TERMS = List.of("thuc don", "menu", "mon gi", "gia mon", "gia cua", "bao nhieu", "con mon");
    private static final Pattern DATE = Pattern.compile("\\b(?:(\\d{4})\\s+(\\d{1,2})\\s+(\\d{1,2})|(\\d{1,2})\\s+(\\d{1,2})\\s+(\\d{4}))\\b");
    private static final List<String> SLOT_TERMS = List.of("khung gio", "gio nao con", "con cho", "con ban", "dat duoc luc", "lich dat ban");

    private final GuestTakeawayService guestService;
    private final ReservationService reservationService;

    public AiFaqService(GuestTakeawayService guestService, ReservationService reservationService) {
        this.guestService = guestService;
        this.reservationService = reservationService;
    }

    public Optional<String> answer(String message) {
        String query = normalize(message);
        if (SLOT_TERMS.stream().anyMatch(query::contains)) return Optional.of(slotAnswer(query));
        if (isBookingHowTo(query)) return Optional.of(bookingAnswer());
        if (isTakeawayPaymentQuestion(query)) return Optional.of(takeawayAnswer());
        if (MENU_TERMS.stream().anyMatch(query::contains)) return Optional.of(menuAnswer(query));
        if (BRANCH_TERMS.stream().anyMatch(query::contains)) return Optional.of(branchAnswer(query));
        return Optional.empty();
    }

    /** Live branch/menu facts bypass the model even in free-form agent mode. */
    public Optional<String> answerFromLiveData(String message) {
        String query = normalize(message);
        if (MENU_TERMS.stream().anyMatch(query::contains)) return Optional.of(menuAnswer(query));
        if (BRANCH_TERMS.stream().anyMatch(query::contains)) return Optional.of(branchAnswer(query));
        return Optional.empty();
    }

    private String slotAnswer(String query) {
        Optional<LocalDate> date = DATE.matcher(query).results().findFirst().map(match -> match.group(1) != null
            ? LocalDate.of(Integer.parseInt(match.group(1)), Integer.parseInt(match.group(2)), Integer.parseInt(match.group(3)))
            : LocalDate.of(Integer.parseInt(match.group(6)), Integer.parseInt(match.group(5)), Integer.parseInt(match.group(4))));
        List<PublicBranchResponse> branches = guestService.getBranches();
        Optional<PublicBranchResponse> branch = findBranch(branches, query);
        if (branch.isEmpty()) return "Bạn muốn đặt bàn ở chi nhánh nào? Hãy cho mình tên chi nhánh và ngày muốn đến.";
        if (date.isEmpty()) return "Bạn muốn xem giờ còn chỗ ở " + branch.get().name() + " vào ngày nào? Hãy nhập ngày theo dạng DD/MM/YYYY.";
        if (reservationService == null) return "Mình chưa tra được khung giờ lúc này. Bạn thử lại sau hoặc gửi yêu cầu cho nhân viên hỗ trợ.";
        List<SlotAvailabilityResponse> slots = reservationService.getAvailableSlots(branch.get().id(), date.get());
        String times = slots.stream().filter(SlotAvailabilityResponse::isAvailable).map(slot -> slot.getSlotTime().toLocalTime().toString())
            .reduce((a, b) -> a + ", " + b).orElse("");
        return times.isBlank()
            ? "Ngày " + date.get().format(DateTimeFormatter.ofPattern("dd/MM/yyyy")) + " tại " + branch.get().name() + " hiện chưa có giờ trống. Bạn thử ngày khác hoặc liên hệ nhân viên nhé."
            : "Các giờ còn chỗ ngày " + date.get().format(DateTimeFormatter.ofPattern("dd/MM/yyyy")) + " tại " + branch.get().name() + ": " + times + ".";
    }

    private String branchAnswer(String query) {
        List<PublicBranchResponse> branches = guestService.getBranches();
        if (branches.isEmpty()) return "Mình chưa có dữ liệu chi nhánh lúc này. Bạn có thể gửi yêu cầu cho nhân viên hỗ trợ.";
        Optional<PublicBranchResponse> selected = findBranch(branches, query);
        if (selected.isPresent()) return formatBranch(selected.get());
        return "Thông tin các chi nhánh:\n" + branches.stream().map(this::formatBranch)
            .reduce((left, right) -> left + "\n\n" + right).orElse("");
    }

    private String menuAnswer(String query) {
        List<PublicBranchResponse> branches = guestService.getBranches();
        Optional<PublicBranchResponse> selected = findBranch(branches, query);
        if (selected.isEmpty()) {
            if (branches.isEmpty()) return "Mình chưa tải được danh sách chi nhánh để tra thực đơn. Bạn thử lại sau nhé.";
            return "Bạn muốn xem thực đơn của chi nhánh nào? Ví dụ: "
                + branches.stream().limit(4).map(PublicBranchResponse::name).reduce((a, b) -> a + ", " + b).orElse("") + ".";
        }

        PublicBranchResponse branch = selected.get();
        GuestTableResponse menu = guestService.getBranchMenu(branch.id());
        if (menu.menu() == null || menu.menu().isEmpty()) return "Thực đơn của " + branch.name() + " hiện chưa có món đang bán.";
        List<BranchMenuItemResponse> items = menu.menu();
        Optional<BranchMenuItemResponse> mentioned = items.stream()
            .filter(item -> item.getName() != null && query.contains(normalize(item.getName())))
            .findFirst();
        if (mentioned.isPresent()) return formatDish(branch, mentioned.get());

        String dishes = items.stream().limit(12).map(item -> "• " + item.getName() + ": " + price(item.getPrice())
            + (item.isAvailable() ? "" : " (tạm hết)")).reduce((a, b) -> a + "\n" + b).orElse("");
        String suffix = items.size() > 12 ? "\nBạn muốn hỏi giá hoặc tình trạng món nào cụ thể?" : "";
        return "Thực đơn " + branch.name() + ":\n" + dishes + suffix;
    }

    private Optional<PublicBranchResponse> findBranch(List<PublicBranchResponse> branches, String query) {
        return branches.stream().filter(branch -> {
            String code = normalize(branch.code());
            String name = normalize(branch.name());
            return (!code.isBlank() && query.contains(code)) || (!name.isBlank() && query.contains(name));
        }).findFirst();
    }

    private String formatBranch(PublicBranchResponse branch) {
        StringBuilder answer = new StringBuilder("• ").append(branch.code()).append(" · ").append(branch.name());
        if (branch.address() != null && !branch.address().isBlank()) answer.append(" — ").append(branch.address());
        if (branch.city() != null && !branch.city().isBlank()
            && (branch.address() == null || !normalize(branch.address()).contains(normalize(branch.city())))) {
            answer.append(", ").append(branch.city());
        }
        if (branch.hours() != null && !branch.hours().isEmpty()) {
            answer.append(". Giờ mở cửa: ").append(branch.hours().stream().map(h -> h.days() + ": " + h.time())
                .reduce((a, b) -> a + "; " + b).orElse("đang cập nhật"));
        }
        answer.append(". Hiện ").append(branch.openNow() ? "đang mở" : "đã đóng cửa");
        answer.append(branch.acceptingTakeaway() ? " và đang nhận đơn mang về" : " và đang tạm ngưng nhận đơn mang về").append('.');
        if (branch.phone() != null && !branch.phone().isBlank()) answer.append(" Điện thoại: ").append(branch.phone()).append('.');
        return answer.toString();
    }

    private String formatDish(PublicBranchResponse branch, BranchMenuItemResponse dish) {
        return dish.getName() + " tại " + branch.name() + " có giá " + price(dish.getPrice())
            + (dish.isAvailable() ? " và hiện còn món." : " và hiện đang tạm hết.");
    }

    private String price(java.math.BigDecimal amount) {
        return amount == null ? "đang cập nhật giá" : String.format(Locale.forLanguageTag("vi-VN"), "%,.0f ₫", amount);
    }

    private boolean isBookingHowTo(String query) {
        return (query.contains("dat ban") || query.contains("dat cho"))
            && (query.contains("the nao") || query.contains("cach dat") || query.contains("nhu the nao")
            || query.contains("cach thuc") || query.contains("dat ban khong"));
    }

    private String bookingAnswer() {
        return "Bước 1: Bấm “Đặt bàn” trên website.\n"
            + "Bước 2: Chọn chi nhánh bạn muốn đến.\n"
            + "Bước 3: Chọn ngày, giờ còn chỗ và số khách.\n"
            + "Bước 4: Nhập tên, số điện thoại rồi gửi yêu cầu; chờ website báo kết quả nhé.";
    }

    private boolean isTakeawayPaymentQuestion(String query) {
        boolean takeaway = query.contains("mang ve") || query.contains("mang di") || query.contains("lay mon");
        boolean payment = query.contains("thanh toan") || query.contains("chuyen khoan") || query.contains("qr") || query.contains("tra tien");
        return takeaway && payment;
    }

    private String takeawayAnswer() {
        return "Bước 1: Bấm “Mang về” và chọn chi nhánh.\n"
            + "Bước 2: Chọn món và số lượng.\n"
            + "Bước 3: Nhập tên, số điện thoại nhận món.\n"
            + "Bước 4: Chọn thanh toán tại quầy hoặc quét QR nếu chi nhánh hỗ trợ.\n"
            + "Bước 5: Gửi đơn; đơn chuyển khoản sẽ vào bếp sau khi SePay xác nhận nhé.";
    }

    private static String normalize(String value) {
        if (value == null) return "";
        String decomposed = Normalizer.normalize(value, Normalizer.Form.NFD);
        return MARKS.matcher(decomposed).replaceAll("").replace('đ', 'd').replace('Đ', 'd')
            .toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", " ").trim().replaceAll("\\s+", " ");
    }
}
