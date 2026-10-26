package com.savoris.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.savoris.ai.AiModelClient;
import com.savoris.dto.request.AiChatRequest;
import com.savoris.dto.request.GuestReservationRequest;
import com.savoris.dto.response.AiChatResponse;
import com.savoris.dto.response.ReservationResponse;
import com.savoris.exception.AppException;
import com.savoris.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
public class AiChatService {
    private static final String SYSTEM = "Bạn là AI Agent hỗ trợ khách hàng trên website Savoris, chuỗi nhà hàng Việt Nam tại Hà Nội. "
        + "Xử lý câu hỏi tự do như một nhân viên hỗ trợ trực tuyến: xác định ý khách, trả lời trực tiếp, ngắn gọn, thân thiện bằng tiếng Việt; "
        + "hướng dẫn từng bước khi cần và chỉ hỏi lại thông tin còn thiếu. Không hỏi khách xác nhận câu trả lời đã giải quyết vấn đề chưa. "
        + "Dùng tools để lấy dữ liệu chi nhánh, menu, giờ đặt bàn; không được tự bịa thông tin. Nếu thiếu thông tin thì nói rõ giới hạn và hướng khách tới nhân viên. "
        + "Không khẳng định trạng thái đơn hàng hoặc giao dịch vì trợ lý chưa có công cụ tra dữ liệu đơn hàng. "
        + "Không tạo đặt bàn khi khách phủ định hoặc chỉ hỏi về việc đặt bàn. Chỉ tạo đặt bàn khi khách yêu cầu đặt bàn rõ ràng trong tin nhắn mới nhất. "
        + "Chỉ xác nhận đã đặt bàn khi tool create_reservation trả về mã đặt bàn.\n\n"
        + com.savoris.ai.SavorisKnowledge.CONTEXT;
    private static final Pattern BOOKING_INTENT = Pattern.compile("(?:đặt\\s*(?:bàn|chỗ)|book\\s+(?:a\\s+)?table)");
    private static final Pattern NEGATED_INTENT = Pattern.compile("(?:\\bkhông\\b|\\bchưa\\b|\\bđừng\\b|\\bkhỏi\\b|\\bnever\\b|\\bdon['’]?t\\b|\\bwon['’]?t\\b|\\bwill\\s+not\\b|\\bdo\\s+not\\b|\\bno\\s+need\\s+to\\b)");
    private static final List<Map<String, Object>> TOOLS = List.of(
        tool("get_branches", "Danh sách chi nhánh và trạng thái nhận đơn", Map.of("type", "object", "properties", Map.of(), "additionalProperties", false)),
        tool("get_menu", "Thực đơn của chi nhánh", objectSchema(Map.of("branchId", integer()))),
        tool("get_slots", "Khung giờ đặt bàn còn trống của chi nhánh theo ngày yyyy-MM-dd", objectSchema(Map.of("branchId", integer(), "date", Map.of("type", "string", "format", "date")))),
        tool("create_reservation", "Tạo đặt bàn cho khách; chỉ dùng khi khách yêu cầu đặt bàn rõ ràng", objectSchema(Map.of(
            "branchId", integer(), "name", Map.of("type", "string"), "phone", Map.of("type", "string"),
            "date", Map.of("type", "string", "description", "Local datetime ISO-8601"), "guests", integer())))
    );

    private final AiModelClient ai;
    private final AiFaqService faqService;
    private final GuestTakeawayService guestService;
    private final ReservationService reservationService;
    private final ObjectMapper mapper;

