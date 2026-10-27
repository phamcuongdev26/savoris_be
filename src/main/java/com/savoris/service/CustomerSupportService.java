package com.savoris.service;

import com.savoris.constant.StaffRole;
import com.savoris.dto.request.CreateSupportTicketRequest;
import com.savoris.dto.request.UpdateSupportTicketRequest;
import com.savoris.dto.request.CreateSupportMessageRequest;
import com.savoris.dto.response.CreatedSupportTicketResponse;
import com.savoris.dto.response.SupportMessageResponse;
import com.savoris.dto.response.SupportTicketResponse;
import com.savoris.entity.CustomerSupportTicketEntity;
import com.savoris.entity.CustomerSupportMessageEntity;
import com.savoris.exception.AppException;
import com.savoris.exception.ErrorCode;
import com.savoris.repository.BranchRepository;
import com.savoris.repository.CustomerSupportTicketRepository;
import com.savoris.repository.CustomerSupportMessageRepository;
import com.savoris.security.BranchAccessGuard;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;
import java.util.UUID;
import java.util.Base64;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;

@Service
@RequiredArgsConstructor
public class CustomerSupportService {
    private final CustomerSupportTicketRepository tickets;
    private final CustomerSupportMessageRepository messages;
    private final BranchAccessGuard branchGuard;
    private final BranchRepository branches;

    private static final SecureRandom RANDOM = new SecureRandom();

    @Transactional
    public CreatedSupportTicketResponse create(CreateSupportTicketRequest request) {
        if (request.branchId() != null && !branches.existsById(request.branchId())) throw new AppException(ErrorCode.BRANCH_NOT_FOUND);
        byte[] random = new byte[32];
        RANDOM.nextBytes(random);
        String accessToken = Base64.getUrlEncoder().withoutPadding().encodeToString(random);
        CustomerSupportTicketEntity ticket = new CustomerSupportTicketEntity();
        ticket.setReferenceCode("SV-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase());
        ticket.setCustomerName(request.customerName().trim());
        ticket.setCustomerPhone(request.customerPhone().trim());
        ticket.setBranchId(request.branchId());
        ticket.setIssue(request.issue().trim());
        ticket.setConversationContext(request.conversationContext() == null ? null : request.conversationContext().trim());
        ticket.setCustomerAccessTokenHash(tokenHash(accessToken));
        ticket = tickets.save(ticket);
        saveMessage(ticket.getId(), "CUSTOMER", null, ticket.getIssue());
        saveMessage(ticket.getId(), "ASSISTANT", null,
            "Xin chào, tôi là Trợ lí alibibee. Yêu cầu của bạn đã được chuyển cho nhân viên Savoris. Vui lòng chờ phản hồi; bạn có thể nhắn thêm tại đây.");
        return new CreatedSupportTicketResponse(ticket.getReferenceCode(), accessToken, ticket.getStatus());
    }

    @Transactional(readOnly = true)
    public List<SupportTicketResponse> list(Integer staffId) {
        var staff = branchGuard.currentUser(staffId);
        List<CustomerSupportTicketEntity> found = staff.getRole() == StaffRole.ADMIN
            ? tickets.findAllByOrderByUpdatedAtDesc()
            : staff.getBranchId() == null ? List.of()
            : tickets.findByBranchIdOrderByUpdatedAtDesc(staff.getBranchId());
        return found.stream().map(SupportTicketResponse::from).toList();
    }

    @Transactional
    public SupportTicketResponse update(Integer staffId, long ticketId, UpdateSupportTicketRequest request) {
        CustomerSupportTicketEntity ticket = tickets.findById(ticketId).orElseThrow(() -> new AppException(ErrorCode.NOT_FOUND));
        if (ticket.getBranchId() != null) branchGuard.check(staffId, ticket.getBranchId());
        else if (branchGuard.currentUser(staffId).getRole() != StaffRole.ADMIN) throw new AppException(ErrorCode.FORBIDDEN);
        ticket.setStatus(request.status());
        ticket.setHandledBy(staffId);
        return SupportTicketResponse.from(tickets.save(ticket));
    }

    @Transactional(readOnly = true)
    public List<SupportMessageResponse> customerMessages(String referenceCode, String accessToken) {
        CustomerSupportTicketEntity ticket = customerTicket(referenceCode, accessToken);
        return messages.findByTicketIdOrderByCreatedAtAscIdAsc(ticket.getId()).stream().map(SupportMessageResponse::from).toList();
    }

    @Transactional
    public List<SupportMessageResponse> customerMessage(String referenceCode, String accessToken, CreateSupportMessageRequest request) {
        CustomerSupportTicketEntity ticket = customerTicket(referenceCode, accessToken);
        saveMessage(ticket.getId(), "CUSTOMER", null, request.body().trim());
        ticket.setStatus("IN_PROGRESS");
        tickets.save(ticket);
        return customerMessages(referenceCode, accessToken);
    }

    @Transactional(readOnly = true)
    public List<SupportMessageResponse> staffMessages(Integer staffId, long ticketId) {
        CustomerSupportTicketEntity ticket = staffTicket(staffId, ticketId);
        return messages.findByTicketIdOrderByCreatedAtAscIdAsc(ticket.getId()).stream().map(SupportMessageResponse::from).toList();
    }

    @Transactional
    public List<SupportMessageResponse> staffMessage(Integer staffId, long ticketId, CreateSupportMessageRequest request) {
        CustomerSupportTicketEntity ticket = staffTicket(staffId, ticketId);
        saveMessage(ticket.getId(), "STAFF", staffId, request.body().trim());
        if (!"IN_PROGRESS".equals(ticket.getStatus())) ticket.setStatus("IN_PROGRESS");
        ticket.setHandledBy(staffId);
        tickets.save(ticket);
        return messages.findByTicketIdOrderByCreatedAtAscIdAsc(ticket.getId()).stream().map(SupportMessageResponse::from).toList();
    }

    private CustomerSupportTicketEntity customerTicket(String referenceCode, String accessToken) {
        CustomerSupportTicketEntity ticket = tickets.findByReferenceCode(referenceCode)
            .orElseThrow(() -> new AppException(ErrorCode.NOT_FOUND));
        byte[] expected = tokenHash(accessToken).getBytes(StandardCharsets.US_ASCII);
        byte[] actual = ticket.getCustomerAccessTokenHash().getBytes(StandardCharsets.US_ASCII);
        if (!MessageDigest.isEqual(expected, actual)) throw new AppException(ErrorCode.FORBIDDEN);
        return ticket;
    }

    private CustomerSupportTicketEntity staffTicket(Integer staffId, long ticketId) {
        CustomerSupportTicketEntity ticket = tickets.findById(ticketId).orElseThrow(() -> new AppException(ErrorCode.NOT_FOUND));
        if (ticket.getBranchId() != null) branchGuard.check(staffId, ticket.getBranchId());
        else if (branchGuard.currentUser(staffId).getRole() != StaffRole.ADMIN) throw new AppException(ErrorCode.FORBIDDEN);
        return ticket;
    }

    private void saveMessage(Long ticketId, String senderType, Integer staffId, String body) {
        CustomerSupportMessageEntity message = new CustomerSupportMessageEntity();
        message.setTicketId(ticketId);
        message.setSenderType(senderType);
        message.setSenderStaffId(staffId);
        message.setBody(body);
        messages.save(message);
    }

    private static String tokenHash(String token) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (java.security.NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is not available", impossible);
        }
    }
}
