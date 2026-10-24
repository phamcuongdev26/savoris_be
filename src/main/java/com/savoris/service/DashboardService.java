package com.savoris.service;

import com.savoris.dto.response.BranchDashboardResponse;
import com.savoris.dto.response.ChainDashboardResponse;

public interface DashboardService {
    ChainDashboardResponse getChainDashboard();
    BranchDashboardResponse getBranchDashboard(Integer staffId, Integer branchId);
}
