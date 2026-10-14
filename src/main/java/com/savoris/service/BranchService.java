package com.savoris.service;

import com.savoris.dto.request.CreateBranchRequest;
import com.savoris.dto.request.UpdateBranchRequest;
import com.savoris.dto.response.BranchResponse;

import java.util.List;

public interface BranchService {
    BranchResponse createBranch(CreateBranchRequest request);
    BranchResponse getBranch(Integer branchId);
    BranchResponse updateBranch(Integer staffId, Integer branchId, UpdateBranchRequest request);
    void deleteBranch(Integer branchId);
    List<BranchResponse> getAllBranches();
    List<BranchResponse> searchBranches(String name);
}
