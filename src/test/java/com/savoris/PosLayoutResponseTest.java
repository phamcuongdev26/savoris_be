package com.savoris;

import com.savoris.dto.response.AreaResponse;
import com.savoris.dto.response.TableResponse;
import com.savoris.entity.AreaEntity;
import com.savoris.entity.DiningTableEntity;
import com.savoris.constant.TableStatus;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PosLayoutResponseTest {
    @Test
    void areaResponseIncludesItsFloorForDynamicFloorTabs() {
        AreaEntity area = AreaEntity.builder().id(8).branchId(2).name("Khu cửa sổ")
            .floorName("Tầng 2").floorSortOrder(2).build();

        AreaResponse response = AreaResponse.from(area);

        assertThat(response.floorName()).isEqualTo("Tầng 2");
        assertThat(response.floorSortOrder()).isEqualTo(2);
    }

    @Test
    void tableResponseIncludesPersistentGridCoordinates() {
        DiningTableEntity table = DiningTableEntity.builder().id(9).areaId(8).name("HK-201")
            .capacity(4).status(TableStatus.AVAILABLE).positionX(2).positionY(1).sortOrder(6)
            .qrToken("demo-token").build();

        TableResponse response = TableResponse.from(table);

        assertThat(response.getPositionX()).isEqualTo(2);
        assertThat(response.getPositionY()).isEqualTo(1);
        assertThat(response.getSortOrder()).isEqualTo(6);
    }
}
