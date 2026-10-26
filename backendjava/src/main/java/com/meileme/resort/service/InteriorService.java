package com.meileme.resort.service;

import com.meileme.resort.config.InteriorType;
import com.meileme.resort.config.SkillEffectKey;
import com.meileme.resort.model.BuildingInstance;
import com.meileme.resort.model.GameState;
import com.meileme.resort.model.InteriorInstance;
import org.springframework.stereotype.Service;

/**
 * 内装升级：内装提供舒适度、承载容量、客群吸引力加成、房屋收益加成；形态等级决定该形态下内装等级上限。
 */
@Service
public class InteriorService {

    private final ConfigRegistry registry;
    private final SkillTreeService skillTree;
    private final IdGenerator idGenerator;

    public InteriorService(ConfigRegistry registry, SkillTreeService skillTree, IdGenerator idGenerator) {
        this.registry = registry;
        this.skillTree = skillTree;
        this.idGenerator = idGenerator;
    }

    /** 内装升级花费（含技能减免） */
    public double upgradeCost(GameState state, InteriorType type, int nextLevel) {
        if (type == null) return 0d;
        com.meileme.resort.config.InteriorConfig ic = registry != null ? registry.interior(type.name()) : null;
        double base = ic != null ? ic.upgradeCostOf(nextLevel) : type.upgradeCostOf(nextLevel);
        return base * skillTree.discount(state, SkillEffectKey.INTERIOR_UPGRADE_COST_DISCOUNT);
    }

    /** 内装升级工期：按内装类型 + 目标等级各自配置 */
    public int upgradeDays(InteriorType type, int nextLevel) {
        if (type == null) return 0;
        com.meileme.resort.config.InteriorConfig ic = registry != null ? registry.interior(type.name()) : null;
        return ic != null ? ic.upgradeDaysOf(nextLevel) : type.upgradeDaysOf(nextLevel);
    }

    /** 校验内装升级；返回 null 表示可执行 */
    public String validateUpgrade(GameState state, BuildingInstance building, InteriorType type) {
        if (state == null || !state.isRunning()) {
            return "GAME_NOT_RUNNING";
        }
        if (building == null) {
            return "BUILDING_NOT_FOUND";
        }
        if (type == null) {
            return "INTERIOR_NOT_FOUND";
        }
        if (!type.isApplicableTo(building.getForm(), building.getMother())) {
            return "INTERIOR_NOT_APPLICABLE";
        }
        if (state.projectOfBuilding(building.getId()) != null) {
            return "PROJECT_IN_PROGRESS";
        }
        InteriorInstance existing = building.findInterior(type);
        int currentLevel = existing == null ? 0 : existing.getLevel();
        int maxLevel = building.getForm().maxInteriorLevelOf(building.getFormLevel());
        if (currentLevel >= maxLevel) {
            return "INTERIOR_LEVEL_CAP_REACHED";
        }
        if (state.getFunds() < upgradeCost(state, type, currentLevel + 1)) {
            return "INSUFFICIENT_FUNDS";
        }
        return null;
    }

    /** 工程完成时内装升级（不存在则安装 Lv1，否则升 1 级；费用已在开工时扣除） */
    public void completeUpgrade(GameState state, BuildingInstance building, InteriorType type) {
        InteriorInstance existing = building.findInterior(type);
        int nextLevel = (existing == null ? 0 : existing.getLevel()) + 1;
        if (existing == null) {
            building.getInteriors().add(new InteriorInstance(idGenerator.next("INT"), type, nextLevel));
        } else {
            existing.setLevel(nextLevel);
        }
    }
}
