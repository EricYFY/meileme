package com.meileme.resort.service;

import com.meileme.resort.config.AbilityType;
import com.meileme.resort.config.Achievement;
import com.meileme.resort.config.AchievementCategory;
import com.meileme.resort.config.AchievementType;
import com.meileme.resort.config.BuildingForm;
import com.meileme.resort.config.CustomerSegment;
import com.meileme.resort.config.EvolutionLine;
import com.meileme.resort.config.MergeRecipe;
import com.meileme.resort.config.MotherBuildingType;
import com.meileme.resort.config.Plot;
import com.meileme.resort.config.Region;
import com.meileme.resort.config.ScenicConfig;
import com.meileme.resort.config.SkillCategory;
import com.meileme.resort.config.SkillEffectKey;
import com.meileme.resort.config.SkillEffectType;
import com.meileme.resort.config.SkillNode;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 静态配置注册表：集中注册全部游戏数值配置（形态 / 配方 / 地块 / 成就 / 技能 / 景区）。
 *
 * <p>所有数值集中于此，禁止散落硬编码；本期为「数值后期配置」阶段，下列数值为合理占位默认值。</p>
 */
@Component
public class ConfigRegistry {

    private final ScenicConfig scenic;
    private final Map<String, BuildingForm> formsById = new LinkedHashMap<>();
    private final List<MergeRecipe> mergeRecipes = new ArrayList<>();
    private final Map<String, Plot> plotsById = new LinkedHashMap<>();
    private final Map<String, Achievement> achievementsById = new LinkedHashMap<>();
    private final Map<String, SkillNode> skillsById = new LinkedHashMap<>();

