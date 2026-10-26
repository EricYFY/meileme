package com.meileme.resort.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.meileme.resort.config.*;
import com.meileme.resort.model.PlotMap;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.*;

/**
 * 数据驱动的配置注册表：全部数值配置统一来源于 default-config.json 与 admin-config.json。
 *
 * <p>代码中零硬编码数值。若存在 admin-config.json 则优先读取；若不存在则读取 default-config.json。
 * 管理员保存时写入 admin-config.json，恢复默认时删除 admin-config.json 并重载默认配置。</p>
 */
@Component
public class ConfigRegistry {

    private static final Logger log = LoggerFactory.getLogger(ConfigRegistry.class);
    private static final String ADMIN_CONFIG_FILE = "admin-config.json";
    private static final String DEFAULT_CONFIG_RESOURCE = "default-config.json";

    private final ObjectMapper objectMapper;

    private final Map<String, BuildingForm> formsById = new LinkedHashMap<>();
    private final Map<MotherBuildingType, String> motherBaseFormMap = new EnumMap<>(MotherBuildingType.class);
    private final List<MergeRecipe> mergeRecipes = new ArrayList<>();
    private final Map<String, Plot> plotsById = new LinkedHashMap<>();
    private final Map<String, PlotMap> plotMaps = new LinkedHashMap<>();
    private final Map<String, Achievement> achievementsById = new LinkedHashMap<>();
    private final Map<String, SkillNode> skillsById = new LinkedHashMap<>();
    private final Map<String, PositionConfig> positionsById = new LinkedHashMap<>();
    private final Map<String, InteriorConfig> interiorsById = new LinkedHashMap<>();
    private final Map<String, RegionConfig> regionsById = new LinkedHashMap<>();
    private final Map<String, SegmentConfig> segmentsById = new LinkedHashMap<>();

    private Map<String, Object> rawConfigMap = new LinkedHashMap<>();
    private boolean isCustomConfig = false;

