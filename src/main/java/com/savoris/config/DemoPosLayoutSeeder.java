package com.savoris.config;

import com.savoris.entity.AreaEntity;
import com.savoris.entity.BranchEntity;
import com.savoris.entity.DiningTableEntity;
import com.savoris.repository.AreaRepository;
import com.savoris.repository.BranchRepository;
import com.savoris.repository.DiningTableRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;
import java.util.HashMap;
import java.util.Map;

/** Opt-in, additive POS fixtures. Never active unless the explicit demo profile is enabled. */
@Configuration
@Profile("demo")
@RequiredArgsConstructor
public class DemoPosLayoutSeeder {
    private final BranchRepository branchRepository;
    private final AreaRepository areaRepository;
    private final DiningTableRepository tableRepository;
    private final TransactionTemplate transactionTemplate;

    private static final Map<String, List<FloorPlan>> PLANS = Map.of(
        "CN01", List.of(
            new FloorPlan("Tầng 1", 1, "HK", 16, List.of("Sảnh chính", "Khu cửa sổ")),
            new FloorPlan("Tầng 2", 2, "HK", 12, List.of("Khu VIP")),
            new FloorPlan("Tầng 3", 3, "HK", 8, List.of("Sảnh chính tầng 3")),
            new FloorPlan("Tầng thượng", 4, "HK", 6, List.of("Khu sân thượng"))
        ),
        "CN02", List.of(
            new FloorPlan("Tầng 1", 1, "CG", 12, List.of("Khu gia đình", "Phòng riêng")),
            new FloorPlan("Tầng 2", 2, "CG", 14, List.of("Sân vườn")),
            new FloorPlan("Tầng 3", 3, "CG", 10, List.of("Khu VIP"))
        ),
        "CN03", List.of(
            new FloorPlan("Tầng 1", 1, "HG", 14, List.of("Trong nhà", "Ven hồ")),
            new FloorPlan("Tầng 2", 2, "HG", 10, List.of("Ban công")),
            new FloorPlan("Sân vườn", 3, "HG", 12, List.of("Sân vườn"))
        ),
        "CN04", List.of(
            new FloorPlan("Tầng 1", 1, "HM", 12, List.of("Sảnh chính", "Khu gia đình")),
            new FloorPlan("Tầng 2", 2, "HM", 10, List.of("Khu phòng riêng"))
        )
    );

    @Bean
    @Order(Ordered.HIGHEST_PRECEDENCE)
    ApplicationRunner seedDemoPosLayout() {
        return args -> transactionTemplate.executeWithoutResult(status -> seedData());
    }

    private void seedData() {
        PLANS.forEach((branchCode, floors) -> branchRepository.findByCode(branchCode)
            .ifPresent(branch -> floors.forEach(floor -> seedFloor(branch, floor))));
    }

