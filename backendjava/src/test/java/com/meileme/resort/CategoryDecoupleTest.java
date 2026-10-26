package com.meileme.resort;

import com.meileme.resort.config.BuildingCategory;
import com.meileme.resort.config.BuildingForm;
import com.meileme.resort.config.EvolutionLine;
import com.meileme.resort.config.InteriorType;
import com.meileme.resort.config.MotherBuildingType;
import com.meileme.resort.model.BuildingInstance;
import com.meileme.resort.model.GameState;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class CategoryDecoupleTest {

    @Test
    public void testFormCategoryIndependentFromMother() {
        // 1. 无母体的纯独立住宿形态（如跨住宿/配套合并出来的综合套房）
        BuildingForm hybridAccomForm = BuildingForm.builder("HYBRID_SUITE", "温泉套房")
                .category(BuildingCategory.ACCOMMODATION)
                .line(EvolutionLine.MERGE)
                .tier(3)
                .capacityPerRoom(4)
                .pricePerRoom(600, 800, 1000)
                .build();

        assertEquals(BuildingCategory.ACCOMMODATION, hybridAccomForm.getCategory());
        assertTrue(hybridAccomForm.isAccommodation());
        assertFalse(hybridAccomForm.isSupport());

        // 2. 无母体的配套形态（如跨住宿/配套合并出来的休闲吧）
        BuildingForm hybridSupportForm = BuildingForm.builder("HYBRID_LOUNGE", "观景茶廊")
                .category(BuildingCategory.SUPPORT)
                .line(EvolutionLine.MERGE)
                .tier(2)
                .serviceCapacity(30)
                .unitPrice(50, 80)
                .build();

        assertEquals(BuildingCategory.SUPPORT, hybridSupportForm.getCategory());
        assertFalse(hybridSupportForm.isAccommodation());
        assertTrue(hybridSupportForm.isSupport());
    }

    @Test
    public void testBuildingInstanceAndGameStateClassificationWithoutMother() {
        GameState state = new GameState();

        // 建筑A：无母体，形态为住宿类
        BuildingForm formA = BuildingForm.builder("CUSTOM_ACCOM", "私汤客舍")
                .category(BuildingCategory.ACCOMMODATION)
                .build();
        BuildingInstance b1 = new BuildingInstance();
        b1.setId("BLD_1");
        b1.setMother(null);
        b1.setForm(formA);
        b1.setAlive(true);

        // 建筑B：无母体，形态为配套类
        BuildingForm formB = BuildingForm.builder("CUSTOM_SUPPORT", "私汤泡池")
                .category(BuildingCategory.SUPPORT)
                .build();
        BuildingInstance b2 = new BuildingInstance();
        b2.setId("BLD_2");
        b2.setMother(null);
        b2.setForm(formB);
        b2.setAlive(true);

        state.getBuildings().add(b1);
        state.getBuildings().add(b2);

        // 验证 BuildingInstance 的分类方法
        assertTrue(b1.isAccommodation());
        assertFalse(b1.isSupport());

        assertFalse(b2.isAccommodation());
        assertTrue(b2.isSupport());

        // 验证 GameState 过滤（确保无 NPE，且分别归入住宿与配套列表）
        List<BuildingInstance> accoms = state.aliveAccommodationBuildings();
        List<BuildingInstance> supports = state.aliveSupportBuildings();

        assertEquals(1, accoms.size());
        assertEquals("BLD_1", accoms.get(0).getId());

        assertEquals(1, supports.size());
        assertEquals("BLD_2", supports.get(0).getId());
    }

    @Test
    public void testInteriorApplicabilityWithNullMother() {
        BuildingForm accomForm = BuildingForm.builder("CUSTOM_ACCOM", "自定义客舍")
                .category(BuildingCategory.ACCOMMODATION)
                .build();

        // 住宿类通用内装（如床位）在母体为 null 时，依然能够判定适用
        assertTrue(InteriorType.BED.isApplicableTo(accomForm, null));

        // 专属配套内装（如厨房设备），不适用住宿类建筑
        assertFalse(InteriorType.KITCHEN.isApplicableTo(accomForm, null));
    }

    @Test
    public void testCrossCategoryMerge() {
        GameState state = new GameState();

        // 建筑A: 住宿类
        BuildingForm formA = BuildingForm.builder("ROOM", "客房", BuildingCategory.ACCOMMODATION).build();
        BuildingInstance b1 = new BuildingInstance();
        b1.setId("ROOM_1");
        b1.setMother(MotherBuildingType.STANDARD_ROOM);
        b1.setForm(formA);
        b1.setPlotId("PLOT_1");
        b1.setCellX(1);
        b1.setCellY(1);

        // 建筑B: 配套类（如温泉）
        BuildingForm formB = BuildingForm.builder("SPRING", "温泉", BuildingCategory.SUPPORT).build();
        BuildingInstance b2 = new BuildingInstance();
        b2.setId("SPRING_1");
        b2.setMother(MotherBuildingType.HOT_SPRING_POOL);
        b2.setForm(formB);
        b2.setPlotId("PLOT_1");
        b2.setCellX(1);
        b2.setCellY(2);

        state.getBuildings().add(b1);
        state.getBuildings().add(b2);

        // 合并产物：温泉木屋（大类自主为住宿类，mother 为 null 或独立）
        BuildingForm mergeResultForm = BuildingForm.builder("HOT_SPRING_CABIN", "温泉木屋")
                .category(BuildingCategory.ACCOMMODATION)
                .line(EvolutionLine.MERGE)
                .tier(3)
                .capacityPerRoom(4)
                .pricePerRoom(1200, 1500)
                .build();

        com.meileme.resort.config.MergeRecipe recipe = new com.meileme.resort.config.MergeRecipe(
                "RECIPE_HYBRID", "客房+温泉->温泉木屋",
                formA, 3, formB, 3, mergeResultForm, 1, 50000, 3
        );

        com.meileme.resort.service.IdGenerator idGen = new com.meileme.resort.service.IdGenerator();
        com.meileme.resort.service.ConfigRegistry reg = new com.meileme.resort.service.ConfigRegistry(new com.fasterxml.jackson.databind.ObjectMapper());
        com.meileme.resort.service.SkillTreeService skillTree = new com.meileme.resort.service.SkillTreeService(reg, null);
        com.meileme.resort.service.EvolutionService evolutionService = new com.meileme.resort.service.EvolutionService(reg, skillTree, idGen);

        BuildingInstance result = evolutionService.completeMerge(state, b1, b2, recipe);

        assertNotNull(result);
        assertFalse(b1.isAlive());
        assertFalse(b2.isAlive());
        assertTrue(result.isAlive());

        // 关键断言：合并产物直接继承形态自身的大类判定，母体为 null 不影响判定
        assertNull(result.getMother());
        assertEquals(BuildingCategory.ACCOMMODATION, result.getForm().getCategory());
        assertTrue(result.isAccommodation());
        assertFalse(result.isSupport());

        // 验证 GameState 中存活住宿列表仅有合并后的新建筑，配套列表为0
        assertEquals(1, state.aliveAccommodationBuildings().size());
        assertEquals(result.getId(), state.aliveAccommodationBuildings().get(0).getId());
        assertEquals(0, state.aliveSupportBuildings().size());
    }

    @Test
    public void testBaseFormMappingFromConfig() {
        com.meileme.resort.service.ConfigRegistry reg = new com.meileme.resort.service.ConfigRegistry(new com.fasterxml.jackson.databind.ObjectMapper());
        reg.loadConfig();

        // 验证各母体在形态完全无 mother 字段时，通过母体-形态正向映射正确拿到 Tier 0 基础形态
        BuildingForm stdRoomForm = reg.baseFormOf(MotherBuildingType.STANDARD_ROOM);
        assertNotNull(stdRoomForm);
        assertEquals("STANDARD_ROOM", stdRoomForm.getId());
        assertEquals(BuildingCategory.ACCOMMODATION, stdRoomForm.getCategory());
        assertEquals(0, stdRoomForm.getTier());

        BuildingForm restForm = reg.baseFormOf(MotherBuildingType.RESTAURANT);
        assertNotNull(restForm);
        assertEquals("RESTAURANT", restForm.getId());
        assertEquals(BuildingCategory.SUPPORT, restForm.getCategory());
        assertEquals(0, restForm.getTier());

        BuildingForm springForm = reg.baseFormOf(MotherBuildingType.HOT_SPRING_POOL);
        assertNotNull(springForm);
        assertEquals("HOT_SPRING_POOL", springForm.getId());
        assertEquals(BuildingCategory.SUPPORT, springForm.getCategory());
        assertEquals(0, springForm.getTier());

        BuildingForm viewForm = reg.baseFormOf(MotherBuildingType.VIEWING_PLATFORM);
        assertNotNull(viewForm);
        assertEquals("VIEWING_PLATFORM", viewForm.getId());
        assertEquals(BuildingCategory.SUPPORT, viewForm.getCategory());
        assertEquals(0, viewForm.getTier());
    }

    @Test
    public void testAchievementRewardAndSkillCostPoints() {
        GameState state = new GameState();
        state.setRunning(true);
        assertEquals(0, state.getSkillPoints());

        // 1. 模拟达成一个奖励 2 点技能点的成就
        com.meileme.resort.config.Achievement ach = new com.meileme.resort.config.Achievement(
                "ACH_TEST", "测试大成就",
                com.meileme.resort.config.AchievementCategory.BUILDING,
                com.meileme.resort.config.AchievementType.BUILDING_COUNT,
                1.0, "", 2
        );
        assertEquals(2, ach.getRewardSkillPoints());

        // 模拟达成条件并触发发放
        state.getBuildings().add(new BuildingInstance());
        state.getUnlockedAchievements().add(ach.getId());
        state.setSkillPoints(state.getSkillPoints() + ach.getRewardSkillPoints());
        assertEquals(2, state.getSkillPoints());

        // 2. 模拟配置需要消耗 2 点技能点的技能
        com.meileme.resort.config.SkillNode node = new com.meileme.resort.config.SkillNode(
                "SK_ADV", "进阶建筑术", "建筑成本减免",
                com.meileme.resort.config.SkillCategory.BUILDING,
                java.util.List.of(),
                com.meileme.resort.config.SkillEffectType.COST_DISCOUNT,
                com.meileme.resort.config.SkillEffectKey.BUILD_COST_DISCOUNT,
                0.15,
                2
        );
        assertEquals(2, node.getCostPoints());

        // 3. 构建 SkillTreeService 验证
        com.meileme.resort.service.ConfigRegistry reg = new com.meileme.resort.service.ConfigRegistry(new com.fasterxml.jackson.databind.ObjectMapper()) {
            @Override
            public com.meileme.resort.config.SkillNode skill(String id) {
                return node.getId().equals(id) ? node : null;
            }
        };
        com.meileme.resort.service.SkillTreeService skillService = new com.meileme.resort.service.SkillTreeService(reg, null);

        // 如果技能点只有 1 点，不足以支付该技能所需 2 点，验证拦截
        state.setSkillPoints(1);
        assertEquals("NO_SKILL_POINT", skillService.validateUnlock(state, "SK_ADV"));

        // 恢复为 2 点技能点，验证通过并准确扣减消耗点数
        state.setSkillPoints(2);
        assertNull(skillService.validateUnlock(state, "SK_ADV"));
        skillService.unlock(state, "SK_ADV");
        assertEquals(0, state.getSkillPoints());
        assertTrue(state.getUnlockedSkills().contains("SK_ADV"));
    }

    @Test
    public void testSaveAdminConfigFileGeneration() throws Exception {
        com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
        com.meileme.resort.service.ConfigRegistry reg = new com.meileme.resort.service.ConfigRegistry(mapper);
        reg.init();
        java.io.File file = reg.resolveAdminConfigFile();
        byte[] backup = null;
        if (file.exists()) {
            backup = java.nio.file.Files.readAllBytes(file.toPath());
        }
        try {
            java.util.Map<String, Object> raw = reg.getRawConfigMap();
            assertNotNull(raw);
            assertFalse(raw.isEmpty());

            reg.saveAdminConfig(raw);
            assertTrue(file.exists(), "admin-config.json 必须成功生成");
            assertTrue(reg.isCustomConfig());
        } finally {
            if (backup != null) {
                java.nio.file.Files.write(file.toPath(), backup);
            } else if (file.exists()) {
                file.delete();
            }
        }
    }

    @Test
    public void testStaffAndConfigRegistryDynamicValues() {
        com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
        com.meileme.resort.service.ConfigRegistry reg = new com.meileme.resort.service.ConfigRegistry(mapper);
        reg.init();

        // 验证 positions 已经成功被解析进 ConfigRegistry
        assertFalse(reg.allPositions().isEmpty(), "positions 配置列表不能为空");
        com.meileme.resort.config.PositionConfig cleanerCfg = reg.getPosition("ROOM_CLEANER");
        assertNotNull(cleanerCfg, "必须包含 ROOM_CLEANER 岗位配置");

        // 验证当前配置中，保洁的 1 级到 2 级升级天数
        int days = cleanerCfg.workDaysToNextLevel(1);
        System.out.println("ROOM_CLEANER upgradeDays: " + days);

        com.meileme.resort.service.SkillTreeService skillService = new com.meileme.resort.service.SkillTreeService(reg, null);
        com.meileme.resort.service.StaffService staffService = new com.meileme.resort.service.StaffService(reg, skillService, new com.meileme.resort.service.IdGenerator());

        GameState state = new GameState();
        com.meileme.resort.model.StaffInstance staff = new com.meileme.resort.model.StaffInstance();
        staff.setId("ST_TEST_1");
        staff.setPosition(com.meileme.resort.config.PositionType.ROOM_CLEANER);
        staff.setLevel(1);
        staff.setDaysAtCurrentLevel(0);

        int reqDays = staffService.trainingRequiredDays(state, staff);
        assertEquals(days, reqDays, "StaffService 必须读取动态配置的天数");

        // 验证动态修改配置时（例如动态改为 15 天），StaffService 立即生效
        com.meileme.resort.config.PositionConfig updatedCfg = cleanerCfg.toBuilder()
                .upgradeDaysByLevel(new int[]{15, 20})
                .build();
        reg.registerPosition(updatedCfg);
        assertEquals(15, staffService.trainingRequiredDays(state, staff), "修改为 15 天后，StaffService 必须实时返回 15 天");

        // 验证 InteriorConfig 和 RegionConfig 也被加载
        assertFalse(reg.allInteriors().isEmpty(), "interiors 配置列表不能为空");
        assertFalse(reg.allRegions().isEmpty(), "regions 配置列表不能为空");
    }

    @Test
    public void testCustomSegmentSupport() {
        com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
        com.meileme.resort.service.ConfigRegistry reg = new com.meileme.resort.service.ConfigRegistry(mapper);
        reg.init();

        // 验证客群列表不为空，且包含管理员配置的客群
        assertFalse(reg.allSegments().isEmpty(), "客群列表不能为空");
        com.meileme.resort.config.SegmentConfig testSeg = reg.getSegment("TEST");
        if (testSeg == null) {
            // 如果是出厂默认配置，则手动注册一个自定义客群测试全链路
            testSeg = com.meileme.resort.config.SegmentConfig.builder()
                    .id("TEST")
                    .displayName("测试客群")
                    .description("单元测试客群")
                    .color("#38bdf8")
                    .build();
            reg.registerSegment(testSeg);
        }
        assertNotNull(reg.getSegment("TEST"), "自定义客群 TEST 必须能被正确读取");

        // 构造 DemandService 并验证潜在客流与吸引力计算能够包含 TEST
        com.meileme.resort.service.SkillTreeService skillService = new com.meileme.resort.service.SkillTreeService(reg, null);
        com.meileme.resort.service.ComfortService comfortService = new com.meileme.resort.service.ComfortService(skillService, reg);
        com.meileme.resort.service.DemandService demandService = new com.meileme.resort.service.DemandService(reg, comfortService, skillService);

        GameState state = new GameState();
        com.meileme.resort.model.DemandResult result = demandService.compute(state);
        assertNotNull(result);
        assertTrue(result.getPotentialBySegment().containsKey("TEST"), "潜在客流必须包含 TEST 客群");
        assertTrue(result.getAttractionBySegment().containsKey("TEST"), "吸引力得分必须包含 TEST 客群");
        assertTrue(result.getConversionBySegment().containsKey("TEST"), "转化率必须包含 TEST 客群");
        assertTrue(result.getServedBySegment().containsKey("TEST"), "接待人数必须包含 TEST 客群");
    }

    @Test
    public void testRoomCleaningAbilityCalculation() {
        com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
        com.meileme.resort.service.ConfigRegistry reg = new com.meileme.resort.service.ConfigRegistry(mapper);
        reg.init();

        com.meileme.resort.config.PositionConfig cleanerCfg = reg.getPosition("ROOM_CLEANER");
        assertNotNull(cleanerCfg, "客房保洁配置不应为空");
        double[] vals = cleanerCfg.getAbilityValueByLevel();
        assertNotNull(vals, "abilityValueByLevel 不应为空");
        assertEquals(3, vals.length);
        assertEquals(30.0, vals[0]);
        assertEquals(55.0, vals[1]);
        assertEquals(85.0, vals[2]);

        // 测试能力值获取
        assertEquals(55.0, cleanerCfg.abilityValueOf(com.meileme.resort.config.AbilityType.ROOM_CLEANING, 2));
        assertEquals(55.0, cleanerCfg.abilityValueOf(null, 2));
        assertEquals(55.0, cleanerCfg.abilityValueOf(2));

        // 构造 2 个 Lv2 客房保洁
        GameState state = new GameState();
        com.meileme.resort.model.StaffInstance s1 = new com.meileme.resort.model.StaffInstance();
        s1.setId("STAFF_1");
        s1.setPosition(com.meileme.resort.config.PositionType.ROOM_CLEANER);
        s1.setLevel(2);
        s1.setAlive(true);

        com.meileme.resort.model.StaffInstance s2 = new com.meileme.resort.model.StaffInstance();
        s2.setId("STAFF_2");
        s2.setPosition(com.meileme.resort.config.PositionType.ROOM_CLEANER);
        s2.setLevel(2);
        s2.setAlive(true);

        state.getStaffs().add(s1);
        state.getStaffs().add(s2);

        com.meileme.resort.service.SkillTreeService skillService = new com.meileme.resort.service.SkillTreeService(reg, null);
        com.meileme.resort.service.ComfortService comfortService = new com.meileme.resort.service.ComfortService(skillService, reg);

        java.util.Map<com.meileme.resort.config.AbilityType, Double> supply = comfortService.totalAbilitySupply(state);
        assertEquals(110.0, supply.get(com.meileme.resort.config.AbilityType.ROOM_CLEANING), 0.001, "2个Lv2保洁的客房清洁能力总供给应为 110.0");
    }

    @Test
    public void testStaffTrainingDurationAndNonDisruptiveCapacity() {
        com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
        com.meileme.resort.service.ConfigRegistry reg = new com.meileme.resort.service.ConfigRegistry(mapper);
        reg.init();

        com.meileme.resort.service.SkillTreeService skillService = new com.meileme.resort.service.SkillTreeService(reg, null);
        com.meileme.resort.service.StaffService staffService = new com.meileme.resort.service.StaffService(reg, skillService, new com.meileme.resort.service.IdGenerator());
        com.meileme.resort.service.ComfortService comfortService = new com.meileme.resort.service.ComfortService(skillService, reg);

        GameState state = new GameState();
        state.setFunds(10000.0);
        state.setRunning(true);

        // 创建一个 Lv1 客房保洁
        com.meileme.resort.model.StaffInstance staff = new com.meileme.resort.model.StaffInstance();
        staff.setId("CLEANER_1");
        staff.setPosition(com.meileme.resort.config.PositionType.ROOM_CLEANER);
        staff.setLevel(1);
        staff.setDaysAtCurrentLevel(10); // 达成在岗历练天数
        staff.setAlive(true);
        state.getStaffs().add(staff);

        assertTrue(staffService.trainingReady(state, staff), "在岗10天后应达到可培训状态");
        int trainDays = staffService.trainingDays(state, staff);
        assertTrue(trainDays >= 1, "培训工期应至少为 1 天");

        // 开启培训
        staffService.startTrain(state, staff);
        assertTrue(staff.isTraining(), "应处于培训状态");
        assertEquals(1, staff.getLevel(), "培训工期内等级仍保持为原等级 Lv1");
        assertFalse(staffService.trainingReady(state, staff), "培训中不可重复触发培训");

        // 核心验证：培训期间能力绝不脱产，按原等级 Lv1 (30.0) 正常供给
        var supplyDuringTraining = comfortService.totalAbilitySupply(state);
        assertEquals(30.0, supplyDuringTraining.get(com.meileme.resort.config.AbilityType.ROOM_CLEANING), 0.001, "培训期间按原等级正常提供30点能力值");

        // 推进工期
        for (int i = 0; i < trainDays; i++) {
            staffService.advanceOneDay(state);
        }

        // 培训结束，晋升至 Lv2
        assertFalse(staff.isTraining(), "工期结束后培训状态解除");
        assertEquals(2, staff.getLevel(), "培训完成后成功晋升为 Lv2");
        assertEquals(0, staff.getDaysAtCurrentLevel(), "新等级在岗天数重置为0");

        // 晋升后能力自动生效为 Lv2 (55.0)
        var supplyAfterUpgrade = comfortService.totalAbilitySupply(state);
        assertEquals(55.0, supplyAfterUpgrade.get(com.meileme.resort.config.AbilityType.ROOM_CLEANING), 0.001, "晋升后能力提升为 Lv2 的 55 点");
    }
}
