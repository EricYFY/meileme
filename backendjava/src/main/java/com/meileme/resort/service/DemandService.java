package com.meileme.resort.service;

import com.meileme.resort.config.BuildingForm;
import com.meileme.resort.config.CustomerSegment;
import com.meileme.resort.config.GameConfig;
import com.meileme.resort.config.ScenicConfig;
import com.meileme.resort.config.SkillEffectKey;
import com.meileme.resort.model.BuildingInstance;
import com.meileme.resort.model.DemandResult;
import com.meileme.resort.model.GameState;
import org.springframework.stereotype.Service;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * 每日客流链路计算。
 *
 * <p>链路：景区固定客群比例 → 拆分各客群潜在客流 → 综合全部建筑（形态 / 形态等级 / 内装舒适度）
 * 计算分客群吸引力 → 换算转化率 → 意向客群人数 → 按权重分配至各住宿建筑 →
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
        ScenicConfig scenic = registry.scenic();

        double attractMult = skillTree.multiplier(state, SkillEffectKey.SEGMENT_ATTRACTION_MULT);
        double conversionMult = skillTree.multiplier(state, SkillEffectKey.CONVERSION_RATE_MULT);

        List<BuildingInstance> alive = state.aliveBuildings();
        Map<com.meileme.resort.config.AbilityType, Double> ratios = comfortService.supplyRatios(state);

        // —— ① 各建筑综合舒适度 ——
        Map<String, Double> comfortById = new java.util.HashMap<>();
        Map<String, Double> capacityById = new java.util.HashMap<>();
        for (BuildingInstance b : alive) {
            comfortById.put(b.getId(), comfortService.comfortOf(state, b, ratios));
            capacityById.put(b.getId(), comfortService.capacityOf(state, b));
        }

        Map<CustomerSegment, Double> potential = new EnumMap<>(CustomerSegment.class);
        Map<CustomerSegment, Double> attraction = new EnumMap<>(CustomerSegment.class);
        Map<CustomerSegment, Double> conversion = new EnumMap<>(CustomerSegment.class);
        Map<CustomerSegment, Double> intent = new EnumMap<>(CustomerSegment.class);
        Map<CustomerSegment, Double> served = new EnumMap<>(CustomerSegment.class);

        for (CustomerSegment seg : CustomerSegment.values()) {
            potential.put(seg, 0d);
            attraction.put(seg, 0d);
            conversion.put(seg, 0d);
            intent.put(seg, 0d);
            served.put(seg, 0d);
        }

        // —— ② 潜在客流 & 吸引力 ——
        for (CustomerSegment seg : CustomerSegment.values()) {
            potential.put(seg, scenic.potentialOf(seg));
            double attr = 0;
            for (BuildingInstance b : alive) {
                BuildingForm f = b.getForm();
                if (f == null) {
                    continue;
                }
                double comfort = comfortById.get(b.getId());
                attr += f.affinityOf(seg) * comfort * f.levelWeightOf(b.getFormLevel());
            }
            attr *= attractMult;
            attraction.put(seg, attr);

            double conv = attr / GameConfig.CONVERSION_DENOMINATOR * conversionMult;
            conv = Math.max(GameConfig.MIN_CONVERSION_RATE, Math.min(GameConfig.MAX_CONVERSION_RATE, conv));
            conversion.put(seg, conv);
            intent.put(seg, potential.get(seg) * conv);
        }

        // —— ③ 权重分配客流至各住宿建筑 ——
        List<BuildingInstance> accommodation = state.aliveAccommodationBuildings();
        result.setTotalRooms(accommodation.size());

        for (BuildingInstance b : accommodation) {
            BuildingForm f = b.getForm();
            double comfort = comfortById.get(b.getId());
            double cap = Math.max(0, capacityById.get(b.getId()));

            // 该建筑各客群权重
            Map<CustomerSegment, Double> weight = new EnumMap<>(CustomerSegment.class);
            for (CustomerSegment seg : CustomerSegment.values()) {
                weight.put(seg, f.affinityOf(seg) * comfort * f.levelWeightOf(b.getFormLevel()));
            }

            double totalRaw = 0;
            Map<CustomerSegment, Double> raw = new EnumMap<>(CustomerSegment.class);
            for (CustomerSegment seg : CustomerSegment.values()) {
                double segTotalWeight = 0;
                for (BuildingInstance other : accommodation) {
                    BuildingForm of = other.getForm();
                    segTotalWeight += of.affinityOf(seg) * comfortById.get(other.getId())
                            * of.levelWeightOf(other.getFormLevel());
                }
                double share = segTotalWeight <= 0 ? 0 : weight.get(seg) / segTotalWeight;
                double v = intent.get(seg) * share;
                raw.put(seg, v);
                totalRaw += v;
            }

            // —— ④ 床位容量截断（不满整间亦按整间计价；分配人数 > 0 即该间售出） ——
            double factor = 1.0;
            if (totalRaw > cap) {
                factor = totalRaw <= 0 ? 0 : cap / totalRaw;
            }
            double servedTotal = 0;
            for (CustomerSegment seg : CustomerSegment.values()) {
                double v = raw.get(seg) * factor;
                served.merge(seg, v, Double::sum);
                servedTotal += v;
            }
            int sold = servedTotal > 0 ? 1 : 0;
            result.getSoldRoomsByBuilding().put(b.getId(), sold);
        }

        // —— ⑤ 配套当日服务人次 = min(全局入住总人数, 配套服务容量上限) ——
        double totalServed = 0;
        for (CustomerSegment seg : CustomerSegment.values()) {
            totalServed += served.get(seg);
        }
        for (BuildingInstance b : state.aliveSupportBuildings()) {
            BuildingForm f = b.getForm();
            double count = Math.min(totalServed, Math.max(0, f.getServiceCapacity()));
            result.getSupportServiceByBuilding().put(b.getId(), count);
        }

        // —— 汇总 ——
        double totalPotential = 0;
        double totalIntent = 0;
        int occupiedRooms = 0;
        for (CustomerSegment seg : CustomerSegment.values()) {
            totalPotential += potential.get(seg);
            totalIntent += intent.get(seg);
        }
        for (Integer sold : result.getSoldRoomsByBuilding().values()) {
            occupiedRooms += sold;
        }

        result.setPotentialBySegment(potential);
        result.setConversionBySegment(conversion);
        result.setIntentBySegment(intent);
        result.setServedBySegment(served);
        result.setTotalPotential(totalPotential);
        result.setTotalIntent(totalIntent);
        result.setTotalServed(totalServed);
        result.setOverflowGuests(Math.max(0, totalIntent - totalServed));
        result.setOccupiedRooms(occupiedRooms);
        return result;
    }
}
