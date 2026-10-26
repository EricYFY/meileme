package com.meileme.resort.service;

import com.meileme.resort.config.AbilityType;
import com.meileme.resort.config.BuildingForm;
import com.meileme.resort.config.CustomerSegment;
import com.meileme.resort.config.GameConfig;
import com.meileme.resort.config.Plot;
import com.meileme.resort.config.Region;
import com.meileme.resort.config.SkillEffectKey;
import com.meileme.resort.model.BuildingInstance;
import com.meileme.resort.model.DemandResult;
import com.meileme.resort.model.GameState;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 每日客流链路计算。
 *
 * <p><b>客流按大区域独立结算</b>：每个区域拥有独立的每日基础客流与客群占比（见 {@link Region}），
 * 区域内的全部建筑共同竞争本区域客流，互不跨区共享。</p>
 *
 * <p>链路：区域客群比例 → 拆分各客群潜在客流 → 综合本区域全部建筑（形态 / 形态等级 / 内装舒适度）
 * 计算分客群吸引力 → 换算转化率 → 意向客群人数 → 按权重分配至本区域各住宿建筑 →
 * 床位容量截断 → 溢出客流直接流失（不做二次分流）；全程无价格竞争。</p>
 */
@Service
public class DemandService {

    private final ConfigRegistry registry;
    private final ComfortService comfortService;
    private final SkillTreeService skillTree;

    public DemandService(ConfigRegistry registry, ComfortService comfortService, SkillTreeService skillTree) {
        this.registry = registry;
        this.comfortService = comfortService;
        this.skillTree = skillTree;
    }

