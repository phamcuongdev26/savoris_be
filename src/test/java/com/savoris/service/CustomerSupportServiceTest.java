package com.savoris.service;

import com.savoris.constant.StaffRole;
import com.savoris.dto.request.CreateSupportTicketRequest;
import com.savoris.dto.request.UpdateSupportTicketRequest;
import com.savoris.entity.CustomerSupportTicketEntity;
import com.savoris.entity.StaffEntity;
import com.savoris.exception.AppException;
import com.savoris.repository.BranchRepository;
import com.savoris.repository.CustomerSupportTicketRepository;
import com.savoris.repository.CustomerSupportMessageRepository;
import com.savoris.security.BranchAccessGuard;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CustomerSupportServiceTest {
    @Mock CustomerSupportTicketRepository tickets;
    @Mock CustomerSupportMessageRepository messages;
    @Mock BranchAccessGuard branchGuard;
    @Mock BranchRepository branches;
    @InjectMocks CustomerSupportService service;

    @Test
    void createsTicketWithGeneratedReferenceAndHandoffContext() {
        when(branches.existsById(2)).thenReturn(true);
        when(tickets.save(any())).thenAnswer(call -> call.getArgument(0));
        var result = service.create(new CreateSupportTicketRequest(" An ", "0912345678", 2,
            " Món này có dị ứng không? ", "Trả lời của trợ lý: chưa rõ"));
        assertThat(result.referenceCode()).startsWith("SV-");
        assertThat(result.accessToken()).isNotBlank();
        var ticket = org.mockito.ArgumentCaptor.forClass(CustomerSupportTicketEntity.class);
        verify(tickets).save(ticket.capture());
        assertThat(ticket.getValue().getCustomerName()).isEqualTo("An");
        assertThat(ticket.getValue().getIssue()).isEqualTo("Món này có dị ứng không?");
        assertThat(ticket.getValue().getConversationContext()).contains("chưa rõ");
        assertThat(ticket.getValue().getCustomerAccessTokenHash()).hasSize(64).isNotEqualTo(result.accessToken());
        verify(messages).save(argThat(message -> message.getBody().equals("Món này có dị ứng không?")
            && message.getSenderType().equals("CUSTOMER")));
    }

    @Test
    void listsOnlyTheAssignedBranchForNonAdminStaff() {
        when(branchGuard.currentUser(7)).thenReturn(StaffEntity.builder().id(7).role(StaffRole.STAFF).branchId(2).build());
        when(tickets.findByBranchIdOrderByUpdatedAtDesc(2)).thenReturn(List.of());
        assertThat(service.list(7)).isEmpty();
        verify(tickets).findByBranchIdOrderByUpdatedAtDesc(2);
        verify(tickets, never()).findAllByOrderByUpdatedAtDesc();
    }

    @Test
    void checksBranchAuthorizationBeforeUpdatingTicket() {
        CustomerSupportTicketEntity ticket = new CustomerSupportTicketEntity();
        ticket.setId(41L); ticket.setBranchId(3); ticket.setStatus("OPEN");
        when(tickets.findById(41L)).thenReturn(Optional.of(ticket));
        doThrow(new AppException(com.savoris.exception.ErrorCode.FORBIDDEN)).when(branchGuard).check(9, 3);
        assertThatThrownBy(() -> service.update(9, 41L, new UpdateSupportTicketRequest("RESOLVED")))
            .isInstanceOf(AppException.class);
        verify(tickets, never()).save(any());
    }
}
