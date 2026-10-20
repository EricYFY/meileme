package com.meileme.resort.service;

import com.meileme.resort.config.BuildingForm;
import com.meileme.resort.config.MotherBuildingType;
import com.meileme.resort.config.Plot;
import com.meileme.resort.config.SkillEffectKey;
import com.meileme.resort.model.BuildingInstance;
import com.meileme.resort.model.GameState;
import org.springframework.stereotype.Service;

/** 新建建筑：仅允许在「已解锁地块 + 母本白名单 + 建筑数量上限内」建造，费用次日生效时结算。 */
@Service
public class ConstructionService {

    private final ConfigRegistry registry;
    private final SkillTreeService skillTree;
    private final IdGenerator idGenerator;

    public ConstructionService(ConfigRegistry registry, SkillTreeService skillTree, IdGenerator idGenerator) {
        this.registry = registry;
        this.skillTree = skillTree;
        this.idGenerator = idGenerator;
    }

    /** 地块建筑数量上限（含技能「地块扩容」加成） */
    public int buildLimit(GameState state, Plot plot) {
        return plot.getBuildLimit() + (int) skillTree.flat(state, SkillEffectKey.PLOT_BUILD_LIMIT);
    }

    /** 新建花费 */
    public double buildCost(GameState state, MotherBuildingType mother) {
        BuildingForm base = registry.baseFormOf(mother);
        return base == null ? 0 : base.getBuildCost();
    }

    /** 校验新建；返回 null 表示可执行 */
    public String validateBuild(GameState state, MotherBuildingType mother, String plotId) {
        if (state == null || state.isGameOver()) {
            return "GAME_NOT_RUNNING";
        }
        if (mother == null) {
            return "BUILDING_TYPE_NOT_FOUND";
        }
        if (!mother.isPhase1Buildable()) {
            return "MOTHER_NOT_BUILDABLE_IN_PHASE_1";
        }
        Plot plot = registry.plot(plotId);
        if (plot == null) {
            return "PLOT_NOT_FOUND";
        }
        if (!state.getUnlockedPlots().contains(plot.getId())) {
            return "PLOT_NOT_UNLOCKED";
        }
        if (!plot.allows(mother)) {
            return "MOTHER_NOT_ALLOWED_IN_PLOT";
        }
        if (state.aliveBuildingCountInPlot(plot.getId()) >= buildLimit(state, plot)) {
            return "PLOT_BUILD_LIMIT_REACHED";
        }
        if (registry.baseFormOf(mother) == null) {
            return "FORM_NOT_CONFIGURED";
        }
        if (state.getFunds() < buildCost(state, mother)) {
            return "INSUFFICIENT_FUNDS";
        }
        return null;
    }

    /** 执行新建 */
    public BuildingInstance build(GameState state, MotherBuildingType mother, String plotId) {
        double cost = buildCost(state, mother);
        state.setFunds(state.getFunds() - cost);
        BuildingInstance b = new BuildingInstance();
        b.setId(idGenerator.next("BLD"));
        b.setMother(mother);
        b.setForm(registry.baseFormOf(mother));
        b.setFormLevel(1);
        b.setPlotId(plotId);
        b.setAlive(true);
        b.setBuiltDay(state.getDay());
        state.getBuildings().add(b);
        state.getStats().setBuildingsBuiltCount(state.getStats().getBuildingsBuiltCount() + 1);
        return b;
    }
}