    public AiChatResponse chat(AiChatRequest request) {
        if (!Boolean.TRUE.equals(request.agentMode())) {
            var faq = faqService.answer(request.message());
            if (faq.isPresent()) return new AiChatResponse(faq.get());
        }
        if (!ai.isConfigured()) throw new AppException(ErrorCode.AI_NOT_CONFIGURED);
        List<Map<String, Object>> messages = new ArrayList<>();
        messages.add(Map.of("role", "system", "content", SYSTEM));
        if (request.history() != null) for (AiChatRequest.AiChatTurn turn : request.history()) {
            messages.add(Map.of("role", turn.role(), "content", turn.content()));
        }
        messages.add(Map.of("role", "user", "content", request.message()));
        String latestMessage = request.message().toLowerCase(Locale.ROOT);
        boolean bookingRequested = BOOKING_INTENT.matcher(latestMessage).find()
            && !NEGATED_INTENT.matcher(latestMessage).find();
        boolean reservationCreated = false;
        for (int iteration = 0; iteration < 3; iteration++) {
            AiModelClient.AiCompletion completion = ai.complete(messages, TOOLS, false);
            if (completion.toolCalls() == null || completion.toolCalls().isEmpty()) {
                return new AiChatResponse(completion.content() == null || completion.content().isBlank()
                    ? "Xin lỗi, mình chưa thể trả lời câu này. Bạn thử hỏi lại nhé."
                    : completion.content().substring(0, Math.min(completion.content().length(), 4000)));
            }
            List<Map<String, Object>> calls = new ArrayList<>();
            for (AiModelClient.AiToolCall call : completion.toolCalls()) {
                Map<String, Object> toolCall = new LinkedHashMap<>();
                toolCall.put("id", call.id());
                toolCall.put("type", "function");
                toolCall.put("function", Map.of("name", call.name(), "arguments", call.argumentsJson()));
                if (call.extraContent() != null && !call.extraContent().isEmpty()) {
                    toolCall.put("extra_content", call.extraContent());
                }
                calls.add(toolCall);
            }
            Map<String, Object> assistant = new LinkedHashMap<>();
            assistant.put("role", "assistant");
            assistant.put("tool_calls", calls);
            if (completion.content() != null) assistant.put("content", completion.content());
            messages.add(assistant);
            for (AiModelClient.AiToolCall call : completion.toolCalls()) {
                Object result;
                try {
                    JsonNode args = mapper.readTree(call.argumentsJson());
                    result = switch (call.name()) {
                        case "get_branches" -> guestService.getBranches();
                        case "get_menu" -> guestService.getBranchMenu(positiveId(args, "branchId"));
                        case "get_slots" -> reservationService.getAvailableSlots(positiveId(args, "branchId"), java.time.LocalDate.parse(requiredText(args, "date")));
                        case "create_reservation" -> {
                            if (!bookingRequested || reservationCreated) yield Map.of("error", "Chỉ tạo một đặt bàn khi khách yêu cầu rõ ràng trong tin nhắn hiện tại.");
                            GuestReservationRequest booking = new GuestReservationRequest(positiveId(args, "branchId"), requiredText(args, "name"),
                                requiredText(args, "phone"), LocalDateTime.parse(requiredText(args, "date")), positiveId(args, "guests"), "Đặt qua trợ lý AI");
                            if (booking.getNumberOfGuests() > 40 || booking.getGuestName().length() > 100 || booking.getGuestPhone().length() > 20
                                || !booking.getGuestPhone().matches("[+0-9() .-]{8,20}") || !booking.getReservationDate().isAfter(LocalDateTime.now())) {
                                yield Map.of("error", "Thông tin đặt bàn không hợp lệ hoặc thời gian không ở tương lai.");
                            }
                            ReservationResponse saved = reservationService.createGuestReservation(booking);
                            reservationCreated = true;
                            yield Map.of("id", saved.getId(), "date", saved.getReservationDate(), "guests", saved.getNumberOfGuests(), "status", saved.getStatus());
                        }
                        default -> Map.of("error", "Tool không được hỗ trợ.");
                    };
                } catch (Exception ex) {
                    result = Map.of("error", "Không thể thực hiện yêu cầu với dữ liệu đã cung cấp. Hãy hỏi khách để làm rõ.");
                }
                messages.add(Map.of("role", "tool", "tool_call_id", call.id(), "name", call.name(), "content", mapper.valueToTree(result).toString()));
            }
        }
        return new AiChatResponse("Mình chưa xử lý xong yêu cầu này. Bạn có thể hỏi lại với thông tin cụ thể hơn nhé.");
    }

    private static Map<String, Object> tool(String name, String description, Map<String, Object> parameters) {
        return Map.of("type", "function", "function", Map.of("name", name, "description", description, "parameters", parameters));
    }
    private static Map<String, Object> objectSchema(Map<String, Object> properties) {
        return Map.of("type", "object", "properties", properties, "required", List.copyOf(properties.keySet()), "additionalProperties", false);
    }
    private static Map<String, Object> integer() { return Map.of("type", "integer", "minimum", 1); }
    private static int positiveId(JsonNode node, String name) {
        JsonNode field = node.path(name);
        if (!field.isIntegralNumber()) throw new IllegalArgumentException("invalid " + name);
        int value = field.asInt(-1);
        if (value < 1) throw new IllegalArgumentException("invalid " + name);
        return value;
    }
    private static String requiredText(JsonNode node, String name) {
        String value = node.path(name).asText("").trim();
        if (value.isEmpty()) throw new IllegalArgumentException("missing " + name);
        return value;
    }
}