    public DemandResult compute(GameState state) {
        DemandResult result = new DemandResult();

        double attractMult = skillTree.multiplier(state, SkillEffectKey.SEGMENT_ATTRACTION_MULT);
        double conversionMult = skillTree.multiplier(state, SkillEffectKey.CONVERSION_RATE_MULT);

        List<BuildingInstance> alive = state.aliveBuildings();
        Map<AbilityType, Double> ratios = comfortService.supplyRatios(state);

        // —— ① 各建筑综合舒适度与承载容量 ——
        Map<String, Double> comfortById = new HashMap<>();
        Map<String, Double> capacityById = new HashMap<>();
        for (BuildingInstance b : alive) {
            comfortById.put(b.getId(), comfortService.comfortOf(state, b, ratios));
            capacityById.put(b.getId(), comfortService.capacityOf(state, b));
        }

        // —— ② 按所属区域分组（区域之间客流池独立） ——
        Map<Region, List<BuildingInstance>> buildingsByRegion = new EnumMap<>(Region.class);
        for (BuildingInstance b : alive) {
            Region region = regionOf(b);
            if (region != null) {
                buildingsByRegion.computeIfAbsent(region, k -> new ArrayList<>()).add(b);
            }
        }

        Map<String, Double> potential = emptySegmentMap();
        Map<String, Double> attraction = emptySegmentMap();
        Map<String, Double> conversion = emptySegmentMap();
        Map<String, Double> intent = emptySegmentMap();
        Map<String, Double> served = emptySegmentMap();

        int totalRooms = 0;
        int occupiedRooms = 0;

        List<String> segmentIds = getActiveSegmentIds();

        for (Region region : Region.values()) {
            if (!state.getUnlockedRegions().contains(region)) {
                continue;
            }
            List<BuildingInstance> buildings = buildingsByRegion.getOrDefault(region, List.of());

            List<BuildingInstance> accommodation = new ArrayList<>();
            List<BuildingInstance> support = new ArrayList<>();
            for (BuildingInstance b : buildings) {
                if (b.isAccommodation()) {
                    accommodation.add(b);
                } else {
                    support.add(b);
                }
            }
            totalRooms += accommodation.size();

            // —— ③ 本区域潜在客流 & 吸引力 & 转化率 ——
            Map<String, Double> regionIntent = new LinkedHashMap<>();
            for (String segId : segmentIds) {
                com.meileme.resort.config.RegionConfig rc = registry != null ? registry.region(region.name()) : null;
                double regionPotential = 0;
                if (rc != null) {
                    regionPotential = rc.potentialOf(segId);
                } else {
                    try {
                        regionPotential = region.potentialOf(CustomerSegment.valueOf(segId));
                    } catch (Exception ignored) {}
                }
                potential.merge(segId, regionPotential, Double::sum);

                double attr = 0;
                for (BuildingInstance b : buildings) {
                    BuildingForm f = b.getForm();
                    if (f == null) {
                        continue;
                    }
                    attr += (f.affinityOf(segId) + b.interiorAffinityBonusOf(segId, registry))
                            * comfortById.get(b.getId());
                }
                attr *= attractMult;
                attraction.merge(segId, attr, Double::sum);

                double conv = attr / GameConfig.CONVERSION_DENOMINATOR * conversionMult;
                conv = Math.max(GameConfig.MIN_CONVERSION_RATE, Math.min(GameConfig.MAX_CONVERSION_RATE, conv));

                double regionIntentValue = regionPotential * conv;
                regionIntent.put(segId, regionIntentValue);
                intent.merge(segId, regionIntentValue, Double::sum);
            }

            // —— ④ 权重分配客流至本区域各住宿建筑 ——
            double regionServed = 0;
            for (BuildingInstance b : accommodation) {
                BuildingForm f = b.getForm();
                double comfort = comfortById.get(b.getId());
                double cap = Math.max(0, capacityById.get(b.getId()));

                Map<String, Double> weight = new LinkedHashMap<>();
                for (String segId : segmentIds) {
                    weight.put(segId, (f.affinityOf(segId) + b.interiorAffinityBonusOf(segId, registry))
                            * comfort);
                }

                double totalRaw = 0;
                Map<String, Double> raw = new LinkedHashMap<>();
                for (String segId : segmentIds) {
                    double segTotalWeight = 0;
                    for (BuildingInstance other : accommodation) {
                        BuildingForm of = other.getForm();
                        segTotalWeight += (of.affinityOf(segId) + other.interiorAffinityBonusOf(segId, registry))
                                * comfortById.get(other.getId());
                    }
                    double share = segTotalWeight <= 0 ? 0 : weight.get(segId) / segTotalWeight;
                    double v = regionIntent.getOrDefault(segId, 0d) * share;
                    raw.put(segId, v);
                    totalRaw += v;
                }

                // —— ⑤ 床位容量截断（不满整间亦按整间计价；分配人数 > 0 即该间售出） ——
                double factor = 1.0;
                if (totalRaw > cap) {
                    factor = totalRaw <= 0 ? 0 : cap / totalRaw;
                }
                double servedTotal = 0;
                for (String segId : segmentIds) {
                    double v = raw.getOrDefault(segId, 0d) * factor;
                    served.merge(segId, v, Double::sum);
                    servedTotal += v;
                }
                regionServed += servedTotal;
                int sold = servedTotal > 0 ? 1 : 0;
                occupiedRooms += sold;
                result.getSoldRoomsByBuilding().put(b.getId(), sold);
            }

            // —— ⑥ 配套当日服务人次 = min(本区域入住总人数, 配套服务容量上限含内装) ——
            for (BuildingInstance b : support) {
                double cap = comfortService.supportCapacityOf(state, b);
                double count = Math.min(regionServed, Math.max(0, cap));
                result.getSupportServiceByBuilding().put(b.getId(), count);
            }
        }

        // —— 汇总 ——
        double totalPotential = 0;
        double totalIntent = 0;
        double totalServed = 0;
        for (String segId : segmentIds) {
            double segPot = potential.getOrDefault(segId, 0d);
            double segIntent = intent.getOrDefault(segId, 0d);
            totalPotential += segPot;
            totalIntent += segIntent;
            totalServed += served.getOrDefault(segId, 0d);
            conversion.put(segId, segPot <= 0 ? 0 : Math.min(GameConfig.MAX_CONVERSION_RATE, segIntent / segPot));
        }

        result.setPotentialBySegment(potential);
        result.setAttractionBySegment(attraction);
        result.setConversionBySegment(conversion);
        result.setIntentBySegment(intent);
        result.setServedBySegment(served);
        result.setTotalPotential(totalPotential);
        result.setTotalIntent(totalIntent);
        result.setTotalServed(totalServed);
        result.setOverflowGuests(Math.max(0, totalIntent - totalServed));
        result.setTotalRooms(totalRooms);
        result.setOccupiedRooms(occupiedRooms);
        return result;
    }

    private Region regionOf(BuildingInstance b) {
        Plot plot = registry.plot(b.getPlotId());
        return plot == null ? null : plot.getRegion();
    }

    private List<String> getActiveSegmentIds() {
        if (registry != null && !registry.allSegments().isEmpty()) {
            List<String> ids = new ArrayList<>();
            for (com.meileme.resort.config.SegmentConfig sc : registry.allSegments()) {
                ids.add(sc.getId());
            }
            return ids;
        }
        List<String> ids = new ArrayList<>();
        for (CustomerSegment cs : CustomerSegment.values()) {
            ids.add(cs.name());
        }
        return ids;
    }

    private Map<String, Double> emptySegmentMap() {
        Map<String, Double> map = new LinkedHashMap<>();
        for (String segId : getActiveSegmentIds()) {
            map.put(segId, 0d);
        }
        return map;
    }
}
