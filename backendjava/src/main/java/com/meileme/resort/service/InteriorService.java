package com.meileme.resort.service;

import com.meileme.resort.config.InteriorType;
import com.meileme.resort.config.SkillEffectKey;
import com.meileme.resort.model.BuildingInstance;
import com.meileme.resort.model.GameState;
import com.meileme.resort.model.InteriorInstance;
import org.springframework.stereotype.Service;

/**
 * 内装升级：内装仅影响舒适度与承载容量；形态等级决定该形态下内装等级上限。
 */
@Service
public class InteriorService {

    private final SkillTreeService skillTree;
    private final IdGenerator idGenerator;

    public InteriorService(SkillTreeService skillTree, IdGenerator idGenerator) {
        this.skillTree = skillTree;
        this.idGenerator = idGenerator;
    }

    /** 内装升级花费（含技能减免） */
    public double upgradeCost(GameState state, InteriorType type, int nextLevel) {
        return type.upgradeCostOf(nextLevel) * skillTree.discount(state, SkillEffectKey.INTERIOR_UPGRADE_COST_DISCOUNT);
    }

    /** 校验内装升级；返回 null 表示可执行 */
    public String validateUpgrade(GameState state, BuildingInstance building, InteriorType type) {
        if (state == null || state.isGameOver()) {
            return "GAME_NOT_RUNNING";
        }
        if (building == null) {
            return "BUILDING_NOT_FOUND";
        }
        if (type == null) {
            return "INTERIOR_NOT_FOUND";
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

    /** 执行内装升级（不存在则安装 Lv1，否则升 1 级） */
    public void upgrade(GameState state, BuildingInstance building, InteriorType type) {
        InteriorInstance existing = building.findInterior(type);
        int nextLevel = (existing == null ? 0 : existing.getLevel()) + 1;
        double cost = upgradeCost(state, type, nextLevel);
        state.setFunds(state.getFunds() - cost);
        if (existing == null) {
            building.getInteriors().add(new InteriorInstance(idGenerator.next("INT"), type, nextLevel));
        } else {
            existing.setLevel(nextLevel);
        }
    }
}