    public ConfigRegistry() {
        // 形态须按「基础形态 → 分支形态 → 合并产物」顺序构建，以便 evolveFrom 引用已构建实例
        BuildingForm standardRoom = BuildingForm.builder("STANDARD_ROOM", "标准客房", MotherBuildingType.STANDARD_ROOM)
                .line(EvolutionLine.BASE).tier(0).capacityPerRoom(2)
                .pricePerRoom(280, 330, 380).buildCost(3000).evolveCost(0)
                .formLevelUpCost(1500, 2500).maintenance(120, 140, 160).baseComfort(50)
                .affinity(CustomerSegment.FAMILY, 1.0).affinity(CustomerSegment.BUSINESS, 1.0)
                .affinity(CustomerSegment.ROMANTIC, 0.8).affinity(CustomerSegment.WELLNESS, 0.8)
                .maxInteriorLevel(1, 2, 3)
                .abilityDemand(AbilityType.ROOM_CLEANING, 20).abilityDemand(AbilityType.ROOM_REPAIR, 10)
                .build();

        BuildingForm familyRoom = BuildingForm.builder("FAMILY_ROOM", "家庭亲子房", MotherBuildingType.STANDARD_ROOM)
                .line(EvolutionLine.FAMILY).tier(1).capacityPerRoom(4)
                .pricePerRoom(500, 580, 660).evolveCost(6000)
                .formLevelUpCost(3000, 5000).maintenance(180, 210, 240).baseComfort(62)
                .affinity(CustomerSegment.FAMILY, 1.8).affinity(CustomerSegment.BUSINESS, 0.5)
                .affinity(CustomerSegment.ROMANTIC, 1.0).affinity(CustomerSegment.WELLNESS, 0.9)
                .maxInteriorLevel(2, 3, 3)
                .abilityDemand(AbilityType.ROOM_CLEANING, 26).abilityDemand(AbilityType.ROOM_REPAIR, 12)
                .evolveFrom(standardRoom).build();

        BuildingForm familySuite = BuildingForm.builder("FAMILY_SUITE", "亲子套房", MotherBuildingType.STANDARD_ROOM)
                .line(EvolutionLine.FAMILY).tier(2).capacityPerRoom(6)
                .pricePerRoom(760, 880, 1000).evolveCost(15000)
                .formLevelUpCost(7000, 11000).maintenance(260, 300, 340).baseComfort(76)
                .affinity(CustomerSegment.FAMILY, 2.2).affinity(CustomerSegment.BUSINESS, 0.4)
                .affinity(CustomerSegment.ROMANTIC, 1.1).affinity(CustomerSegment.WELLNESS, 1.0)
                .maxInteriorLevel(2, 3, 3)
                .abilityDemand(AbilityType.ROOM_CLEANING, 34).abilityDemand(AbilityType.ROOM_REPAIR, 16)
                .evolveFrom(familyRoom).build();

        BuildingForm businessRoom = BuildingForm.builder("BUSINESS_ROOM", "商务行政房", MotherBuildingType.STANDARD_ROOM)
                .line(EvolutionLine.BUSINESS).tier(1).capacityPerRoom(2)
                .pricePerRoom(620, 720, 820).evolveCost(7000)
                .formLevelUpCost(3200, 5200).maintenance(200, 230, 260).baseComfort(64)
                .affinity(CustomerSegment.BUSINESS, 1.9).affinity(CustomerSegment.FAMILY, 0.5)
                .affinity(CustomerSegment.ROMANTIC, 1.0).affinity(CustomerSegment.WELLNESS, 0.9)
                .maxInteriorLevel(2, 3, 3)
                .abilityDemand(AbilityType.ROOM_CLEANING, 22).abilityDemand(AbilityType.ROOM_REPAIR, 14)
                .evolveFrom(standardRoom).build();

        BuildingForm businessSuite = BuildingForm.builder("BUSINESS_SUITE", "行政套房", MotherBuildingType.STANDARD_ROOM)
                .line(EvolutionLine.BUSINESS).tier(2).capacityPerRoom(3)
                .pricePerRoom(950, 1100, 1250).evolveCost(17000)
                .formLevelUpCost(7500, 12000).maintenance(280, 320, 360).baseComfort(80)
                .affinity(CustomerSegment.BUSINESS, 2.3).affinity(CustomerSegment.FAMILY, 0.4)
                .affinity(CustomerSegment.ROMANTIC, 1.2).affinity(CustomerSegment.WELLNESS, 1.1)
                .maxInteriorLevel(2, 3, 3)
                .abilityDemand(AbilityType.ROOM_CLEANING, 30).abilityDemand(AbilityType.ROOM_REPAIR, 18)
                .evolveFrom(businessRoom).build();

        BuildingForm mergedFamilyVilla = BuildingForm.builder("MERGED_FAMILY_VILLA", "亲子合院", MotherBuildingType.STANDARD_ROOM)
                .line(EvolutionLine.MERGE).tier(3).capacityPerRoom(10)
                .pricePerRoom(1500, 1750, 2000)
                .formLevelUpCost(9000, 14000).maintenance(420, 480, 540).baseComfort(95)
                .affinity(CustomerSegment.FAMILY, 2.6).affinity(CustomerSegment.BUSINESS, 0.6)
                .affinity(CustomerSegment.ROMANTIC, 1.4).affinity(CustomerSegment.WELLNESS, 1.3)
                .maxInteriorLevel(3, 3, 3)
                .abilityDemand(AbilityType.ROOM_CLEANING, 45).abilityDemand(AbilityType.ROOM_REPAIR, 22)
                .build();

        BuildingForm mergedBusinessHall = BuildingForm.builder("MERGED_BUSINESS_HALL", "商务行政馆", MotherBuildingType.STANDARD_ROOM)
                .line(EvolutionLine.MERGE).tier(3).capacityPerRoom(8)
                .pricePerRoom(1600, 1850, 2100)
                .formLevelUpCost(9500, 15000).maintenance(440, 500, 560).baseComfort(98)
                .affinity(CustomerSegment.BUSINESS, 2.6).affinity(CustomerSegment.FAMILY, 0.6)
                .affinity(CustomerSegment.ROMANTIC, 1.5).affinity(CustomerSegment.WELLNESS, 1.4)
                .maxInteriorLevel(3, 3, 3)
                .abilityDemand(AbilityType.ROOM_CLEANING, 44).abilityDemand(AbilityType.ROOM_REPAIR, 24)
                .build();

        // —— 其余住宿母本基础形态（仅登记，第一期不可建） ——
        BuildingForm luxuryRoom = BuildingForm.builder("LUXURY_ROOM", "豪华客房", MotherBuildingType.LUXURY_ROOM)
                .tier(0).capacityPerRoom(4).pricePerRoom(680, 780, 880).buildCost(8000)
                .formLevelUpCost(3000, 5000).maintenance(350, 390, 430).baseComfort(70)
                .affinity(CustomerSegment.FAMILY, 1.1).affinity(CustomerSegment.BUSINESS, 1.4)
                .affinity(CustomerSegment.ROMANTIC, 1.2).affinity(CustomerSegment.WELLNESS, 1.1)
                .maxInteriorLevel(2, 3, 3)
                .abilityDemand(AbilityType.ROOM_CLEANING, 24).abilityDemand(AbilityType.ROOM_REPAIR, 12)
                .build();

        BuildingForm woodenCabin = BuildingForm.builder("WOODEN_CABIN", "独栋木屋", MotherBuildingType.WOODEN_CABIN)
                .tier(0).capacityPerRoom(4).pricePerRoom(720, 820, 920).buildCost(12000)
                .formLevelUpCost(4000, 6500).maintenance(500, 550, 600).baseComfort(72)
                .affinity(CustomerSegment.FAMILY, 1.3).affinity(CustomerSegment.BUSINESS, 0.8)
                .affinity(CustomerSegment.ROMANTIC, 1.5).affinity(CustomerSegment.WELLNESS, 1.2)
                .maxInteriorLevel(2, 3, 3)
                .abilityDemand(AbilityType.ROOM_CLEANING, 24).abilityDemand(AbilityType.ROOM_REPAIR, 12)
                .build();

        BuildingForm duplexLoft = BuildingForm.builder("DUPLEX_LOFT", "复式LOFT", MotherBuildingType.DUPLEX_LOFT)
                .tier(0).capacityPerRoom(5).pricePerRoom(860, 960, 1060).buildCost(10000)
                .formLevelUpCost(3500, 6000).maintenance(420, 460, 500).baseComfort(68)
                .affinity(CustomerSegment.BUSINESS, 1.5).affinity(CustomerSegment.FAMILY, 1.2)
                .affinity(CustomerSegment.ROMANTIC, 1.3).affinity(CustomerSegment.WELLNESS, 0.9)
                .maxInteriorLevel(2, 3, 3)
                .abilityDemand(AbilityType.ROOM_CLEANING, 26).abilityDemand(AbilityType.ROOM_REPAIR, 13)
                .build();

        BuildingForm courtyardHouse = BuildingForm.builder("COURTYARD_HOUSE", "庭院小院", MotherBuildingType.COURTYARD_HOUSE)
                .tier(0).capacityPerRoom(6).pricePerRoom(1100, 1250, 1400).buildCost(15000)
                .formLevelUpCost(5000, 8000).maintenance(600, 660, 720).baseComfort(82)
                .affinity(CustomerSegment.WELLNESS, 1.6).affinity(CustomerSegment.FAMILY, 1.5)
                .affinity(CustomerSegment.ROMANTIC, 1.4).affinity(CustomerSegment.BUSINESS, 0.9)
                .maxInteriorLevel(2, 3, 3)
                .abilityDemand(AbilityType.ROOM_CLEANING, 30).abilityDemand(AbilityType.ROOM_REPAIR, 16)
                .build();

        BuildingForm multiBedroomHouse = BuildingForm.builder("MULTI_BEDROOM_HOUSE", "多卧平房", MotherBuildingType.MULTI_BEDROOM_HOUSE)
                .tier(0).capacityPerRoom(8).pricePerRoom(1250, 1400, 1550).buildCost(11000)
                .formLevelUpCost(4000, 6500).maintenance(450, 500, 550).baseComfort(66)
                .affinity(CustomerSegment.FAMILY, 1.7).affinity(CustomerSegment.BUSINESS, 0.5)
                .affinity(CustomerSegment.ROMANTIC, 0.9).affinity(CustomerSegment.WELLNESS, 0.9)
                .maxInteriorLevel(2, 3, 3)
                .abilityDemand(AbilityType.ROOM_CLEANING, 32).abilityDemand(AbilityType.ROOM_REPAIR, 14)
                .build();

        // —— 配套母本基础形态（9 种，仅登记） ——
        BuildingForm restaurant = BuildingForm.builder("RESTAURANT", "餐厅", MotherBuildingType.RESTAURANT)
                .tier(0).serviceCapacity(40).unitPrice(80, 95, 110).buildCost(6000)
                .formLevelUpCost(2500, 4200).maintenance(220, 260, 300).baseComfort(40)
                .affinity(CustomerSegment.FAMILY, 1.2).affinity(CustomerSegment.BUSINESS, 1.0)
                .affinity(CustomerSegment.ROMANTIC, 1.0).affinity(CustomerSegment.WELLNESS, 0.8)
                .maxInteriorLevel(2, 3, 3)
                .abilityDemand(AbilityType.RESTAURANT_SERVICE, 20).abilityDemand(AbilityType.COOKING, 15)
                .build();

        BuildingForm hotSpringPool = BuildingForm.builder("HOT_SPRING_POOL", "温泉泡池", MotherBuildingType.HOT_SPRING_POOL)
                .tier(0).serviceCapacity(20).unitPrice(60, 75, 90).buildCost(7500)
                .formLevelUpCost(2800, 4600).maintenance(280, 320, 360).baseComfort(45)
                .affinity(CustomerSegment.WELLNESS, 1.8).affinity(CustomerSegment.ROMANTIC, 1.3)
                .affinity(CustomerSegment.FAMILY, 0.9).affinity(CustomerSegment.BUSINESS, 0.7)
                .maxInteriorLevel(2, 3, 3)
                .abilityDemand(AbilityType.HOT_SPRING_OPERATION, 18).abilityDemand(AbilityType.HOT_SPRING_REPAIR, 10)
                .build();

        BuildingForm viewingPlatform = BuildingForm.builder("VIEWING_PLATFORM", "观景台", MotherBuildingType.VIEWING_PLATFORM)
                .tier(0).serviceCapacity(30).unitPrice(40, 50, 60).buildCost(4000)
                .formLevelUpCost(1800, 3000).maintenance(150, 180, 210).baseComfort(35)
                .affinity(CustomerSegment.ROMANTIC, 1.4).affinity(CustomerSegment.WELLNESS, 1.2)
                .affinity(CustomerSegment.FAMILY, 1.1).affinity(CustomerSegment.BUSINESS, 0.9)
                .maxInteriorLevel(2, 3, 3)
                .build();

        BuildingForm receptionLobby = BuildingForm.builder("RECEPTION_LOBBY", "接待大堂", MotherBuildingType.RECEPTION_LOBBY)
                .tier(0).serviceCapacity(0).unitPrice(0, 0, 0).buildCost(5000)
                .formLevelUpCost(2000, 3400).maintenance(180, 210, 240).baseComfort(30)
                .affinity(CustomerSegment.FAMILY, 1.0).affinity(CustomerSegment.BUSINESS, 1.0)
                .affinity(CustomerSegment.ROMANTIC, 1.0).affinity(CustomerSegment.WELLNESS, 1.0)
                .maxInteriorLevel(2, 3, 3)
                .build();

        BuildingForm teaLibrary = BuildingForm.builder("TEA_LIBRARY", "茶室书吧", MotherBuildingType.TEA_LIBRARY)
                .tier(0).serviceCapacity(15).unitPrice(50, 60, 70).buildCost(4500)
                .formLevelUpCost(1900, 3200).maintenance(160, 190, 220).baseComfort(38)
                .affinity(CustomerSegment.WELLNESS, 1.4).affinity(CustomerSegment.ROMANTIC, 1.2)
                .affinity(CustomerSegment.FAMILY, 1.0).affinity(CustomerSegment.BUSINESS, 1.0)
                .maxInteriorLevel(2, 3, 3)
                .build();

        BuildingForm cultureWorkshop = BuildingForm.builder("CULTURE_WORKSHOP", "文创工坊", MotherBuildingType.CULTURE_WORKSHOP)
                .tier(0).serviceCapacity(12).unitPrice(55, 65, 80).buildCost(5500)
                .formLevelUpCost(2200, 3600).maintenance(200, 230, 260).baseComfort(36)
                .affinity(CustomerSegment.FAMILY, 1.3).affinity(CustomerSegment.ROMANTIC, 1.1)
                .affinity(CustomerSegment.WELLNESS, 1.0).affinity(CustomerSegment.BUSINESS, 0.8)
                .maxInteriorLevel(2, 3, 3)
                .build();

        BuildingForm logisticsWarehouse = BuildingForm.builder("LOGISTICS_WAREHOUSE", "后勤库房", MotherBuildingType.LOGISTICS_WAREHOUSE)
                .tier(0).serviceCapacity(0).unitPrice(0, 0, 0).buildCost(3500)
                .formLevelUpCost(1500, 2500).maintenance(140, 160, 180).baseComfort(20)
                .affinity(CustomerSegment.FAMILY, 0.8).affinity(CustomerSegment.BUSINESS, 0.8)
                .affinity(CustomerSegment.ROMANTIC, 0.8).affinity(CustomerSegment.WELLNESS, 0.8)
                .maxInteriorLevel(2, 3, 3)
                .build();

        BuildingForm outdoorCamp = BuildingForm.builder("OUTDOOR_CAMP", "户外营地", MotherBuildingType.OUTDOOR_CAMP)
                .tier(0).serviceCapacity(25).unitPrice(35, 45, 55).buildCost(3000)
                .formLevelUpCost(1400, 2400).maintenance(120, 140, 160).baseComfort(34)
                .affinity(CustomerSegment.FAMILY, 1.4).affinity(CustomerSegment.ROMANTIC, 1.2)
                .affinity(CustomerSegment.WELLNESS, 1.1).affinity(CustomerSegment.BUSINESS, 0.6)
                .maxInteriorLevel(2, 3, 3)
                .build();

        BuildingForm parentChildPark = BuildingForm.builder("PARENT_CHILD_PARK", "亲子乐园", MotherBuildingType.PARENT_CHILD_PARK)
                .tier(0).serviceCapacity(35).unitPrice(45, 55, 65).buildCost(6500)
                .formLevelUpCost(2600, 4400).maintenance(240, 280, 320).baseComfort(42)
                .affinity(CustomerSegment.FAMILY, 1.8).affinity(CustomerSegment.ROMANTIC, 0.9)
                .affinity(CustomerSegment.WELLNESS, 0.8).affinity(CustomerSegment.BUSINESS, 0.5)
                .maxInteriorLevel(2, 3, 3)
                .build();

        registerForm(standardRoom, familyRoom, familySuite, businessRoom, businessSuite,
                mergedFamilyVilla, mergedBusinessHall,
                luxuryRoom, woodenCabin, duplexLoft, courtyardHouse, multiBedroomHouse,
                restaurant, hotSpringPool, viewingPlatform, receptionLobby, teaLibrary,
                cultureWorkshop, logisticsWarehouse, outdoorCamp, parentChildPark);

        // ===================== 合并进化配方 =====================
        // 配方框架完整支持跨母本合并（sourceFormA / sourceFormB 可属不同母本）；
        // 第一期仅标准客房母本可建造，故下列配方均落在标准客房进化线内。
        mergeRecipes.add(new MergeRecipe("MERGE_FAMILY_VILLA", "亲子合院（合并）",
                familySuite, 3, familySuite, 3, mergedFamilyVilla, 1, 30000));
        mergeRecipes.add(new MergeRecipe("MERGE_BUSINESS_HALL", "商务行政馆（合并）",
                businessSuite, 3, businessSuite, 3, mergedBusinessHall, 1, 32000));
        mergeRecipes.add(new MergeRecipe("MERGE_CROSS_LINE_HALL", "跨线合并：商务行政馆",
                familySuite, 3, businessSuite, 3, mergedBusinessHall, 1, 40000));

        // ===================== 地块 =====================
        registerPlot(
                new Plot("A_COUNTRYSIDE", "A村·乡间地块", Region.VILLAGE_A, 1, 8,
                        Set.of(MotherBuildingType.STANDARD_ROOM),
                        Set.of(MotherBuildingType.RESTAURANT, MotherBuildingType.VIEWING_PLATFORM),
                        1, List.of(), true),
                new Plot("A_HILLSIDE", "A村·山坡地块", Region.VILLAGE_A, 2, 10,
                        Set.of(MotherBuildingType.STANDARD_ROOM),
                        Set.of(MotherBuildingType.RESTAURANT, MotherBuildingType.HOT_SPRING_POOL),
                        2, List.of("ACH_BUILDING_20"), false),
                new Plot("A_LAKESIDE", "A村·湖区地块", Region.VILLAGE_A, 3, 12,
                        Set.of(MotherBuildingType.STANDARD_ROOM),
                        Set.of(MotherBuildingType.RESTAURANT, MotherBuildingType.HOT_SPRING_POOL, MotherBuildingType.VIEWING_PLATFORM),
                        3, List.of("ACH_GUEST_500", "ACH_INCOME_100000"), false),
                new Plot("B_COUNTRYSIDE", "B镇·乡间地块", Region.TOWN_B, 1, 12,
                        Set.of(MotherBuildingType.STANDARD_ROOM, MotherBuildingType.LUXURY_ROOM),
                        Set.of(MotherBuildingType.RESTAURANT, MotherBuildingType.RECEPTION_LOBBY),
                        2, List.of("ACH_A_TOTAL", "ACH_BUILDING_40"), false),
                new Plot("B_HILLSIDE", "B镇·山坡地块", Region.TOWN_B, 2, 14,
                        Set.of(MotherBuildingType.STANDARD_ROOM, MotherBuildingType.LUXURY_ROOM, MotherBuildingType.WOODEN_CABIN),
                        Set.of(MotherBuildingType.RESTAURANT, MotherBuildingType.HOT_SPRING_POOL, MotherBuildingType.TEA_LIBRARY),
                        3, List.of("ACH_A_TOTAL", "ACH_GUEST_2000"), false),
                new Plot("B_LAKESIDE", "B镇·湖区地块", Region.TOWN_B, 3, 16,
                        Set.of(MotherBuildingType.STANDARD_ROOM, MotherBuildingType.LUXURY_ROOM, MotherBuildingType.COURTYARD_HOUSE),
                        Set.of(MotherBuildingType.RESTAURANT, MotherBuildingType.HOT_SPRING_POOL, MotherBuildingType.VIEWING_PLATFORM),
                        3, List.of("ACH_A_TOTAL", "ACH_INCOME_1000000"), false));

        // ===================== 成就 =====================
        registerAchievement(
                new Achievement("ACH_INCOME_100000", "累计总收入 10 万", AchievementCategory.FINANCE,
                        AchievementType.CUMULATIVE_INCOME, 100000, ""),
                new Achievement("ACH_ASSETS_100000", "总资产 10 万", AchievementCategory.FINANCE,
                        AchievementType.TOTAL_ASSETS, 100000, ""),
                new Achievement("ACH_A_TOTAL", "A村总成就·累计收入 30 万", AchievementCategory.FINANCE,
                        AchievementType.CUMULATIVE_INCOME, 300000, ""),
                new Achievement("ACH_INCOME_1000000", "累计总收入 100 万", AchievementCategory.FINANCE,
                        AchievementType.CUMULATIVE_INCOME, 1000000, ""),
                new Achievement("ACH_BUILDING_20", "已建建筑 20 座", AchievementCategory.BUILDING,
                        AchievementType.BUILDING_COUNT, 20, ""),
                new Achievement("ACH_BUILDING_40", "已建建筑 40 座", AchievementCategory.BUILDING,
                        AchievementType.BUILDING_COUNT, 40, ""),
                new Achievement("ACH_FORM_FAMILY_SUITE", "建成亲子套房 Lv1", AchievementCategory.BUILDING,
                        AchievementType.FORM_LEVEL_COUNT, 1, "FAMILY_SUITE_1"),
                new Achievement("ACH_FORM_MERGED_VILLA", "建成亲子合院 Lv1", AchievementCategory.BUILDING,
                        AchievementType.FORM_LEVEL_COUNT, 1, "MERGED_FAMILY_VILLA_1"),
                new Achievement("ACH_GUEST_500", "累计接待 500 人", AchievementCategory.GUEST,
                        AchievementType.CUMULATIVE_GUESTS, 500, ""),
                new Achievement("ACH_GUEST_2000", "累计接待 2000 人", AchievementCategory.GUEST,
                        AchievementType.CUMULATIVE_GUESTS, 2000, ""),
                new Achievement("ACH_SEG_FAMILY_300", "家庭客群累计接待 300 人", AchievementCategory.GUEST,
                        AchievementType.SEGMENT_GUESTS, 300, "FAMILY"),
                new Achievement("ACH_STAFF_10", "员工总数 10 人", AchievementCategory.STAFF,
                        AchievementType.STAFF_COUNT, 10, ""),
                new Achievement("ACH_POS_CHEF_LV3", "拥有 Lv3 厨师", AchievementCategory.STAFF,
                        AchievementType.POSITION_LEVEL_COUNT, 1, "CHEF_3"));

        // ===================== 技能树（DAG，禁止循环依赖） =====================
        registerSkill(
                // 一级
                node("SK_EVOLVE_COST", "进化节流 I", "建筑形态进化费用 −10%", SkillCategory.BUILDING,
                        List.of(), SkillEffectType.COST_DISCOUNT, SkillEffectKey.EVOLVE_COST_DISCOUNT, 0.10),
                node("SK_INTERIOR_COST", "内装节流 I", "内装升级费用 −10%", SkillCategory.INTERIOR,
                        List.of(), SkillEffectType.COST_DISCOUNT, SkillEffectKey.INTERIOR_UPGRADE_COST_DISCOUNT, 0.10),
                node("SK_ATTRACT", "引流 I", "分客群吸引力 +10%", SkillCategory.DEMAND,
                        List.of(), SkillEffectType.MULTIPLIER, SkillEffectKey.SEGMENT_ATTRACTION_MULT, 0.10),
                node("SK_HIRE_COST", "招聘节流 I", "招聘消耗 −10%", SkillCategory.HIRING,
                        List.of(), SkillEffectType.COST_DISCOUNT, SkillEffectKey.HIRE_COST_DISCOUNT, 0.10),
                node("SK_ROOM_PRICE", "房价溢价 I", "房晚基准单价 +5%", SkillCategory.FINANCE,
                        List.of(), SkillEffectType.MULTIPLIER, SkillEffectKey.ROOM_PRICE_MULT, 0.05),
                node("SK_REGION_GATE", "区块通达 I", "区块解锁所需成就门槛 −10%", SkillCategory.REGION,
                        List.of(), SkillEffectType.COST_DISCOUNT, SkillEffectKey.REGION_ACHIEVEMENT_DISCOUNT, 0.10),
                // 二级
                node("SK_EVOLVE_COST_II", "进化节流 II", "建筑形态进化费用再 −10%", SkillCategory.BUILDING,
                        List.of("SK_EVOLVE_COST"), SkillEffectType.COST_DISCOUNT, SkillEffectKey.EVOLVE_COST_DISCOUNT, 0.10),
                node("SK_MERGE_COST", "合并节流 I", "合并进化消耗 −15%", SkillCategory.BUILDING,
                        List.of("SK_EVOLVE_COST"), SkillEffectType.COST_DISCOUNT, SkillEffectKey.MERGE_COST_DISCOUNT, 0.15),
                node("SK_PLOT_LIMIT", "地块扩容 I", "单地块可建建筑上限 +2", SkillCategory.BUILDING,
                        List.of("SK_EVOLVE_COST"), SkillEffectType.FLAT, SkillEffectKey.PLOT_BUILD_LIMIT, 2.0),
                node("SK_INTERIOR_COMFORT", "舒适内装 I", "内装舒适度加成 +15%", SkillCategory.INTERIOR,
                        List.of("SK_INTERIOR_COST"), SkillEffectType.MULTIPLIER, SkillEffectKey.INTERIOR_COMFORT_MULT, 0.15),
                node("SK_INTERIOR_CAPACITY", "承载内装 I", "内装承载加成 +15%", SkillCategory.INTERIOR,
                        List.of("SK_INTERIOR_COST"), SkillEffectType.MULTIPLIER, SkillEffectKey.INTERIOR_CAPACITY_MULT, 0.15),
                node("SK_CONVERSION", "转化率 I", "客群转化率 +15%", SkillCategory.DEMAND,
                        List.of("SK_ATTRACT"), SkillEffectType.MULTIPLIER, SkillEffectKey.CONVERSION_RATE_MULT, 0.15),
                node("SK_STAFF_ABILITY", "人力素质 I", "员工能力值 +15%", SkillCategory.HIRING,
                        List.of("SK_HIRE_COST"), SkillEffectType.MULTIPLIER, SkillEffectKey.STAFF_ABILITY_MULT, 0.15),
                node("SK_STAFF_TRAIN", "岗前培训 I", "员工自动升级所需在岗时长 −15%", SkillCategory.HIRING,
                        List.of("SK_HIRE_COST"), SkillEffectType.COST_DISCOUNT, SkillEffectKey.STAFF_UPGRADE_DAYS_MULT, 0.15),
                node("SK_STAFF_SALARY", "薪资优化 I", "员工每日薪资 −10%", SkillCategory.HIRING,
                        List.of("SK_HIRE_COST"), SkillEffectType.COST_DISCOUNT, SkillEffectKey.STAFF_SALARY_DISCOUNT, 0.10),
                node("SK_SUPPORT_PRICE", "配套溢价 I", "配套基准单价 +5%", SkillCategory.FINANCE,
                        List.of("SK_ROOM_PRICE"), SkillEffectType.MULTIPLIER, SkillEffectKey.SUPPORT_PRICE_MULT, 0.05),
                node("SK_MAINTENANCE", "运维节流 I", "建筑维护成本 −10%", SkillCategory.FINANCE,
                        List.of("SK_ROOM_PRICE"), SkillEffectType.COST_DISCOUNT, SkillEffectKey.MAINTENANCE_COST_DISCOUNT, 0.10),
                // 三级
                node("SK_MERGE_BONUS", "合并专精", "合并产物房价 +10%", SkillCategory.BUILDING,
                        List.of("SK_MERGE_COST"), SkillEffectType.FLAT, SkillEffectKey.MERGE_BONUS, 0.10),
                node("SK_FIRE_COMP", "离职安抚", "解雇补偿 −20%", SkillCategory.HIRING,
                        List.of("SK_STAFF_ABILITY"), SkillEffectType.COST_DISCOUNT, SkillEffectKey.FIRE_COMPENSATION_DISCOUNT, 0.20));

        // ===================== 景区顶层 =====================
        Map<CustomerSegment, Double> ratios = new LinkedHashMap<>();
        ratios.put(CustomerSegment.FAMILY, 0.35);
        ratios.put(CustomerSegment.BUSINESS, 0.25);
        ratios.put(CustomerSegment.ROMANTIC, 0.20);
        ratios.put(CustomerSegment.WELLNESS, 0.20);
        this.scenic = new ScenicConfig("云栖山谷景区", 60, Collections.unmodifiableMap(ratios));
    }

