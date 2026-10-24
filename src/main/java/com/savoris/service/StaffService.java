package com.savoris.service;

import com.savoris.dto.request.CreateStaffRequest;
import com.savoris.dto.request.UpdateStaffRequest;
import com.savoris.dto.response.StaffDetailResponse;

import java.util.List;

public interface StaffService {
    StaffDetailResponse createStaff(Integer actorId, CreateStaffRequest request);
    StaffDetailResponse getStaff(Integer actorId, Integer staffId);
    StaffDetailResponse updateStaff(Integer actorId, Integer staffId, UpdateStaffRequest request);
    void deleteStaff(Integer actorId, Integer staffId);
    void resetPassword(Integer actorId, Integer staffId, String newPassword);
    List<StaffDetailResponse> getStaffByBranch(Integer actorId, Integer branchId);
    List<StaffDetailResponse> searchStaff(String fullName);
    List<StaffDetailResponse> getAllStaff();
}
