package com.savoris.service.impl;

import com.savoris.dto.request.AreaRequest;
import com.savoris.dto.request.TableRequest;
import com.savoris.dto.response.AreaResponse;
import com.savoris.dto.response.TableResponse;
import com.savoris.entity.AreaEntity;
import com.savoris.entity.DiningTableEntity;
import com.savoris.exception.AppException;
import com.savoris.exception.ErrorCode;
import com.savoris.repository.AreaRepository;
import com.savoris.repository.BranchRepository;
import com.savoris.repository.DiningTableRepository;
import com.savoris.repository.OrderRepository;
import com.savoris.security.BranchAccessGuard;
import com.savoris.service.FloorService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * The floor plan of a branch: areas (Tầng 1, Sân vườn…) and the tables in them, entered by the branch itself.
 */
@Service
@RequiredArgsConstructor
public class FloorServiceImpl implements FloorService {

    private final AreaRepository areaRepository;
    private final DiningTableRepository diningTableRepository;
    private final BranchRepository branchRepository;
    private final OrderRepository orderRepository;
    private final BranchAccessGuard branchAccessGuard;

    @Override
    @Transactional(readOnly = true)
    public List<AreaResponse> getAreas(Integer staffId, Integer branchId) {
        branchAccessGuard.check(staffId, branchId);
        return areaRepository.findByBranchIdOrderByName(branchId).stream().map(AreaResponse::from).toList();
    }

    @Override
    @Transactional
    public AreaResponse createArea(Integer staffId, Integer branchId, AreaRequest request) {
        branchAccessGuard.check(staffId, branchId);
        if (!branchRepository.existsById(branchId)) {
            throw new AppException(ErrorCode.BRANCH_NOT_FOUND);
        }
        AreaEntity area = new AreaEntity();
        area.setBranchId(branchId);
        area.setName(request.name().trim());
        return AreaResponse.from(areaRepository.save(area));
    }

    @Override
    @Transactional
    public AreaResponse renameArea(Integer staffId, Integer areaId, AreaRequest request) {
        AreaEntity area = findArea(staffId, areaId);
        area.setName(request.name().trim());
        return AreaResponse.from(areaRepository.save(area));
    }

    @Override
    @Transactional
    public void deleteArea(Integer staffId, Integer areaId) {
        AreaEntity area = findArea(staffId, areaId);
        if (diningTableRepository.existsByAreaId(areaId)) {
            throw new AppException(ErrorCode.INVALID_STATE, "Remove the tables of " + area.getName() + " first");
        }
        areaRepository.delete(area);
    }

    @Override
    @Transactional(readOnly = true)
    public List<TableResponse> getTables(Integer staffId, Integer branchId) {
        branchAccessGuard.check(staffId, branchId);
        return diningTableRepository.findByBranchId(branchId).stream().map(TableResponse::from).toList();
    }

    @Override
    @Transactional
    public TableResponse createTable(Integer staffId, Integer areaId, TableRequest request) {
        AreaEntity area = findArea(staffId, areaId);
        String name = uniqueName(area.getBranchId(), request.name(), 0);
        DiningTableEntity table = new DiningTableEntity();
        table.setAreaId(areaId);
        table.setName(name);
        table.setCapacity(request.capacity());
        return TableResponse.from(diningTableRepository.save(table));
    }

    @Override
    @Transactional
    public TableResponse updateTable(Integer staffId, Integer tableId, TableRequest request) {
        DiningTableEntity table = findTable(staffId, tableId);
        Integer branchId = areaRepository.findById(table.getAreaId()).orElseThrow().getBranchId();
        table.setName(uniqueName(branchId, request.name(), tableId));
        table.setCapacity(request.capacity());
        return TableResponse.from(diningTableRepository.save(table));
    }

    // A table that ever had an order keeps its history; only unused tables can be removed
    @Override
    @Transactional
    public void deleteTable(Integer staffId, Integer tableId) {
        DiningTableEntity table = findTable(staffId, tableId);
        if (orderRepository.existsByTableId(tableId)) {
            throw new AppException(ErrorCode.INVALID_STATE, "Table " + table.getName() + " already has orders and cannot be removed");
        }
        diningTableRepository.delete(table);
    }

    // Guests and staff call a table by its name ("bàn 05"), so two tables of one branch may not share it.
    // The branch row lock keeps two managers adding the same name at once from both getting through.
    private String uniqueName(Integer branchId, String requested, Integer exceptTableId) {
        String name = requested.trim();
        branchRepository.findByIdForUpdate(branchId);
        if (diningTableRepository.existsNameInBranch(branchId, name, exceptTableId)) {
            throw new AppException(ErrorCode.DUPLICATE_TABLE_NAME, "Branch already has a table named " + name);
        }
        return name;
    }

    private AreaEntity findArea(Integer staffId, Integer areaId) {
        AreaEntity area = areaRepository.findById(areaId)
            .orElseThrow(() -> new AppException(ErrorCode.NOT_FOUND, "Area not found: " + areaId));
        branchAccessGuard.check(staffId, area.getBranchId());
        return area;
    }

    private DiningTableEntity findTable(Integer staffId, Integer tableId) {
        DiningTableEntity table = diningTableRepository.findById(tableId)
            .orElseThrow(() -> new AppException(ErrorCode.NOT_FOUND, "Table not found: " + tableId));
        findArea(staffId, table.getAreaId());
        return table;
    }
}
