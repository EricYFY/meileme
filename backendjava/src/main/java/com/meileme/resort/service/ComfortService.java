package com.meileme.resort.service;

import com.meileme.resort.config.AbilityType;
import com.meileme.resort.config.BuildingForm;
import com.meileme.resort.config.GameConfig;
import com.meileme.resort.config.SkillEffectKey;
import com.meileme.resort.model.BuildingInstance;
import com.meileme.resort.model.GameState;
import com.meileme.resort.model.InteriorInstance;
import com.meileme.resort.model.StaffInstance;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * 舒适度与承载计算。
 *
 * <p>综合舒适度 = 形态基础舒适度（含形态等级加成）+ 内装舒适度，再乘以「人力供给系数」。
 * 人力供给不足<b>仅降低相关建筑舒适度，不削减承载容量</b>。</p>
 */
@Service
public class ComfortService {

    private final SkillTreeService skillTree;

    public ComfortService(SkillTreeService skillTree) {
        this.skillTree = skillTree;
    }

    /** 全局各能力总需求（全部存活建筑） */
    public Map<AbilityType, Double> totalAbilityDemand(GameState state) {
        Map<AbilityType, Double> demand = new EnumMap<>(AbilityType.class);
        for (AbilityType a : AbilityType.values()) {
            demand.put(a, 0d);
        }
        for (BuildingInstance b : state.aliveBuildings()) {
            BuildingForm f = b.getForm();
            if (f == null) {
                continue;
            }
            for (AbilityType a : AbilityType.values()) {
                double d = f.abilityDemandOf(a);
                if (d > 0) {
                    demand.merge(a, d * b.getFormLevel(), Double::sum);
                }
            }
        }
        return demand;
    }

    /** 全局各能力总供给（全部在册员工，含技能倍率） */
    public Map<AbilityType, Double> totalAbilitySupply(GameState state) {
        double abilityMult = skillTree.multiplier(state, SkillEffectKey.STAFF_ABILITY_MULT);
        Map<AbilityType, Double> supply = new EnumMap<>(AbilityType.class);
        for (AbilityType a : AbilityType.values()) {
            supply.put(a, 0d);
        }
        for (StaffInstance s : state.aliveStaffs()) {
            double v = s.abilityValue() * abilityMult;
            for (AbilityType a : s.abilities()) {
                supply.merge(a, v, Double::sum);
            }
        }
        return supply;
    }

    /** 全局人力供给系数（按能力维度）：ratio = min(1, 供给 / 需求) */
    public Map<AbilityType, Double> supplyRatios(GameState state) {
        Map<AbilityType, Double> demand = totalAbilityDemand(state);
        Map<AbilityType, Double> supply = totalAbilitySupply(state);
        Map<AbilityType, Double> ratios = new EnumMap<>(AbilityType.class);
        for (AbilityType a : AbilityType.values()) {
            double d = demand.getOrDefault(a, 0d);
            ratios.put(a, d <= 0 ? 1.0 : Math.min(1.0, supply.getOrDefault(a, 0d) / d));
        }
        return ratios;
    }

    /** 单建筑内装舒适度加成 */
    public double interiorComfortOf(GameState state, BuildingInstance b) {
        double interiorComfortMult = skillTree.multiplier(state, SkillEffectKey.INTERIOR_COMFORT_MULT);
        double interiorComfort = 0;
        for (InteriorInstance it : b.getInteriors()) {
            interiorComfort += it.getInteriorType().getComfortPerLevel() * it.getLevel();
        }
        return interiorComfort * interiorComfortMult;
    }

    /** 单建筑人力供给保障系数 */
    public double staffFactorOf(BuildingInstance b, Map<AbilityType, Double> ratios) {
        BuildingForm f = b.getForm();
        if (f == null) {
            return 1.0;
        }
        double factor = 1.0;
        boolean hasDemand = false;
        for (AbilityType a : AbilityType.values()) {
            if (f.abilityDemandOf(a) > 0) {
                hasDemand = true;
                factor = Math.min(factor, ratios.getOrDefault(a, 1.0));
            }
        }
        if (hasDemand) {
            return Math.max(GameConfig.STAFF_SHORTAGE_MIN_COMFORT_RATIO, factor);
        }
        return 1.0;
    }

    /** 单建筑短缺的能力名称列表（含实际供给率） */
    public List<Map<String, Object>> shortageAbilitiesOf(BuildingInstance b, Map<AbilityType, Double> ratios) {
        BuildingForm f = b.getForm();
        if (f == null) {
            return List.of();
        }
        List<Map<String, Object>> list = new ArrayList<>();
        for (AbilityType a : AbilityType.values()) {
            if (f.abilityDemandOf(a) > 0) {
                double r = ratios.getOrDefault(a, 1.0);
                if (r < 0.999) {
                    list.add(Map.of(
                            "ability", a.name(),
                            "name", a.getDisplayName(),
                            "ratio", r
                    ));
                }
            }
        }
        return list;
    }

    /** 单建筑综合舒适度 */
    public double comfortOf(GameState state, BuildingInstance b, Map<AbilityType, Double> ratios) {
        BuildingForm f = b.getForm();
        if (f == null) {
            return 0;
        }
        double base = f.comfortOf(b.getFormLevel()) + interiorComfortOf(state, b);
        return base * staffFactorOf(b, ratios);
    }

    /** 单建筑承载容量（住宿 = 每间可接待人数 + 承载型内装容量） */
    public double capacityOf(GameState state, BuildingInstance b) {
        BuildingForm f = b.getForm();
        if (f == null) {
            return 0;
        }
        double capacityMult = skillTree.multiplier(state, SkillEffectKey.INTERIOR_CAPACITY_MULT);
        double extra = 0;
        for (InteriorInstance it : b.getInteriors()) {
            extra += it.getInteriorType().getCapacityPerLevel() * it.getLevel();
        }
        extra *= capacityMult;
        return f.getCapacityPerRoom() + extra;
    }

    /** 配套服务容量（配套 = 形态服务容量上限 + 承载型内装容量） */
    public double supportCapacityOf(GameState state, BuildingInstance b) {
        BuildingForm f = b.getForm();
        if (f == null) {
            return 0;
        }
        double capacityMult = skillTree.multiplier(state, SkillEffectKey.INTERIOR_CAPACITY_MULT);
        double extra = 0;
        for (InteriorInstance it : b.getInteriors()) {
            extra += it.getInteriorType().getCapacityPerLevel() * it.getLevel();
        }
        extra *= capacityMult;
        return f.getServiceCapacity() + extra;
    }
}