    private void seedFloor(BranchEntity branch, FloorPlan floor) {
        List<AreaEntity> existing = areaRepository.findByBranchIdOrderByName(branch.getId()).stream()
            .filter(area -> floor.name().equals(area.getFloorName())).toList();
        for (String areaName : floor.areaNames()) {
            String stableAreaName = resolvedAreaName(branch.getId(), floor, areaName);
            AreaEntity area = areaRepository.findByBranchIdAndName(branch.getId(), stableAreaName).orElseGet(() -> {
                AreaEntity created = new AreaEntity();
                created.setBranchId(branch.getId());
                created.setName(stableAreaName);
                created.setFloorName(floor.name());
                created.setFloorSortOrder(floor.sortOrder());
                return areaRepository.save(created);
            });
            if (!floor.name().equals(area.getFloorName())) throw new IllegalStateException("Fixture area belongs to another floor: " + stableAreaName);
            if (!Integer.valueOf(floor.sortOrder()).equals(area.getFloorSortOrder())) {
                area.setFloorSortOrder(floor.sortOrder());
                areaRepository.save(area);
            }
        }

        List<AreaEntity> floorAreas = areaRepository.findByBranchIdOrderByName(branch.getId()).stream()
            .filter(area -> floor.name().equals(area.getFloorName())).toList();
        if (floorAreas.isEmpty()) floorAreas = existing;
        List<AreaEntity> fixtureAreas = floor.areaNames().stream()
            .map(name -> areaRepository.findByBranchIdAndName(branch.getId(), resolvedAreaName(branch.getId(), floor, name)).orElseThrow())
            .toList();
        redistributeFixtureTables(floorAreas, fixtureAreas, floor);
        int current = floorAreas.stream().mapToInt(area -> tableRepository.findByAreaIdOrderBySortOrderAscNameAsc(area.getId()).size()).sum();
        if (current >= floor.tableCount()) return;

        int nextCode = 1;
        while (current < floor.tableCount()) {
            String name = tableName(floor, nextCode++);
            if (tableRepository.existsNameInBranch(branch.getId(), name, 0)) continue;
            AreaEntity tableArea = fixtureAreas.stream().min((left, right) -> Integer.compare(
                tableRepository.findByAreaIdOrderBySortOrderAscNameAsc(left.getId()).size(),
                tableRepository.findByAreaIdOrderBySortOrderAscNameAsc(right.getId()).size())).orElseThrow();
            List<DiningTableEntity> tables = tableRepository.findByAreaIdOrderBySortOrderAscNameAsc(tableArea.getId());
            int sort = tables.stream().map(DiningTableEntity::getSortOrder).filter(value -> value != null).mapToInt(Integer::intValue).max().orElse(-1) + 1;
            DiningTableEntity table = new DiningTableEntity();
            table.setAreaId(tableArea.getId());
            table.setName(name);
            table.setCapacity(capacityFor(sort));
            table.setPositionX(sort % 4);
            table.setPositionY(sort / 4);
            table.setSortOrder(sort);
            tableRepository.save(table);
            current++;
        }
    }

    private String resolvedAreaName(Integer branchId, FloorPlan floor, String areaName) {
        AreaEntity namedArea = areaRepository.findByBranchIdAndName(branchId, areaName).orElse(null);
        return namedArea != null && !floor.name().equals(namedArea.getFloorName())
            ? "%s · %s".formatted(areaName, floor.name()) : areaName;
    }

    private void redistributeFixtureTables(List<AreaEntity> floorAreas, List<AreaEntity> fixtureAreas, FloorPlan floor) {
        List<DiningTableEntity> fixtureTables = floorAreas.stream()
            .flatMap(area -> tableRepository.findByAreaIdOrderBySortOrderAscNameAsc(area.getId()).stream())
            .filter(table -> table.getName().startsWith(floor.prefix() + "-"))
            .sorted((left, right) -> left.getName().compareTo(right.getName())).toList();
        Map<Integer, Integer> nextSortByArea = new HashMap<>();
        for (AreaEntity area : fixtureAreas) {
            int maxSort = tableRepository.findByAreaIdOrderBySortOrderAscNameAsc(area.getId()).stream()
                .filter(table -> !table.getName().startsWith(floor.prefix() + "-"))
                .map(DiningTableEntity::getSortOrder).filter(value -> value != null).mapToInt(Integer::intValue).max().orElse(-1);
            nextSortByArea.put(area.getId(), maxSort + 1);
        }
        for (int index = 0; index < fixtureTables.size(); index++) {
            DiningTableEntity table = fixtureTables.get(index);
            AreaEntity area = fixtureAreas.get(index % fixtureAreas.size());
            int sort = nextSortByArea.get(area.getId());
            nextSortByArea.put(area.getId(), sort + 1);
            table.setAreaId(area.getId());
            table.setSortOrder(sort);
            table.setPositionX(sort % 4);
            table.setPositionY(sort / 4);
            tableRepository.save(table);
        }
    }

    private static String tableName(FloorPlan floor, int number) {
        if ("HK".equals(floor.prefix()) && floor.name().equals("Tầng thượng")) return "HK-R%02d".formatted(number);
        if ("HG".equals(floor.prefix()) && floor.name().equals("Sân vườn")) return "HG-G%02d".formatted(number);
        return "%s-%d%02d".formatted(floor.prefix(), floor.sortOrder(), number);
    }

    private static int capacityFor(int sort) {
        return switch (sort % 4) { case 0 -> 2; case 1 -> 4; case 2 -> 6; default -> 8; };
    }

    private record FloorPlan(String name, int sortOrder, String prefix, int tableCount, List<String> areaNames) { }
}