    private static SkillNode node(String id, String name, String desc, SkillCategory cat,
                                  List<String> prereq, SkillEffectType type, SkillEffectKey key, double value) {
        return new SkillNode(id, name, desc, cat, prereq, type, key, value);
    }

    private void registerForm(BuildingForm... forms) {
        for (BuildingForm f : forms) {
            formsById.put(f.getId(), f);
        }
    }

    private void registerPlot(Plot... plots) {
        for (Plot p : plots) {
            plotsById.put(p.getId(), p);
        }
    }

    private void registerAchievement(Achievement... list) {
        for (Achievement a : list) {
            achievementsById.put(a.getId(), a);
        }
    }

    private void registerSkill(SkillNode... list) {
        for (SkillNode s : list) {
            skillsById.put(s.getId(), s);
        }
    }

    // ===================== 查询接口 =====================

    public ScenicConfig scenic() {
        return scenic;
    }

    public BuildingForm form(String id) {
        return id == null ? null : formsById.get(id);
    }

    public List<BuildingForm> allForms() {
        return Collections.unmodifiableList(new ArrayList<>(formsById.values()));
    }

    /** 指定母本的基础形态（tier = 0） */
    public BuildingForm baseFormOf(MotherBuildingType mother) {
        for (BuildingForm f : formsById.values()) {
            if (f.getMother() == mother && f.getTier() == 0) {
                return f;
            }
        }
        return null;
    }

