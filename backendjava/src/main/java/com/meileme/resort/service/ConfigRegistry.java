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
                .formLevelUpCost(1500, 2500).formLevelUpDays(2, 3).baseComfort(50)
                .affinity(CustomerSegment.FAMILY, 1.0).affinity(CustomerSegment.BUSINESS, 1.0)
                .affinity(CustomerSegment.ROMANTIC, 0.8).affinity(CustomerSegment.WELLNESS, 0.8)
                .maxInteriorLevel(1, 2, 3)
                .abilityDemand(AbilityType.ROOM_CLEANING, 20).abilityDemand(AbilityType.ROOM_REPAIR, 10)
                .build();

        BuildingForm familyRoom = BuildingForm.builder("FAMILY_ROOM", "家庭亲子房", MotherBuildingType.STANDARD_ROOM)
                .line(EvolutionLine.FAMILY).tier(1).capacityPerRoom(4)
                .pricePerRoom(500, 580, 660).evolveCost(6000)
                .formLevelUpCost(3000, 5000).formLevelUpDays(3, 4).baseComfort(62)
                .affinity(CustomerSegment.FAMILY, 1.8).affinity(CustomerSegment.BUSINESS, 0.5)
                .affinity(CustomerSegment.ROMANTIC, 1.0).affinity(CustomerSegment.WELLNESS, 0.9)
                .maxInteriorLevel(2, 3, 3)
                .abilityDemand(AbilityType.ROOM_CLEANING, 26).abilityDemand(AbilityType.ROOM_REPAIR, 12)
                .evolveFrom(standardRoom).evolveDays(4).build();

        BuildingForm familySuite = BuildingForm.builder("FAMILY_SUITE", "亲子套房", MotherBuildingType.STANDARD_ROOM)
                .line(EvolutionLine.FAMILY).tier(2).capacityPerRoom(6)
                .pricePerRoom(760, 880, 1000).evolveCost(15000)
                .formLevelUpCost(7000, 11000).formLevelUpDays(4, 5).baseComfort(76)
                .affinity(CustomerSegment.FAMILY, 2.2).affinity(CustomerSegment.BUSINESS, 0.4)
                .affinity(CustomerSegment.ROMANTIC, 1.1).affinity(CustomerSegment.WELLNESS, 1.0)
                .maxInteriorLevel(2, 3, 3)
                .abilityDemand(AbilityType.ROOM_CLEANING, 34).abilityDemand(AbilityType.ROOM_REPAIR, 16)
                .evolveFrom(familyRoom).evolveDays(5).build();

        BuildingForm businessRoom = BuildingForm.builder("BUSINESS_ROOM", "商务行政房", MotherBuildingType.STANDARD_ROOM)
                .line(EvolutionLine.BUSINESS).tier(1).capacityPerRoom(2)
                .pricePerRoom(620, 720, 820).evolveCost(7000)
                .formLevelUpCost(3200, 5200).formLevelUpDays(3, 4).baseComfort(64)
                .affinity(CustomerSegment.BUSINESS, 1.9).affinity(CustomerSegment.FAMILY, 0.5)
                .affinity(CustomerSegment.ROMANTIC, 1.0).affinity(CustomerSegment.WELLNESS, 0.9)
                .maxInteriorLevel(2, 3, 3)
                .abilityDemand(AbilityType.ROOM_CLEANING, 22).abilityDemand(AbilityType.ROOM_REPAIR, 14)
                .evolveFrom(standardRoom).evolveDays(4).build();

        BuildingForm businessSuite = BuildingForm.builder("BUSINESS_SUITE", "行政套房", MotherBuildingType.STANDARD_ROOM)
                .line(EvolutionLine.BUSINESS).tier(2).capacityPerRoom(3)
                .pricePerRoom(950, 1100, 1250).evolveCost(17000)
                .formLevelUpCost(7500, 12000).formLevelUpDays(4, 5).baseComfort(80)
                .affinity(CustomerSegment.BUSINESS, 2.3).affinity(CustomerSegment.FAMILY, 0.4)
                .affinity(CustomerSegment.ROMANTIC, 1.2).affinity(CustomerSegment.WELLNESS, 1.1)
                .maxInteriorLevel(2, 3, 3)
                .abilityDemand(AbilityType.ROOM_CLEANING, 30).abilityDemand(AbilityType.ROOM_REPAIR, 18)
                .evolveFrom(businessRoom).evolveDays(5).build();

        BuildingForm mergedFamilyVilla = BuildingForm.builder("MERGED_FAMILY_VILLA", "亲子合院", MotherBuildingType.STANDARD_ROOM)
                .line(EvolutionLine.MERGE).tier(3).capacityPerRoom(10)
                .pricePerRoom(1500, 1750, 2000)
                .formLevelUpCost(9000, 14000).formLevelUpDays(5, 6).baseComfort(95)
                .affinity(CustomerSegment.FAMILY, 2.6).affinity(CustomerSegment.BUSINESS, 0.6)
                .affinity(CustomerSegment.ROMANTIC, 1.4).affinity(CustomerSegment.WELLNESS, 1.3)
                .maxInteriorLevel(3, 3, 3)
                .abilityDemand(AbilityType.ROOM_CLEANING, 45).abilityDemand(AbilityType.ROOM_REPAIR, 22)
                .build();

        BuildingForm mergedBusinessHall = BuildingForm.builder("MERGED_BUSINESS_HALL", "商务行政馆", MotherBuildingType.STANDARD_ROOM)
                .line(EvolutionLine.MERGE).tier(3).capacityPerRoom(8)
                .pricePerRoom(1600, 1850, 2100)
                .formLevelUpCost(9500, 15000).formLevelUpDays(5, 6).baseComfort(98)
                .affinity(CustomerSegment.BUSINESS, 2.6).affinity(CustomerSegment.FAMILY, 0.6)
                .affinity(CustomerSegment.ROMANTIC, 1.5).affinity(CustomerSegment.WELLNESS, 1.4)
                .maxInteriorLevel(3, 3, 3)
                .abilityDemand(AbilityType.ROOM_CLEANING, 44).abilityDemand(AbilityType.ROOM_REPAIR, 24)
                .build();

        // —— 其余住宿母本基础形态（仅登记，第一期不可建） ——
        BuildingForm luxuryRoom = BuildingForm.builder("LUXURY_ROOM", "豪华客房", MotherBuildingType.LUXURY_ROOM)
                .tier(0).capacityPerRoom(4).pricePerRoom(680, 780, 880).buildCost(8000)
                .formLevelUpCost(3000, 5000).formLevelUpDays(3, 4).baseComfort(70)
                .affinity(CustomerSegment.FAMILY, 1.1).affinity(CustomerSegment.BUSINESS, 1.4)
                .affinity(CustomerSegment.ROMANTIC, 1.2).affinity(CustomerSegment.WELLNESS, 1.1)
                .maxInteriorLevel(2, 3, 3)
                .abilityDemand(AbilityType.ROOM_CLEANING, 24).abilityDemand(AbilityType.ROOM_REPAIR, 12)
                .build();

        BuildingForm woodenCabin = BuildingForm.builder("WOODEN_CABIN", "独栋木屋", MotherBuildingType.WOODEN_CABIN)
                .tier(0).capacityPerRoom(4).pricePerRoom(720, 820, 920).buildCost(12000)
                .formLevelUpCost(4000, 6500).formLevelUpDays(4, 5).baseComfort(72)
                .affinity(CustomerSegment.FAMILY, 1.3).affinity(CustomerSegment.BUSINESS, 0.8)
                .affinity(CustomerSegment.ROMANTIC, 1.5).affinity(CustomerSegment.WELLNESS, 1.2)
                .maxInteriorLevel(2, 3, 3)
                .abilityDemand(AbilityType.ROOM_CLEANING, 24).abilityDemand(AbilityType.ROOM_REPAIR, 12)
                .build();

        BuildingForm duplexLoft = BuildingForm.builder("DUPLEX_LOFT", "复式LOFT", MotherBuildingType.DUPLEX_LOFT)
                .tier(0).capacityPerRoom(5).pricePerRoom(860, 960, 1060).buildCost(10000)
                .formLevelUpCost(3500, 6000).formLevelUpDays(3, 5).baseComfort(68)
                .affinity(CustomerSegment.BUSINESS, 1.5).affinity(CustomerSegment.FAMILY, 1.2)
                .affinity(CustomerSegment.ROMANTIC, 1.3).affinity(CustomerSegment.WELLNESS, 0.9)
                .maxInteriorLevel(2, 3, 3)
                .abilityDemand(AbilityType.ROOM_CLEANING, 26).abilityDemand(AbilityType.ROOM_REPAIR, 13)
                .build();

        BuildingForm courtyardHouse = BuildingForm.builder("COURTYARD_HOUSE", "庭院小院", MotherBuildingType.COURTYARD_HOUSE)
                .tier(0).capacityPerRoom(6).pricePerRoom(1100, 1250, 1400).buildCost(15000)
                .formLevelUpCost(5000, 8000).formLevelUpDays(5, 6).baseComfort(82)
                .affinity(CustomerSegment.WELLNESS, 1.6).affinity(CustomerSegment.FAMILY, 1.5)
                .affinity(CustomerSegment.ROMANTIC, 1.4).affinity(CustomerSegment.BUSINESS, 0.9)
                .maxInteriorLevel(2, 3, 3)
                .abilityDemand(AbilityType.ROOM_CLEANING, 30).abilityDemand(AbilityType.ROOM_REPAIR, 16)
                .build();

        BuildingForm multiBedroomHouse = BuildingForm.builder("MULTI_BEDROOM_HOUSE", "多卧平房", MotherBuildingType.MULTI_BEDROOM_HOUSE)
                .tier(0).capacityPerRoom(8).pricePerRoom(1250, 1400, 1550).buildCost(11000)
                .formLevelUpCost(4000, 6500).formLevelUpDays(4, 5).baseComfort(66)
                .affinity(CustomerSegment.FAMILY, 1.7).affinity(CustomerSegment.BUSINESS, 0.5)
                .affinity(CustomerSegment.ROMANTIC, 0.9).affinity(CustomerSegment.WELLNESS, 0.9)
                .maxInteriorLevel(2, 3, 3)
                .abilityDemand(AbilityType.ROOM_CLEANING, 32).abilityDemand(AbilityType.ROOM_REPAIR, 14)
                .build();

        // —— 配套母本基础形态（9 种，仅登记） ——
        BuildingForm restaurant = BuildingForm.builder("RESTAURANT", "餐厅", MotherBuildingType.RESTAURANT)
                .tier(0).serviceCapacity(40).unitPrice(80, 95, 110).buildCost(6000)
                .formLevelUpCost(2500, 4200).formLevelUpDays(2, 3).baseComfort(40)
                .affinity(CustomerSegment.FAMILY, 1.2).affinity(CustomerSegment.BUSINESS, 1.0)
                .affinity(CustomerSegment.ROMANTIC, 1.0).affinity(CustomerSegment.WELLNESS, 0.8)
                .maxInteriorLevel(2, 3, 3)
                .abilityDemand(AbilityType.RESTAURANT_SERVICE, 20).abilityDemand(AbilityType.COOKING, 15)
                .build();

        BuildingForm hotSpringPool = BuildingForm.builder("HOT_SPRING_POOL", "温泉泡池", MotherBuildingType.HOT_SPRING_POOL)
                .tier(0).serviceCapacity(20).unitPrice(60, 75, 90).buildCost(7500)
                .formLevelUpCost(2800, 4600).formLevelUpDays(3, 4).baseComfort(45)
                .affinity(CustomerSegment.WELLNESS, 1.8).affinity(CustomerSegment.ROMANTIC, 1.3)
                .affinity(CustomerSegment.FAMILY, 0.9).affinity(CustomerSegment.BUSINESS, 0.7)
                .maxInteriorLevel(2, 3, 3)
                .abilityDemand(AbilityType.HOT_SPRING_OPERATION, 18).abilityDemand(AbilityType.HOT_SPRING_REPAIR, 10)
                .build();

        BuildingForm viewingPlatform = BuildingForm.builder("VIEWING_PLATFORM", "观景台", MotherBuildingType.VIEWING_PLATFORM)
                .tier(0).serviceCapacity(30).unitPrice(40, 50, 60).buildCost(4000)
                .formLevelUpCost(1800, 3000).formLevelUpDays(1, 2).baseComfort(35)
                .affinity(CustomerSegment.ROMANTIC, 1.4).affinity(CustomerSegment.WELLNESS, 1.2)
                .affinity(CustomerSegment.FAMILY, 1.1).affinity(CustomerSegment.BUSINESS, 0.9)
                .maxInteriorLevel(2, 3, 3)
                .build();

        BuildingForm receptionLobby = BuildingForm.builder("RECEPTION_LOBBY", "接待大堂", MotherBuildingType.RECEPTION_LOBBY)
                .tier(0).serviceCapacity(0).unitPrice(0, 0, 0).buildCost(5000)
                .formLevelUpCost(2000, 3400).formLevelUpDays(2, 2).baseComfort(30)
                .affinity(CustomerSegment.FAMILY, 1.0).affinity(CustomerSegment.BUSINESS, 1.0)
                .affinity(CustomerSegment.ROMANTIC, 1.0).affinity(CustomerSegment.WELLNESS, 1.0)
                .maxInteriorLevel(2, 3, 3)
                .build();

        BuildingForm teaLibrary = BuildingForm.builder("TEA_LIBRARY", "茶室书吧", MotherBuildingType.TEA_LIBRARY)
                .tier(0).serviceCapacity(15).unitPrice(50, 60, 70).buildCost(4500)
                .formLevelUpCost(1900, 3200).formLevelUpDays(2, 2).baseComfort(38)
                .affinity(CustomerSegment.WELLNESS, 1.4).affinity(CustomerSegment.ROMANTIC, 1.2)
                .affinity(CustomerSegment.FAMILY, 1.0).affinity(CustomerSegment.BUSINESS, 1.0)
                .maxInteriorLevel(2, 3, 3)
                .build();

        BuildingForm cultureWorkshop = BuildingForm.builder("CULTURE_WORKSHOP", "文创工坊", MotherBuildingType.CULTURE_WORKSHOP)
                .tier(0).serviceCapacity(12).unitPrice(55, 65, 80).buildCost(5500)
                .formLevelUpCost(2200, 3600).formLevelUpDays(2, 3).baseComfort(36)
                .affinity(CustomerSegment.FAMILY, 1.3).affinity(CustomerSegment.ROMANTIC, 1.1)
                .affinity(CustomerSegment.WELLNESS, 1.0).affinity(CustomerSegment.BUSINESS, 0.8)
                .maxInteriorLevel(2, 3, 3)
                .build();

        BuildingForm logisticsWarehouse = BuildingForm.builder("LOGISTICS_WAREHOUSE", "后勤库房", MotherBuildingType.LOGISTICS_WAREHOUSE)
                .tier(0).serviceCapacity(0).unitPrice(0, 0, 0).buildCost(3500)
                .formLevelUpCost(1500, 2500).formLevelUpDays(1, 1).baseComfort(20)
                .affinity(CustomerSegment.FAMILY, 0.8).affinity(CustomerSegment.BUSINESS, 0.8)
                .affinity(CustomerSegment.ROMANTIC, 0.8).affinity(CustomerSegment.WELLNESS, 0.8)
                .maxInteriorLevel(2, 3, 3)
                .build();

        BuildingForm outdoorCamp = BuildingForm.builder("OUTDOOR_CAMP", "户外营地", MotherBuildingType.OUTDOOR_CAMP)
                .tier(0).serviceCapacity(25).unitPrice(35, 45, 55).buildCost(3000)
                .formLevelUpCost(1400, 2400).formLevelUpDays(1, 2).baseComfort(34)
                .affinity(CustomerSegment.FAMILY, 1.4).affinity(CustomerSegment.ROMANTIC, 1.2)
                .affinity(CustomerSegment.WELLNESS, 1.1).affinity(CustomerSegment.BUSINESS, 0.6)
                .maxInteriorLevel(2, 3, 3)
                .build();

        BuildingForm parentChildPark = BuildingForm.builder("PARENT_CHILD_PARK", "亲子乐园", MotherBuildingType.PARENT_CHILD_PARK)
                .tier(0).serviceCapacity(35).unitPrice(45, 55, 65).buildCost(6500)
                .formLevelUpCost(2600, 4400).formLevelUpDays(3, 4).baseComfort(42)
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
                familySuite, 3, familySuite, 3, mergedFamilyVilla, 1, 30000, 5));
        mergeRecipes.add(new MergeRecipe("MERGE_BUSINESS_HALL", "商务行政馆（合并）",
                businessSuite, 3, businessSuite, 3, mergedBusinessHall, 1, 32000, 5));
        mergeRecipes.add(new MergeRecipe("MERGE_CROSS_LINE_HALL", "跨线合并：商务行政馆",
                familySuite, 3, businessSuite, 3, mergedBusinessHall, 1, 40000, 6));

        // ===================== 地块（按注册顺序构成解锁链） =====================
        // 规则：每一地块必须先解锁「上一地块」（跨区域同样成立），再达成自身成就条件后解锁；
        // 区域内首个地块解锁即视为该区域解锁。开局仅默认解锁「史东薇尔城」。
        registerPlot(
                new Plot("STORMVEIL_CASTLE", "史东薇尔城", Region.LIMGRAVE, 1, 8, 0,
                        Set.of(MotherBuildingType.STANDARD_ROOM),
                        Set.of(MotherBuildingType.RESTAURANT, MotherBuildingType.VIEWING_PLATFORM),
                        1, List.of(), true),
                new Plot("MISTWOOD_EDGE", "雾林边缘", Region.LIMGRAVE, 2, 8, 100000,
                        Set.of(MotherBuildingType.STANDARD_ROOM),
                        Set.of(MotherBuildingType.RESTAURANT, MotherBuildingType.OUTDOOR_CAMP),
                        2, List.of("ACH_ASSET_10"), false),
                new Plot("AGHEEL_LAKE", "亚基尔湖", Region.LIMGRAVE, 3, 10, 1000000,
                        Set.of(MotherBuildingType.STANDARD_ROOM),
                        Set.of(MotherBuildingType.RESTAURANT, MotherBuildingType.HOT_SPRING_POOL),
                        3, List.of("ACH_ASSET_100", "ACH_GUEST_5000"), false),
                new Plot("CARIA_MANOR", "卡利亚城寨", Region.LIURNIA, 1, 8, 5000000,
                        Set.of(MotherBuildingType.STANDARD_ROOM),
                        Set.of(MotherBuildingType.RESTAURANT, MotherBuildingType.TEA_LIBRARY, MotherBuildingType.VIEWING_PLATFORM),
                        3, List.of("ACH_ASSET_500", "ACH_GUEST_10000", "ACH_STAFF_20"), false),
                new Plot("RAYA_LUCARIA", "雷亚卢卡利亚学院", Region.LIURNIA, 2, 10, 8000000,
                        Set.of(MotherBuildingType.STANDARD_ROOM),
                        Set.of(MotherBuildingType.RESTAURANT, MotherBuildingType.TEA_LIBRARY, MotherBuildingType.CULTURE_WORKSHOP),
                        3, List.of("ACH_BUILDING_20", "ACH_FAMILY_SUITE"), false),
                new Plot("FOUR_BELFRIES", "四钟楼", Region.LIURNIA, 3, 12, 10000000,
                        Set.of(MotherBuildingType.STANDARD_ROOM),
                        Set.of(MotherBuildingType.RESTAURANT, MotherBuildingType.HOT_SPRING_POOL, MotherBuildingType.VIEWING_PLATFORM),
                        3, List.of("ACH_STAFF_50", "ACH_MANAGER_LV3"), false));

        // ===================== 成就 =====================
        registerAchievement(
                // —— 财务：总资产 ——
                new Achievement("ACH_ASSET_10", "总资产 10 万", AchievementCategory.FINANCE,
                        AchievementType.TOTAL_ASSETS, 100000, ""),
                new Achievement("ACH_ASSET_100", "总资产 100 万", AchievementCategory.FINANCE,
                        AchievementType.TOTAL_ASSETS, 1000000, ""),
                new Achievement("ACH_ASSET_500", "总资产 500 万", AchievementCategory.FINANCE,
                        AchievementType.TOTAL_ASSETS, 5000000, ""),
                new Achievement("ACH_ASSET_1000", "总资产 1000 万", AchievementCategory.FINANCE,
                        AchievementType.TOTAL_ASSETS, 10000000, ""),
                new Achievement("ACH_ASSET_10000", "总资产 10000 万", AchievementCategory.FINANCE,
                        AchievementType.TOTAL_ASSETS, 100000000, ""),
                // —— 建筑：建筑数量 ——
                new Achievement("ACH_BUILDING_5", "已建建筑 5 座", AchievementCategory.BUILDING,
                        AchievementType.BUILDING_COUNT, 5, ""),
                new Achievement("ACH_BUILDING_10", "已建建筑 10 座", AchievementCategory.BUILDING,
                        AchievementType.BUILDING_COUNT, 10, ""),
                new Achievement("ACH_BUILDING_20", "已建建筑 20 座", AchievementCategory.BUILDING,
                        AchievementType.BUILDING_COUNT, 20, ""),
                new Achievement("ACH_BUILDING_50", "已建建筑 50 座", AchievementCategory.BUILDING,
                        AchievementType.BUILDING_COUNT, 50, ""),
                new Achievement("ACH_BUILDING_100", "已建建筑 100 座", AchievementCategory.BUILDING,
                        AchievementType.BUILDING_COUNT, 100, ""),
                // —— 客流：累计接待 ——
                new Achievement("ACH_GUEST_100", "累计接待 100 人", AchievementCategory.GUEST,
                        AchievementType.CUMULATIVE_GUESTS, 100, ""),
                new Achievement("ACH_GUEST_1000", "累计接待 1000 人", AchievementCategory.GUEST,
                        AchievementType.CUMULATIVE_GUESTS, 1000, ""),
                new Achievement("ACH_GUEST_5000", "累计接待 5000 人", AchievementCategory.GUEST,
                        AchievementType.CUMULATIVE_GUESTS, 5000, ""),
                new Achievement("ACH_GUEST_10000", "累计接待 10000 人", AchievementCategory.GUEST,
                        AchievementType.CUMULATIVE_GUESTS, 10000, ""),
                new Achievement("ACH_GUEST_100000", "累计接待 100000 人", AchievementCategory.GUEST,
                        AchievementType.CUMULATIVE_GUESTS, 100000, ""),
                // —— 雇佣：员工数量 / 特定岗位等级 ——
                new Achievement("ACH_STAFF_5", "员工总数 5 人", AchievementCategory.STAFF,
                        AchievementType.STAFF_COUNT, 5, ""),
                new Achievement("ACH_STAFF_10", "员工总数 10 人", AchievementCategory.STAFF,
                        AchievementType.STAFF_COUNT, 10, ""),
                new Achievement("ACH_STAFF_20", "员工总数 20 人", AchievementCategory.STAFF,
                        AchievementType.STAFF_COUNT, 20, ""),
                new Achievement("ACH_STAFF_50", "员工总数 50 人", AchievementCategory.STAFF,
                        AchievementType.STAFF_COUNT, 50, ""),
                new Achievement("ACH_STAFF_500", "员工总数 500 人", AchievementCategory.STAFF,
                        AchievementType.STAFF_COUNT, 500, ""),
                new Achievement("ACH_CHEF_LV3", "招聘 1 名 3 级厨师", AchievementCategory.STAFF,
                        AchievementType.POSITION_LEVEL_COUNT, 1, "CHEF_3"),
                new Achievement("ACH_MANAGER_LV3", "招聘 1 名 3 级店长", AchievementCategory.STAFF,
                        AchievementType.POSITION_LEVEL_COUNT, 1, "MANAGER_3"),
                // —— 建筑：特定形态 ——
                new Achievement("ACH_FAMILY_SUITE", "建造 1 座亲子套房", AchievementCategory.BUILDING,
                        AchievementType.FORM_LEVEL_COUNT, 1, "FAMILY_SUITE_1"),
                new Achievement("ACH_BUSINESS_SUITE", "建造 1 座行政套房", AchievementCategory.BUILDING,
                        AchievementType.FORM_LEVEL_COUNT, 1, "BUSINESS_SUITE_1"));

        // ===================== 技能树（DAG，禁止循环依赖） =====================
        registerSkill(
                // —— 无前置（一级） ——
                node("SK_INTERIOR_COST", "内装节流 I", "内装升级费用 −10%", SkillCategory.INTERIOR,
                        List.of(), SkillEffectType.COST_DISCOUNT, SkillEffectKey.INTERIOR_UPGRADE_COST_DISCOUNT, 0.10),
                node("SK_HIRE_COST", "招聘节流 I", "招聘消耗 −10%", SkillCategory.HIRING,
                        List.of(), SkillEffectType.COST_DISCOUNT, SkillEffectKey.HIRE_COST_DISCOUNT, 0.10),
                node("SK_FIRE_COMP", "离职安抚 I", "解雇补偿 −10%", SkillCategory.HIRING,
                        List.of(), SkillEffectType.COST_DISCOUNT, SkillEffectKey.FIRE_COMPENSATION_DISCOUNT, 0.10),
                // —— 二级 ——
                node("SK_EVOLVE_COST", "进化节流 I", "建筑形态进化费用 −10%", SkillCategory.BUILDING,
                        List.of("SK_INTERIOR_COST"), SkillEffectType.COST_DISCOUNT, SkillEffectKey.EVOLVE_COST_DISCOUNT, 0.10),
                node("SK_INTERIOR_COMFORT", "舒适内装 I", "内装舒适度加成 +15%", SkillCategory.INTERIOR,
                        List.of("SK_INTERIOR_COST"), SkillEffectType.MULTIPLIER, SkillEffectKey.INTERIOR_COMFORT_MULT, 0.15),
                node("SK_STAFF_ABILITY", "人力素质 I", "员工能力值 +10%", SkillCategory.HIRING,
                        List.of("SK_HIRE_COST", "SK_FIRE_COMP"), SkillEffectType.MULTIPLIER, SkillEffectKey.STAFF_ABILITY_MULT, 0.10),
                // —— 三级 ——
                node("SK_BUILD_COST", "建造节流 I", "新建建筑费用 −10%", SkillCategory.BUILDING,
                        List.of("SK_INTERIOR_COST", "SK_EVOLVE_COST"), SkillEffectType.COST_DISCOUNT, SkillEffectKey.BUILD_COST_DISCOUNT, 0.10),
                // —— 四级（终点） ——
                node("SK_ROOM_PRICE", "房屋收益溢价 I", "房屋收益基准 +10%", SkillCategory.FINANCE,
                        List.of("SK_EVOLVE_COST", "SK_BUILD_COST"),
                        SkillEffectType.MULTIPLIER, SkillEffectKey.ROOM_PRICE_MULT, 0.10));
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
}