    public ConfigRegistry(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @PostConstruct
    public synchronized void init() {
        loadConfig();
    }

    /** 智能定位 admin-config.json 文件位置（兼容根目录与 backendjava 目录） */
    public File resolveAdminConfigFile() {
        File f = new File(ADMIN_CONFIG_FILE);
        if (f.exists()) return f;
        File sub = new File("backendjava", ADMIN_CONFIG_FILE);
        if (sub.exists()) return sub;
        if (new File("backendjava").isDirectory()) {
            return sub;
        }
        return f;
    }

    /** 读取配置（优先 admin-config.json，不存在则回退 default-config.json） */
    public synchronized void loadConfig() {
        File adminFile = resolveAdminConfigFile();
        if (adminFile.exists() && adminFile.isFile() && adminFile.length() > 0) {
            try {
                String json = Files.readString(adminFile.toPath(), StandardCharsets.UTF_8);
                Map<String, Object> root = objectMapper.readValue(json, new TypeReference<>() {});
                parseAndApply(root);
                isCustomConfig = true;
                log.info("已成功载入管理员自定义配置: {}", adminFile.getAbsolutePath());
                return;
            } catch (Exception e) {
                log.error("读取 admin-config.json 失败，将回退载入默认配置", e);
            }
        }

        // 读取 default-config.json
        try {
            ClassPathResource res = new ClassPathResource(DEFAULT_CONFIG_RESOURCE);
            try (InputStream in = res.getInputStream()) {
                String json = new String(in.readAllBytes(), StandardCharsets.UTF_8);
                Map<String, Object> root = objectMapper.readValue(json, new TypeReference<>() {});
                parseAndApply(root);
                isCustomConfig = false;
                log.info("已成功载入出厂默认配置: {}", DEFAULT_CONFIG_RESOURCE);
            }
        } catch (Exception e) {
            log.error("加载 default-config.json 严重失败！", e);
        }
    }

    /** 解析并应用配置到运行时内存实体 */
    @SuppressWarnings("unchecked")
    private void parseAndApply(Map<String, Object> root) {
        this.rawConfigMap = root;

        // 1. 全局参数
        Map<String, Object> global = (Map<String, Object>) root.get("global");
        if (global != null) {
            GameConfig.applyGlobalConfig(global);
        }

        // 2. 母体建筑与基础形态映射
        motherBaseFormMap.clear();
        List<Map<String, Object>> mothersData = (List<Map<String, Object>>) root.get("mothers");
        if (mothersData != null) {
            for (Map<String, Object> m : mothersData) {
                String mid = (String) m.get("id");
                String baseFormId = (String) m.get("baseFormId");
                if (mid != null) {
                    try {
                        MotherBuildingType mType = MotherBuildingType.valueOf(mid);
                        motherBaseFormMap.put(mType, (baseFormId != null && !baseFormId.isBlank()) ? baseFormId : mid);
                    } catch (Exception ignored) {}
                }
            }
        }

        // 3. 清空并重建形态（完全无母体依赖）
        formsById.clear();
        List<Map<String, Object>> formsData = (List<Map<String, Object>>) root.get("buildingForms");
        if (formsData != null) {
            // 两遍构建：第一遍构建无 evolveFrom 实体，第二遍处理 evolveFrom 引用
            Map<String, BuildingForm.Builder> builders = new LinkedHashMap<>();
            Map<String, String> evolveFromMap = new LinkedHashMap<>();

            for (Map<String, Object> m : formsData) {
                String id = (String) m.get("id");
                String displayName = (String) m.get("displayName");
                BuildingCategory category = BuildingCategory.ACCOMMODATION;
                if (m.containsKey("category") && m.get("category") != null) {
                    try {
                        category = BuildingCategory.valueOf(m.get("category").toString());
                    } catch (Exception ignored) {}
                }
                BuildingForm.Builder b = BuildingForm.builder(id, displayName, category);

                if (m.containsKey("line")) b.line(EvolutionLine.valueOf((String) m.get("line")));
                if (m.containsKey("tier")) b.tier(((Number) m.get("tier")).intValue());
                if (m.containsKey("capacityPerRoom")) b.capacityPerRoom(((Number) m.get("capacityPerRoom")).doubleValue());
                if (m.containsKey("serviceCapacity")) b.serviceCapacity(((Number) m.get("serviceCapacity")).doubleValue());
                if (m.containsKey("buildCost")) b.buildCost(((Number) m.get("buildCost")).doubleValue());
                if (m.containsKey("evolveCost")) b.evolveCost(((Number) m.get("evolveCost")).doubleValue());
                if (m.containsKey("evolveDays")) b.evolveDays(((Number) m.get("evolveDays")).intValue());
                if (m.containsKey("baseComfort")) b.baseComfort(((Number) m.get("baseComfort")).doubleValue());

                if (m.containsKey("pricePerRoom")) {
                    List<Number> pr = (List<Number>) m.get("pricePerRoom");
                    if (pr != null && !pr.isEmpty()) {
                        b.pricePerRoom(pr.stream().mapToDouble(Number::doubleValue).toArray());
                    }
                }
                if (m.containsKey("unitPrice")) {
                    List<Number> up = (List<Number>) m.get("unitPrice");
                    if (up != null && !up.isEmpty()) {
                        b.unitPrice(up.stream().mapToDouble(Number::doubleValue).toArray());
                    }
                }
                if (m.containsKey("formLevelUpCost")) {
                    List<Number> lc = (List<Number>) m.get("formLevelUpCost");
                    if (lc != null && !lc.isEmpty()) {
                        b.formLevelUpCost(lc.stream().mapToDouble(Number::doubleValue).toArray());
                    }
                }
                if (m.containsKey("formLevelUpDays")) {
                    List<Number> ld = (List<Number>) m.get("formLevelUpDays");
                    if (ld != null && !ld.isEmpty()) {
                        b.formLevelUpDays(ld.stream().mapToInt(Number::intValue).toArray());
                    }
                }
                if (m.containsKey("maxInteriorLevel")) {
                    List<Number> mi = (List<Number>) m.get("maxInteriorLevel");
                    if (mi != null && !mi.isEmpty()) {
                        b.maxInteriorLevel(mi.stream().mapToInt(Number::intValue).toArray());
                    }
                }
                if (m.containsKey("affinities")) {
                    Map<String, Number> aff = (Map<String, Number>) m.get("affinities");
                    if (aff != null) {
                        for (Map.Entry<String, Number> e : aff.entrySet()) {
                            try {
                                b.affinity(e.getKey(), e.getValue().doubleValue());
                            } catch (Exception ignored) {}
                        }
                    }
                }
                if (m.containsKey("abilityDemands")) {
                    Map<String, Number> dem = (Map<String, Number>) m.get("abilityDemands");
                    if (dem != null) {
                        for (Map.Entry<String, Number> e : dem.entrySet()) {
                            try {
                                b.abilityDemand(AbilityType.valueOf(e.getKey()), e.getValue().doubleValue());
                            } catch (Exception ignored) {}
                        }
                    }
                }

                if (m.containsKey("availableInteriors")) {
                    List<String> avi = (List<String>) m.get("availableInteriors");
                    if (avi != null) {
                        b.availableInteriors(avi);
                    }
                }

                if (m.containsKey("capacityInterior")) {
                    Object ci = m.get("capacityInterior");
                    b.capacityInterior(ci != null && !String.valueOf(ci).isBlank() ? String.valueOf(ci) : null);
                } else {
                    if (category == BuildingCategory.ACCOMMODATION) {
                        b.capacityInterior("BED");
                    } else if (id != null && id.toUpperCase().contains("RESTAURANT")) {
                        b.capacityInterior("DINING_TABLE");
                    } else if (id != null && id.toUpperCase().contains("HOT_SPRING")) {
                        b.capacityInterior("HOT_SPRING_TUB");
                    }
                }

                builders.put(id, b);
                String from = (String) m.get("evolveFrom");
                if (from != null && !from.isBlank()) {
                    evolveFromMap.put(id, from);
                }
            }

            // 结合 evolutionLines 动态注入进化路线关系
            List<Map<String, Object>> evoLines = (List<Map<String, Object>>) root.get("evolutionLines");
            if (evoLines != null) {
                for (Map<String, Object> evo : evoLines) {
                    String srcId = (String) evo.get("sourceFormId");
                    String tgtId = (String) evo.get("targetFormId");
                    if (tgtId != null && builders.containsKey(tgtId)) {
                        if (srcId != null && !srcId.isBlank()) {
                            evolveFromMap.put(tgtId, srcId);
                        }
                        BuildingForm.Builder tb = builders.get(tgtId);
                        if (evo.containsKey("cost")) {
                            tb.evolveCost(((Number) evo.get("cost")).doubleValue());
                        }
                        if (evo.containsKey("days")) {
                            tb.evolveDays(((Number) evo.get("days")).intValue());
                        }
                    }
                }
            }

            // 拓扑/链式顺序构建
            for (Map.Entry<String, BuildingForm.Builder> entry : builders.entrySet()) {
                String id = entry.getKey();
                String fromId = evolveFromMap.get(id);
                if (fromId != null && formsById.containsKey(fromId)) {
                    entry.getValue().evolveFrom(formsById.get(fromId));
                }
                formsById.put(id, entry.getValue().build());
            }
        }

        // 3. 合并配方
        mergeRecipes.clear();
        List<Map<String, Object>> recipesData = (List<Map<String, Object>>) root.get("mergeRecipes");
        if (recipesData != null) {
            for (Map<String, Object> m : recipesData) {
                String id = (String) m.get("id");
                String displayName = (String) m.get("displayName");
                BuildingForm formA = formsById.get((String) m.get("sourceFormA"));
                int lvA = ((Number) m.get("sourceLevelA")).intValue();
                BuildingForm formB = formsById.get((String) m.get("sourceFormB"));
                int lvB = ((Number) m.get("sourceLevelB")).intValue();
                BuildingForm resultForm = formsById.get((String) m.get("resultForm"));
                int resLv = m.containsKey("resultLevel") ? ((Number) m.get("resultLevel")).intValue() : 1;
                double cost = ((Number) m.get("mergeCost")).doubleValue();
                int days = ((Number) m.get("mergeDays")).intValue();

                if (formA != null && formB != null && resultForm != null) {
                    mergeRecipes.add(new MergeRecipe(id, displayName, formA, lvA, formB, lvB, resultForm, resLv, cost, days));
                }
            }
        }

        // 4. 地块与地图
        plotsById.clear();
        plotMaps.clear();
        List<Map<String, Object>> plotsData = (List<Map<String, Object>>) root.get("plots");
        if (plotsData != null) {
            int mapIndex = 0;
            for (Map<String, Object> m : plotsData) {
                String id = (String) m.get("id");
                String displayName = (String) m.get("displayName");
                Region region = Region.valueOf((String) m.get("region"));
                int order = ((Number) m.get("orderInRegion")).intValue();
                int width = ((Number) m.get("width")).intValue();
                int height = ((Number) m.get("height")).intValue();
                int buildable = ((Number) m.get("buildableCellCount")).intValue();
                int limit = ((Number) m.get("buildLimit")).intValue();
                double cost = ((Number) m.get("unlockCost")).doubleValue();

                Set<MotherBuildingType> accomList = new HashSet<>();
                List<String> accNames = (List<String>) m.get("accommodationWhitelist");
                if (accNames != null) {
                    for (String s : accNames) {
                        try { accomList.add(MotherBuildingType.valueOf(s)); } catch (Exception ignored) {}
                    }
                }

                Set<MotherBuildingType> suppList = new HashSet<>();
                List<String> suppNames = (List<String>) m.get("supportWhitelist");
                if (suppNames != null) {
                    for (String s : suppNames) {
                        try { suppList.add(MotherBuildingType.valueOf(s)); } catch (Exception ignored) {}
                    }
                }

                List<String> allowedMothers = (List<String>) m.get("allowedMothers");
                if (allowedMothers != null) {
                    for (String s : allowedMothers) {
                        try {
                            MotherBuildingType mt = MotherBuildingType.valueOf(s);
                            if (mt.getCategory() == BuildingCategory.ACCOMMODATION) accomList.add(mt);
                            else suppList.add(mt);
                        } catch (Exception ignored) {}
                    }
                }

                int evolveTierCap = ((Number) m.get("evolveTierCap")).intValue();
                List<String> achIds = (List<String>) m.get("unlockAchievementIds");
                if (achIds == null) achIds = List.of();
                boolean byDefault = Boolean.TRUE.equals(m.get("unlockedByDefault"));

                Plot p = new Plot(id, displayName, region, order, width, height, buildable, limit,
                        cost, accomList, suppList, evolveTierCap, achIds, byDefault);
                plotsById.put(id, p);

                plotMaps.put(id, MapGenerator.generate(p, (int) GameConfig.MAP_SEED + mapIndex * 31));
                mapIndex++;
            }
        }

        // 5. 成就
        achievementsById.clear();
        List<Map<String, Object>> achsData = (List<Map<String, Object>>) root.get("achievements");
        if (achsData != null) {
            for (Map<String, Object> m : achsData) {
                String id = (String) m.get("id");
                String displayName = (String) m.get("displayName");
                AchievementType type = AchievementType.valueOf((String) m.get("type"));
                AchievementCategory cat;
                try {
                    cat = AchievementCategory.valueOf((String) m.get("category"));
                } catch (Exception e) {
                    if (type == AchievementType.TOTAL_ASSETS || type == AchievementType.CUMULATIVE_INCOME) cat = AchievementCategory.FINANCE;
                    else if (type == AchievementType.BUILDING_COUNT || type == AchievementType.FORM_LEVEL_COUNT) cat = AchievementCategory.BUILDING;
                    else if (type == AchievementType.CUMULATIVE_GUESTS || type == AchievementType.SEGMENT_GUESTS) cat = AchievementCategory.GUEST;
                    else cat = AchievementCategory.STAFF;
                }
                double target = m.containsKey("targetValue")
                        ? ((Number) m.get("targetValue")).doubleValue()
                        : (m.containsKey("target") ? ((Number) m.get("target")).doubleValue() : 0.0);
                String param = m.containsKey("param") ? (String) m.get("param") : "";
                int rewardSkillPoints = m.containsKey("rewardSkillPoints") ? ((Number) m.get("rewardSkillPoints")).intValue() : 1;
                achievementsById.put(id, new Achievement(id, displayName, cat, type, target, param, rewardSkillPoints));
            }
        }

        // 6. 技能树
        skillsById.clear();
        List<Map<String, Object>> skillsData = (List<Map<String, Object>>) root.get("skills");
        if (skillsData != null) {
            for (Map<String, Object> m : skillsData) {
                String id = (String) m.get("id");
                String displayName = (String) m.get("displayName");
                String description = (String) m.get("description");
                SkillEffectType type = SkillEffectType.valueOf((String) m.get("effectType"));
                SkillEffectKey key = SkillEffectKey.valueOf((String) m.get("effectKey"));
                SkillCategory cat;
                try {
                    cat = SkillCategory.valueOf((String) m.get("category"));
                } catch (Exception e) {
                    if (key == SkillEffectKey.INTERIOR_UPGRADE_COST_DISCOUNT || key == SkillEffectKey.INTERIOR_COMFORT_MULT) cat = SkillCategory.INTERIOR;
                    else if (key == SkillEffectKey.HIRE_COST_DISCOUNT || key == SkillEffectKey.FIRE_COMPENSATION_DISCOUNT || key == SkillEffectKey.STAFF_ABILITY_MULT) cat = SkillCategory.HIRING;
                    else if (key == SkillEffectKey.BUILD_COST_DISCOUNT || key == SkillEffectKey.EVOLVE_COST_DISCOUNT) cat = SkillCategory.BUILDING;
                    else if (key == SkillEffectKey.ROOM_PRICE_MULT || key == SkillEffectKey.SUPPORT_PRICE_MULT) cat = SkillCategory.FINANCE;
                    else cat = SkillCategory.BUILDING;
                }
                List<String> prereq = (List<String>) m.get("prerequisites");
                if (prereq == null) prereq = List.of();
                double val = ((Number) m.get("value")).doubleValue();
                int costPoints = m.containsKey("costPoints") ? ((Number) m.get("costPoints")).intValue()
                        : (m.containsKey("skillPointsCost") ? ((Number) m.get("skillPointsCost")).intValue() : 1);
                skillsById.put(id, new SkillNode(id, displayName, description, cat, prereq, type, key, val, costPoints));
            }
        }

        // 7. 岗位配置 (PositionConfig)
        positionsById.clear();
        List<Map<String, Object>> posData = (List<Map<String, Object>>) root.get("positions");
        if (posData != null) {
            for (Map<String, Object> m : posData) {
                String id = (String) m.get("id");
                String displayName = (String) m.get("displayName");
                List<AbilityType> abilities = new ArrayList<>();
                List<String> abs = (List<String>) m.get("abilities");
                if (abs != null) {
                    for (String aStr : abs) {
                        try { abilities.add(AbilityType.valueOf(aStr)); } catch (Exception ignored) {}
                    }
                }
                Map<AbilityType, double[]> abilityOutputs = new EnumMap<>(AbilityType.class);
                Map<String, Object> aoMap = (Map<String, Object>) m.get("abilityOutputs");
                if (aoMap != null) {
                    for (Map.Entry<String, Object> entry : aoMap.entrySet()) {
                        try {
                            AbilityType at = AbilityType.valueOf(entry.getKey());
                            if (entry.getValue() instanceof List<?> list) {
                                double[] arr = list.stream().mapToDouble(n -> ((Number) n).doubleValue()).toArray();
                                abilityOutputs.put(at, arr);
                            }
                        } catch (Exception ignored) {}
                    }
                }
                double[] abilityValueByLevel = null;
                if (m.containsKey("abilityValueByLevel") && m.get("abilityValueByLevel") instanceof List<?> list) {
                    abilityValueByLevel = list.stream().mapToDouble(n -> ((Number) n).doubleValue()).toArray();
                } else if (m.containsKey("abilityValues") && m.get("abilityValues") instanceof List<?> list) {
                    abilityValueByLevel = list.stream().mapToDouble(n -> ((Number) n).doubleValue()).toArray();
                } else if (!abilityOutputs.isEmpty()) {
                    abilityValueByLevel = abilityOutputs.values().iterator().next();
                }
                double hireCost = m.containsKey("hireCost") ? ((Number) m.get("hireCost")).doubleValue() : 400.0;
                int[] upgradeDaysByLevel = null;
                if (m.containsKey("upgradeDaysByLevel") && m.get("upgradeDaysByLevel") instanceof List<?> list) {
                    upgradeDaysByLevel = list.stream().mapToInt(n -> ((Number) n).intValue()).toArray();
                } else {
                    int d1 = m.containsKey("upgradeDaysLv1ToLv2") ? ((Number) m.get("upgradeDaysLv1ToLv2")).intValue() : 10;
                    int d2 = m.containsKey("upgradeDaysLv2ToLv3") ? ((Number) m.get("upgradeDaysLv2ToLv3")).intValue() : 20;
                    upgradeDaysByLevel = new int[]{d1, d2};
                }
                int[] trainDaysByLevel = null;
                if (m.containsKey("trainDaysByLevel") && m.get("trainDaysByLevel") instanceof List<?> list) {
                    trainDaysByLevel = list.stream().mapToInt(n -> ((Number) n).intValue()).toArray();
                } else {
                    trainDaysByLevel = new int[]{1, 2};
                }
                double[] trainCostByLevel = null;
                if (m.containsKey("trainCostByLevel") && m.get("trainCostByLevel") instanceof List<?> list) {
                    trainCostByLevel = list.stream().mapToDouble(n -> ((Number) n).doubleValue()).toArray();
                } else {
                    trainCostByLevel = new double[]{Math.round(hireCost * 0.6), Math.round(hireCost * 1.2)};
                }
                double compensationBase = m.containsKey("compensationBase") ? ((Number) m.get("compensationBase")).doubleValue() : 600.0;

                PositionConfig pc = PositionConfig.builder()
                        .id(id)
                        .displayName(displayName)
                        .abilities(abilities)
                        .abilityOutputs(abilityOutputs)
                        .abilityValueByLevel(abilityValueByLevel)
                        .hireCost(hireCost)
                        .upgradeDaysByLevel(upgradeDaysByLevel)
                        .trainDaysByLevel(trainDaysByLevel)
                        .trainCostByLevel(trainCostByLevel)
                        .compensationBase(compensationBase)
                        .build();
                positionsById.put(id, pc);
            }
        }

        // 8. 内装配置 (InteriorConfig)
        interiorsById.clear();
        List<Map<String, Object>> interiorData = (List<Map<String, Object>>) root.get("interiors");
        if (interiorData != null) {
            for (Map<String, Object> m : interiorData) {
                String id = (String) m.get("id");
                String displayName = (String) m.get("displayName");
                InteriorCategory category = InteriorCategory.NORMAL;
                if (m.containsKey("category")) {
                    try { category = InteriorCategory.valueOf(m.get("category").toString()); } catch (Exception ignored) {}
                }
                double comfortPerLevel = m.containsKey("comfortPerLevel") ? ((Number) m.get("comfortPerLevel")).doubleValue() : 2.0;
                double capacityPerLevel = m.containsKey("capacityPerLevel") ? ((Number) m.get("capacityPerLevel")).doubleValue() : 0.0;
                double revenueBonusPerLevel = m.containsKey("revenueBonusPerLevel") ? ((Number) m.get("revenueBonusPerLevel")).doubleValue() : 0.02;

                Map<String, Double> segmentAffinity = new LinkedHashMap<>();
                Map<String, Object> saMap = (Map<String, Object>) m.get("segmentAffinityPerLevel");
                if (saMap != null) {
                    for (Map.Entry<String, Object> entry : saMap.entrySet()) {
                        try {
                            segmentAffinity.put(entry.getKey(), ((Number) entry.getValue()).doubleValue());
                        } catch (Exception ignored) {}
                    }
                }
                double[] upgradeCostByLevel = null;
                if (m.containsKey("upgradeCostByLevel") && m.get("upgradeCostByLevel") instanceof List<?> list) {
                    upgradeCostByLevel = list.stream().mapToDouble(n -> ((Number) n).doubleValue()).toArray();
                }
                int[] upgradeDaysByLevel = null;
                if (m.containsKey("upgradeDaysByLevel") && m.get("upgradeDaysByLevel") instanceof List<?> list) {
                    upgradeDaysByLevel = list.stream().mapToInt(n -> ((Number) n).intValue()).toArray();
                }
                Set<MotherBuildingType> applicableMothers = new HashSet<>();
                List<String> amList = (List<String>) m.get("applicableMothers");
                if (amList != null) {
                    for (String s : amList) {
                        try { applicableMothers.add(MotherBuildingType.valueOf(s)); } catch (Exception ignored) {}
                    }
                }

                InteriorConfig ic = InteriorConfig.builder()
                        .id(id)
                        .displayName(displayName)
                        .category(category)
                        .comfortPerLevel(comfortPerLevel)
                        .capacityPerLevel(capacityPerLevel)
                        .revenueBonusPerLevel(revenueBonusPerLevel)
                        .segmentAffinityPerLevel(segmentAffinity)
                        .upgradeCostByLevel(upgradeCostByLevel)
                        .upgradeDaysByLevel(upgradeDaysByLevel)
                        .applicableMothers(applicableMothers)
                        .build();
                interiorsById.put(id, ic);
            }
        }

        // 9. 大区域配置 (RegionConfig)
        regionsById.clear();
        List<Map<String, Object>> regData = (List<Map<String, Object>>) root.get("regions");
        if (regData != null) {
            for (Map<String, Object> m : regData) {
                String id = (String) m.get("id");
                String displayName = (String) m.get("displayName");
                double baseDailyFlow = m.containsKey("baseDailyFlow") ? ((Number) m.get("baseDailyFlow")).doubleValue() : 100.0;
                Map<String, Double> segmentRatios = new LinkedHashMap<>();
                Map<String, Object> srMap = (Map<String, Object>) m.get("segmentRatios");
                if (srMap != null) {
                    for (Map.Entry<String, Object> entry : srMap.entrySet()) {
                        try {
                            segmentRatios.put(entry.getKey(), ((Number) entry.getValue()).doubleValue());
                        } catch (Exception ignored) {}
                    }
                }
                RegionConfig rc = RegionConfig.builder()
                        .id(id)
                        .displayName(displayName)
                        .baseDailyFlow(baseDailyFlow)
                        .segmentRatios(segmentRatios)
                        .build();
                regionsById.put(id, rc);
            }
        }

        // 10. 客群配置 (SegmentConfig)
        segmentsById.clear();
        List<Map<String, Object>> segData = (List<Map<String, Object>>) root.get("segments");
        if (segData != null && !segData.isEmpty()) {
            for (Map<String, Object> m : segData) {
                String id = (String) m.get("id");
                String displayName = (String) m.get("displayName");
                String description = (String) m.get("description");
                String color = (String) m.get("color");
                String icon = (String) m.get("icon");
                if (id != null && !id.isBlank()) {
                    SegmentConfig sc = SegmentConfig.builder()
                            .id(id)
                            .displayName(displayName != null ? displayName : id)
                            .description(description != null ? description : "")
                            .color(color != null ? color : "#8ab4ff")
                            .icon(icon)
                            .build();
                    segmentsById.put(id, sc);
                }
            }
        } else {
            for (CustomerSegment cs : CustomerSegment.values()) {
                segmentsById.put(cs.name(), SegmentConfig.builder()
                        .id(cs.name())
                        .displayName(cs.getDisplayName())
                        .description("")
                        .build());
            }
        }
    }

    /** 管理员保存新配置：写入 admin-config.json 并重载生效 */
    public synchronized void saveAdminConfig(Map<String, Object> newConfigMap) throws Exception {
        String json = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(newConfigMap);
        File adminFile = resolveAdminConfigFile();
        Files.writeString(adminFile.toPath(), json, StandardCharsets.UTF_8);

        // 兼容性双写：确保项目根目录与 backendjava 目录下均同步生成 admin-config.json
        try {
            File fRoot = new File(ADMIN_CONFIG_FILE);
            Files.writeString(fRoot.toPath(), json, StandardCharsets.UTF_8);
            File fSub = new File("backendjava", ADMIN_CONFIG_FILE);
            if (fSub.getParentFile() != null && fSub.getParentFile().isDirectory()) {
                Files.writeString(fSub.toPath(), json, StandardCharsets.UTF_8);
            }
        } catch (Exception ignored) {}

        parseAndApply(newConfigMap);
        this.isCustomConfig = true;
        log.info("管理员配置已成功写入 {} 并热更新完毕", adminFile.getAbsolutePath());
    }

    /** 管理员恢复默认配置：删除 admin-config.json 并重新载入 default-config.json */
    public synchronized void resetToDefault() throws Exception {
        File f1 = new File(ADMIN_CONFIG_FILE);
        if (f1.exists()) {
            boolean d1 = f1.delete();
            log.info("已删除自定义配置文件 {}: {}", f1.getAbsolutePath(), d1);
        }
        File f2 = new File("backendjava", ADMIN_CONFIG_FILE);
        if (f2.exists()) {
            boolean d2 = f2.delete();
            log.info("已删除自定义配置文件 {}: {}", f2.getAbsolutePath(), d2);
        }
        loadConfig();
    }

    // ===================== 查询接口 =====================

    public Map<String, Object> getRawConfigMap() {
        return rawConfigMap;
    }

    public boolean isCustomConfig() {
        return isCustomConfig;
    }

    public BuildingForm form(String id) {
        return id == null ? null : formsById.get(id);
    }

    public List<BuildingForm> allForms() {
        return Collections.unmodifiableList(new ArrayList<>(formsById.values()));
    }

    public BuildingForm baseFormOf(MotherBuildingType mother) {
        if (mother == null) return null;
        String targetId = motherBaseFormMap.get(mother);
        if (targetId != null && formsById.containsKey(targetId)) {
            return formsById.get(targetId);
        }
        if (formsById.containsKey(mother.name())) {
            return formsById.get(mother.name());
        }
        for (BuildingForm f : formsById.values()) {
            if (f.getTier() == 0 && f.getCategory() == mother.getCategory()) {
                return f;
            }
        }
        return null;
    }

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
        if (id == null) return null;
        for (MergeRecipe r : mergeRecipes) {
            if (r.getId().equals(id)) return r;
        }
        return null;
    }

