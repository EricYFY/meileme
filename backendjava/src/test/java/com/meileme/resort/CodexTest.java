package com.meileme.resort;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.meileme.resort.config.MotherBuildingType;
import com.meileme.resort.config.PositionType;
import com.meileme.resort.model.CodexState;
import com.meileme.resort.model.GameState;
import com.meileme.resort.service.ConfigRegistry;
import com.meileme.resort.service.ConstructionService;
import com.meileme.resort.service.IdGenerator;
import com.meileme.resort.service.RegionService;
import com.meileme.resort.service.SkillTreeService;
import com.meileme.resort.service.StaffService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class CodexTest {

    private ConfigRegistry registry;
    private RegionService regionService;
    private ConstructionService constructionService;
    private StaffService staffService;
    private IdGenerator idGenerator;

    @BeforeEach
    public void setup() {
        registry = new ConfigRegistry(new ObjectMapper());
        registry.init();
        idGenerator = new IdGenerator();
        SkillTreeService skillTree = new SkillTreeService(registry, null);
        regionService = new RegionService(registry);
        constructionService = new ConstructionService(registry, skillTree, idGenerator);
        staffService = new StaffService(registry, skillTree, idGenerator);
    }

    @Test
    public void testCodexBasicUnlockAndDedup() {
        CodexState codex = new CodexState();

        // 1. 建筑形态
        assertTrue(codex.unlockForm("FORM_TEST_A"));
        assertFalse(codex.unlockForm("FORM_TEST_A"), "重复解锁应返回 false");
        assertEquals(1, codex.getUnlockedForms().size());

        // 2. 内装设施
        assertTrue(codex.unlockInterior("BED_DELUXE"));
        assertFalse(codex.unlockInterior("BED_DELUXE"));
        assertEquals(1, codex.getUnlockedInteriors().size());

        // 3. 地块
        assertTrue(codex.unlockPlot("BEACH_PLOT_1"));
        assertFalse(codex.unlockPlot("BEACH_PLOT_1"));
        assertEquals(1, codex.getUnlockedPlots().size());

        // 4. 员工岗位
        assertTrue(codex.unlockPosition("ROOM_CLEANER"));
        assertFalse(codex.unlockPosition("ROOM_CLEANER"));
        assertEquals(1, codex.getUnlockedPositions().size());
    }

    @Test
    public void testRegionServiceUnlocksPlotToCodex() {
        GameState state = new GameState();
        state.setFunds(100000);
        state.setRunning(true);

        // 开局初始化，默认解锁地块应自动录入图鉴
        regionService.initialize(state);
        assertNotNull(state.getCodex());
        assertFalse(state.getCodex().getUnlockedPlots().isEmpty(), "开局默认地块应计入图鉴");
        assertTrue(state.getCodex().getUnlockedPlots().contains("STORMVEIL_CASTLE"));
    }

    @Test
    public void testConstructionServiceUnlocksFormToCodex() {
        GameState state = new GameState();
        state.setFunds(100000);
        state.setRunning(true);
        state.getUnlockedPlots().add("STORMVEIL_CASTLE");

        MotherBuildingType mother = MotherBuildingType.STANDARD_ROOM;
        assertNotNull(mother);

        var building = constructionService.completeBuild(state, mother, "STORMVEIL_CASTLE", 0, 0);
        assertNotNull(building);
        assertNotNull(building.getForm());

        assertTrue(state.getCodex().getUnlockedForms().contains(building.getForm().getId()),
                "新建建筑完成后应解锁图鉴：" + building.getForm().getId());
    }

    @Test
    public void testStaffServiceUnlocksPositionToCodex() {
        GameState state = new GameState();
        state.setFunds(100000);
        state.setRunning(true);

        staffService.hire(state, PositionType.ROOM_CLEANER);
        assertTrue(state.getCodex().getUnlockedPositions().contains(PositionType.ROOM_CLEANER.name()),
                "首次招聘保洁员应解锁对应图鉴");

        staffService.hire(state, PositionType.CHEF);
        assertTrue(state.getCodex().getUnlockedPositions().contains(PositionType.CHEF.name()),
                "首次招聘主厨应解锁对应图鉴");
    }
}