    /** 某形态可直接分支进化到的下一阶段形态列表 */
    public List<BuildingForm> branchTargetsOf(BuildingForm current) {
        List<BuildingForm> list = new ArrayList<>();
        for (BuildingForm f : formsById.values()) {
            if (f.getEvolveFrom() == current) {
                list.add(f);
            }
        }
        return list;
    }

    public MergeRecipe recipe(String id) {
        if (id == null) {
            return null;
        }
        for (MergeRecipe r : mergeRecipes) {
            if (r.getId().equals(id)) {
                return r;
            }
        }
        return null;
    }

    /** 匹配给定两个「形态 + 等级」的合并配方 */
    public MergeRecipe matchRecipe(BuildingForm formA, int levelA, BuildingForm formB, int levelB) {
        for (MergeRecipe r : mergeRecipes) {
            if (r.matches(formA, levelA, formB, levelB)) {
                return r;
            }
        }
        return null;
    }

    public List<MergeRecipe> allRecipes() {
        return Collections.unmodifiableList(mergeRecipes);
    }

    public Plot plot(String id) {
        return id == null ? null : plotsById.get(id);
    }

    public List<Plot> allPlots() {
        return Collections.unmodifiableList(new ArrayList<>(plotsById.values()));
    }

    public Achievement achievement(String id) {
        return id == null ? null : achievementsById.get(id);
    }

    public List<Achievement> allAchievements() {
        return Collections.unmodifiableList(new ArrayList<>(achievementsById.values()));
    }

    public SkillNode skill(String id) {
        return id == null ? null : skillsById.get(id);
    }

    public List<SkillNode> allSkills() {
        return Collections.unmodifiableList(new ArrayList<>(skillsById.values()));
    }

    /** 解锁 B 镇所需的 A 村总成就 ID */
    public String regionGateAchievement() {
        return "ACH_A_TOTAL";
    }
}
