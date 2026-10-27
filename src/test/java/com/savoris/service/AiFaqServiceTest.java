package com.savoris.service;

import com.savoris.dto.response.BranchMenuItemResponse;
import com.savoris.dto.response.GuestTableResponse;
import com.savoris.dto.response.PublicBranchResponse;
import com.savoris.dto.response.SlotAvailabilityResponse;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AiFaqServiceTest {
    private final GuestTakeawayService guestService = mock(GuestTakeawayService.class);
    private final ReservationService reservationService = mock(ReservationService.class);
    private final AiFaqService faq = new AiFaqService(guestService, reservationService);

    @Test
    void answersBranchHoursAndAddressFromLiveBranchData() {
        when(guestService.getBranches()).thenReturn(List.of(branch()));

        String answer = faq.answer("Chi nhánh Hoàn Kiếm ở đâu và mở cửa lúc nào?").orElseThrow();

        assertThat(answer).contains("CN01 · Hoàn Kiếm", "12 Lý Quốc Sư", "10:00 – 22:00", "đang mở");
    }

    @Test
    void doesNotRepeatCityWhenFullAddressAlreadyContainsIt() {
        PublicBranchResponse branch = new PublicBranchResponse(3, "CN03", "Hàng Giấy", "hang-giay",
            "Số 40, Phố Hàng Giấy, Hà Nội", true, 5, true, "Hà Nội", null, null, true,
            "04727352235", "cn03@savoris.invalid", List.of());
        when(guestService.getBranches()).thenReturn(List.of(branch));

        String answer = faq.answer("Địa chỉ chi nhánh CN03").orElseThrow();

        assertThat(answer.split("Hà Nội", -1)).hasSize(2);
    }

    @Test
    void answersMenuAndPriceFromSelectedBranchData() {
        BranchMenuItemResponse pho = new BranchMenuItemResponse(7, 1, "PHO", "Phở bò", "bát",
            new BigDecimal("85000"), null, new BigDecimal("85000"), true, null, true, false, true);
        when(guestService.getBranches()).thenReturn(List.of(branch()));
        when(guestService.getBranchMenu(1)).thenReturn(new GuestTableResponse("CN01", "Hoàn Kiếm",
            "12 Lý Quốc Sư", null, null, null, new BigDecimal("8"), List.of(), List.of(pho)));

        String answer = faq.answer("Thực đơn Hoàn Kiếm có món gì, phở bò giá bao nhiêu?").orElseThrow();

        assertThat(answer).contains("Phở bò", "85.000 ₫", "Hoàn Kiếm");
    }

    @Test
    void answersReservationAndTakeawayHowToQuestionsWithDocumentedSteps() {
        String booking = faq.answer("Tôi đặt bàn thế nào?").orElseThrow();
        String takeaway = faq.answer("Đặt món mang về và thanh toán bằng QR thế nào?").orElseThrow();

        assertThat(booking).contains("Bước 1: Bấm “Đặt bàn”", "Bước 2: Chọn chi nhánh", "Bước 3: Chọn ngày, giờ", "Bước 4: Nhập");
        assertThat(takeaway).contains("Bước 1: Bấm “Mang về”", "Bước 2: Chọn món", "Bước 3: Nhập tên, số điện thoại", "Bước 5: Gửi đơn", "SePay");
    }

    @Test
    void asksForBranchWhenMenuQuestionDoesNotIdentifyOne() {
        when(guestService.getBranches()).thenReturn(List.of(branch()));

        String answer = faq.answer("Cho tôi xem thực đơn").orElseThrow();

        assertThat(answer).contains("Bạn muốn xem thực đơn của chi nhánh nào?");
    }

    @Test
    void leavesUnrecognizedQuestionsForKnowledgeFallback() {
        assertThat(faq.answer("Bạn hãy kể một câu chuyện vui về mùa thu.")).isEmpty();
    }

    @Test
    void answersAvailableReservationSlotsFromLiveData() {
        when(guestService.getBranches()).thenReturn(List.of(branch()));
        when(reservationService.getAvailableSlots(1, java.time.LocalDate.of(2026, 11, 2))).thenReturn(List.of(
            SlotAvailabilityResponse.builder().slotTime(java.time.LocalDateTime.of(2026, 11, 2, 18, 0)).available(true).build(),
            SlotAvailabilityResponse.builder().slotTime(java.time.LocalDateTime.of(2026, 11, 2, 19, 0)).available(false).build()));

        String answer = faq.answer("Ngày 02/11/2026 Hoàn Kiếm còn khung giờ nào đặt bàn?").orElseThrow();

        assertThat(answer).contains("Hoàn Kiếm", "02/11/2026", "18:00").doesNotContain("19:00");
    }

    private static PublicBranchResponse branch() {
        return new PublicBranchResponse(1, "CN01", "Hoàn Kiếm", "hoan-kiem", "12 Lý Quốc Sư", true, 5, true,
            "Hà Nội", 21.0, 105.0, true, "024 7302 8181", "hoankiem@savoris.vn",
            List.of(new PublicBranchResponse.Hours("Thứ Hai", "10:00 – 22:00")));
    }
}
