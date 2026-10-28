package com.savoris.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.savoris.ai.AiModelClient;
import com.savoris.constant.KitchenLoadStatus;
import com.savoris.dto.response.BranchLoadResponse;
import com.savoris.entity.BranchEntity;
import com.savoris.dto.request.KitchenForecastSampleRequest;
import com.savoris.dto.response.KitchenForecastResponse;
import com.savoris.exception.AppException;
import com.savoris.exception.ErrorCode;
import com.savoris.repository.BranchRepository;
import com.savoris.repository.StaffRepository;
import com.savoris.security.BranchAccessGuard;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class KitchenForecastServiceTest {
    @Test
    void uploadedDemoScenarioBuildsForecastWithoutCallingAiOrWritingOperationalData() {
        AiModelClient ai = mock(AiModelClient.class);
        BranchRepository branches = mock(BranchRepository.class);
        StaffRepository staff = mock(StaffRepository.class);
        BranchAccessGuard access = mock(BranchAccessGuard.class);
        KitchenService kitchen = mock(KitchenService.class);
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        BranchEntity branch = new BranchEntity();
        branch.setId(1);
        branch.setName("Cơ sở 1");
        when(branches.findById(1)).thenReturn(Optional.of(branch));
        LocalDateTime firstHour = LocalDateTime.now(ZoneId.of("Asia/Ho_Chi_Minh")).plusHours(1)
            .withMinute(0).withSecond(0).withNano(0);
        List<KitchenForecastSampleRequest.HistoryHour> uploadedHistory = IntStream.range(0, 24)
            .mapToObj(hour -> new KitchenForecastSampleRequest.HistoryHour(hour,
                hour == firstHour.getHour() ? 8.0 : 0.0,
                hour == firstHour.getHour() ? 17.0 : hour == firstHour.plusHours(1).getHour() ? 38.0 : 0.0))
            .toList();
        var request = new KitchenForecastSampleRequest(40, 15L, 6L, 55, 3, uploadedHistory);

        var result = new KitchenForecastService(ai, branches, staff, access, kitchen, jdbc, new ObjectMapper())
            .forecastFromSample(9, 1, request);

        assertThat(result.sampleData()).isTrue();
        assertThat(result.kitchenCapacity()).isEqualTo(40);
        assertThat(result.pendingPortions()).isEqualTo(15);
        assertThat(result.cookingPortions()).isEqualTo(6);
        assertThat(result.currentLoadPercent()).isEqualTo(55);
        assertThat(result.forecast()).extracting(KitchenForecastResponse.ForecastHour::predictedLoad)
            .containsExactly(43, 95);
        assertThat(result.dataNote()).contains("DỮ LIỆU MÔ PHỎNG");
        org.mockito.Mockito.verifyNoInteractions(ai, staff, kitchen, jdbc);
    }

    @Test
    void uploadedDemoScenarioRejectsIncompleteHourlyHistory() {
        AiModelClient ai = mock(AiModelClient.class);
        BranchRepository branches = mock(BranchRepository.class);
        BranchAccessGuard access = mock(BranchAccessGuard.class);
        BranchEntity branch = new BranchEntity();
        branch.setId(1);
        branch.setName("Cơ sở 1");
        when(branches.findById(1)).thenReturn(Optional.of(branch));
        var request = new KitchenForecastSampleRequest(40, 15L, 6L, 55, 3,
            IntStream.range(0, 23).mapToObj(hour -> new KitchenForecastSampleRequest.HistoryHour(hour, 0.0, 0.0)).toList());

        assertThatThrownBy(() -> new KitchenForecastService(ai, branches, null, access, null, null, new ObjectMapper())
            .forecastFromSample(9, 1, request))
            .isInstanceOf(AppException.class)
            .extracting("errorCode").isEqualTo(ErrorCode.INVALID_REQUEST);
    }

    @Test
    void sampleForecastReturnsClearlyLabeledSyntheticDataWithoutCallingAiProvider() {
        AiModelClient ai = mock(AiModelClient.class);
        BranchRepository branches = mock(BranchRepository.class);
        StaffRepository staff = mock(StaffRepository.class);
        BranchAccessGuard access = mock(BranchAccessGuard.class);
        KitchenService kitchen = mock(KitchenService.class);
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        BranchEntity branch = new BranchEntity();
        branch.setId(1);
        branch.setName("Cơ sở 1");
        branch.setKitchenCapacity(40);
        when(ai.isConfigured()).thenReturn(true);
        when(branches.findById(1)).thenReturn(Optional.of(branch));

        var result = new KitchenForecastService(ai, branches, staff, access, kitchen, jdbc, new ObjectMapper())
            .forecast(9, 1, true);

        assertThat(result.sampleData()).isTrue();
        assertThat(result.history()).hasSize(24);
        assertThat(result.history()).anySatisfy(hour -> {
            assertThat(hour.averageOrders()).isEqualTo(22.0);
            assertThat(hour.averagePortions()).isEqualTo(71.0);
        });
        assertThat(result.forecast()).hasSize(2);
        assertThat(result.dataNote()).contains("DỮ LIỆU MÔ PHỎNG", "không phản ánh hoạt động thật");
        assertThat(result.alert()).contains("Dữ liệu mô phỏng");

        BranchEntity secondBranch = new BranchEntity();
        secondBranch.setId(2);
        secondBranch.setName("Cơ sở 2");
        when(branches.findById(2)).thenReturn(Optional.of(secondBranch));
        var secondBranchResult = new KitchenForecastService(ai, branches, staff, access, kitchen, jdbc, new ObjectMapper())
            .forecast(9, 2, true);
        assertThat(secondBranchResult.sampleData()).isTrue();
        assertThat(secondBranchResult.kitchenCapacity()).isEqualTo(36);
        assertThat(secondBranchResult.pendingPortions()).isEqualTo(11);
        assertThat(secondBranchResult.currentLoadPercent()).isEqualTo(42);
        assertThat(secondBranchResult.history()).anySatisfy(hour -> {
            assertThat(hour.averageOrders()).isEqualTo(13.0);
            assertThat(hour.averagePortions()).isEqualTo(31.0);
        });
        assertThat(secondBranchResult.currentLoadPercent()).isNotEqualTo(result.currentLoadPercent());
        org.mockito.Mockito.verifyNoInteractions(ai);
        org.mockito.Mockito.verifyNoInteractions(staff, kitchen, jdbc);
    }

    @Test
    void loadsHistoryForEachForecastWeekdayInHanoiBusinessTime() {
        AiModelClient ai = mock(AiModelClient.class);
        BranchRepository branches = mock(BranchRepository.class);
        StaffRepository staff = mock(StaffRepository.class);
        BranchAccessGuard access = mock(BranchAccessGuard.class);
        KitchenService kitchen = mock(KitchenService.class);
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        BranchEntity branch = new BranchEntity();
        branch.setId(7);
        branch.setName("Hoàn Kiếm");
        branch.setKitchenCapacity(40);
        when(ai.isConfigured()).thenReturn(true);
        when(branches.findById(7)).thenReturn(Optional.of(branch));
        when(staff.findByBranchIdAndStatus(any(), any())).thenReturn(List.of());
        when(kitchen.computeLoad(branch)).thenReturn(new BranchLoadResponse(7, 3, 2, 40, 12, 0, 5,
            KitchenLoadStatus.NORMAL, true));
        List<Map<String, Object>> history = IntStream.range(0, 24)
            .mapToObj(hour -> Map.<String, Object>of("hour", hour, "average_orders", 4.0, "average_portions", 20.0))
            .toList();
        when(jdbc.queryForList(anyString(), any(Object[].class))).thenReturn(history);
        when(ai.complete(anyList(), anyList(), anyBoolean())).thenReturn(new AiModelClient.AiCompletion(
            "{\"forecast\":[{\"hour\":\"23h\",\"predictedLoad\":60,\"risk\":\"NORMAL\"},"
                + "{\"hour\":\"00h\",\"predictedLoad\":70,\"risk\":\"HIGH\"}],"
                + "\"alert\":\"Theo dõi tải bếp\",\"actions\":[\"Theo dõi hàng đợi\"],\"suggestedPauseAt\":null}", List.of()));

        LocalDateTime now = LocalDateTime.now(ZoneId.of("Asia/Ho_Chi_Minh"));
        LocalDateTime firstForecastHour = now.plusHours(1).withMinute(0).withSecond(0).withNano(0);
        Set<LocalDate> targetDays = Set.copyOf(List.of(now.toLocalDate(), firstForecastHour.toLocalDate(), firstForecastHour.plusHours(1).toLocalDate()));
        Map<Integer, LocalDate> daysByWeekday = targetDays.stream().collect(java.util.stream.Collectors.toMap(
            day -> day.getDayOfWeek().getValue() % 7, day -> day));

        new KitchenForecastService(ai, branches, staff, access, kitchen, jdbc, new ObjectMapper()).forecast(9, 7);

        org.mockito.ArgumentCaptor<String> sql = org.mockito.ArgumentCaptor.forClass(String.class);
        org.mockito.ArgumentCaptor<Object[]> parameters = org.mockito.ArgumentCaptor.forClass(Object[].class);
        verify(jdbc, times(targetDays.size())).queryForList(sql.capture(), parameters.capture());
        assertThat(sql.getAllValues()).allSatisfy(query -> assertThat(query).contains("business_opened_at", "INTERVAL '1 minute'"));
        assertThat(parameters.getAllValues()).extracting(values -> (Integer) values[4]).containsExactlyInAnyOrderElementsOf(daysByWeekday.keySet());
        assertThat(parameters.getAllValues()).allSatisfy(values -> {
            int weekday = (Integer) values[4];
            LocalDate businessDate = daysByWeekday.get(weekday);
            ZoneOffset databaseOffset = ZoneId.systemDefault().getRules().getOffset(java.time.Instant.now());
            ZoneOffset businessOffset = ZoneId.of("Asia/Ho_Chi_Minh").getRules().getOffset(java.time.Instant.now());
            int shiftMinutes = (businessOffset.getTotalSeconds() - databaseOffset.getTotalSeconds()) / 60;
            assertThat(values[2]).isEqualTo(businessDate.minusDays(28).atStartOfDay().minusMinutes(shiftMinutes));
            assertThat(values[3]).isEqualTo(businessDate.atStartOfDay().minusMinutes(shiftMinutes));
        });
    }
}
