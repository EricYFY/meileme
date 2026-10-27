package com.meileme.resort.service;

import com.meileme.resort.config.BuildingForm;
import com.meileme.resort.config.MotherBuildingType;
import com.meileme.resort.config.Plot;
import com.meileme.resort.config.SkillEffectKey;
import com.meileme.resort.model.BuildingInstance;
import com.meileme.resort.model.GameState;
import com.meileme.resort.model.MapCell;
import com.meileme.resort.model.PlotMap;
import org.springframework.stereotype.Service;

/** 新建建筑：仅允许在「已解锁地块 + 母本白名单 + 可建造空格」上建造，费用次日生效时结算。 */
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

    /** 新建花费（含技能「建造节流」减免） */
    public double buildCost(GameState state, MotherBuildingType mother) {
        if (mother == null) {
            return 0;
        }
        double cost = registry != null ? registry.motherBuildCost(mother) : 3000.0;
        return cost * skillTree.discount(state, SkillEffectKey.BUILD_COST_DISCOUNT);
    }

    /** 新建工期（游戏日）：按母本各自配置 */
    public int projectDays(MotherBuildingType mother) {
        if (mother == null) {
            return 3;
        }
        return registry != null ? registry.motherBuildDays(mother) : mother.getBuildDays();
    }

    /** 校验落格坐标：在地图内、可建造、未被占用；返回 null 表示可落格 */
    public String validateCell(GameState state, String plotId, int cellX, int cellY) {
        return validateCell(state, plotId, cellX, cellY, null);
    }

    /** 校验落格坐标（工程完成二次校验时排除该工程自身占用的格子） */
    public String validateCell(GameState state, String plotId, int cellX, int cellY, String excludeProjectId) {
        PlotMap map = registry.plotMap(plotId);
        if (map == null) {
            return "PLOT_MAP_NOT_FOUND";
        }
        if (cellX < 0 || cellY < 0 || cellX >= map.getWidth() || cellY >= map.getHeight()) {
            return "CELL_OUT_OF_BOUNDS";
        }
        MapCell cell = map.cellAt(cellX, cellY);
        if (cell == null || !cell.isBuildable()) {
            return "CELL_NOT_BUILDABLE";
        }
        if (state.isCellOccupied(plotId, cellX, cellY, excludeProjectId)) {
            return "CELL_OCCUPIED";
        }
        return null;
    }

    /** 校验新建；返回 null 表示可执行 */
    public String validateBuild(GameState state, MotherBuildingType mother, String plotId, int cellX, int cellY) {
        if (state == null || !state.isRunning()) {
            return "GAME_NOT_RUNNING";
        }
        if (mother == null) {
            return "BUILDING_TYPE_NOT_FOUND";
        }
        if (!mother.isOpen()) {
            return "MOTHER_NOT_OPEN";
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
        String cellReason = validateCell(state, plotId, cellX, cellY);
        if (cellReason != null) {
            return cellReason;
        }
        if (registry.baseFormOf(mother) == null) {
            return "FORM_NOT_CONFIGURED";
        }
        if (state.getFunds() < buildCost(state, mother)) {
            return "INSUFFICIENT_FUNDS";
        }
        return null;
    }

    /** 工程完成时新建建筑（费用已在开工时扣除，此处不再扣费） */
    public BuildingInstance completeBuild(GameState state, MotherBuildingType mother, String plotId, int cellX, int cellY) {
        BuildingInstance b = new BuildingInstance();
        b.setId(idGenerator.next("BLD"));
        b.setMother(mother);
        b.setForm(registry.baseFormOf(mother));
        b.setFormLevel(1);
        b.setPlotId(plotId);
        b.setCellX(cellX);
        b.setCellY(cellY);
        b.setAlive(true);
        b.setBuiltDay(state.getDay());
        state.getBuildings().add(b);
        state.getStats().setBuildingsBuiltCount(state.getStats().getBuildingsBuiltCount() + 1);
        if (state.getCodex() != null && b.getForm() != null) {
            state.getCodex().unlockForm(b.getForm().getId());
        }
        return b;
    }
}