    public MergeRecipe matchRecipe(BuildingForm formA, int levelA, BuildingForm formB, int levelB) {
        for (MergeRecipe r : mergeRecipes) {
            if (r.matches(formA, levelA, formB, levelB)) return r;
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

    public List<PlotMap> allPlotMaps() {
        return Collections.unmodifiableList(new ArrayList<>(plotMaps.values()));
    }

    public PlotMap plotMap(String id) {
        return id == null ? null : plotMaps.get(id);
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

    public PositionConfig position(String id) {
        return id == null ? null : positionsById.get(id);
    }

    public PositionConfig getPosition(String id) {
        return position(id);
    }

    public void registerPosition(PositionConfig p) {
        if (p != null && p.getId() != null) {
            positionsById.put(p.getId(), p);
        }
    }

    public List<PositionConfig> allPositions() {
        return Collections.unmodifiableList(new ArrayList<>(positionsById.values()));
    }

    public InteriorConfig interior(String id) {
        return id == null ? null : interiorsById.get(id);
    }

    public InteriorConfig getInterior(String id) {
        return interior(id);
    }

    public List<InteriorConfig> allInteriors() {
        return Collections.unmodifiableList(new ArrayList<>(interiorsById.values()));
    }

    public RegionConfig region(String id) {
        return id == null ? null : regionsById.get(id);
    }

    public RegionConfig getRegion(String id) {
        return region(id);
    }

    public List<RegionConfig> allRegions() {
        return Collections.unmodifiableList(new ArrayList<>(regionsById.values()));
    }

    public SegmentConfig segment(String id) {
        return id == null ? null : segmentsById.get(id);
    }

    public SegmentConfig getSegment(String id) {
        return segment(id);
    }

    public List<SegmentConfig> allSegments() {
        return Collections.unmodifiableList(new ArrayList<>(segmentsById.values()));
    }

    public void registerSegment(SegmentConfig sc) {
        if (sc != null && sc.getId() != null) {
            segmentsById.put(sc.getId(), sc);
        }
    }
}
