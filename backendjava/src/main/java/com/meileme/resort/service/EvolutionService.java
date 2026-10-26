package com.meileme.resort.service;

import com.meileme.resort.config.BuildingForm;
import com.meileme.resort.config.EvolutionLine;
import com.meileme.resort.config.GameConfig;
import com.meileme.resort.config.MergeRecipe;
import com.meileme.resort.config.Plot;
import com.meileme.resort.config.SkillEffectKey;
import com.meileme.resort.model.BuildingInstance;
import com.meileme.resort.model.GameState;
import com.meileme.resort.model.InteriorInstance;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * 建筑进化：形态等级升级（Lv1 → Lv2 → Lv3）、分支进化（满级后选定进化线）、合并进化（消耗二建一）。
 *
 * <p>两大进化体系不互斥：建筑既可在分支线上进阶，也可作为合并配方源建筑被消耗。</p>
 */
@Service
public class EvolutionService {

    private final ConfigRegistry registry;
    private final SkillTreeService skillTree;
    private final IdGenerator idGenerator;

    public EvolutionService(ConfigRegistry registry, SkillTreeService skillTree, IdGenerator idGenerator) {
        this.registry = registry;
        this.skillTree = skillTree;
        this.idGenerator = idGenerator;
    }

    // ===================== 形态等级升级 =====================

    /** 形态升级工期：按形态各自配置的 [Lv1→Lv2, Lv2→Lv3] 取值 */
    public int formLevelUpDays(BuildingForm form, int currentLevel) {
        return form == null ? 0 : form.formLevelUpDaysOf(currentLevel);
    }

    /** 分支进化工期：按目标形态配置 */
    public int branchEvolveDays(BuildingForm target) {
        return target == null ? 0 : target.getEvolveDays();
    }

    /** 合并进化工期：按合并配方配置 */
    public int mergeEvolveDays(MergeRecipe recipe) {
        return recipe == null ? 0 : recipe.getMergeDays();
    }

    public double formLevelUpCost(GameState state, BuildingInstance building) {
        double base = building.getForm().formLevelUpCostOf(building.getFormLevel());
        return base * skillTree.discount(state, SkillEffectKey.EVOLVE_COST_DISCOUNT);
    }

    public String validateFormLevelUp(GameState state, BuildingInstance building) {
        if (state == null || !state.isRunning()) {
            return "GAME_NOT_RUNNING";
        }
        if (building == null) {
            return "BUILDING_NOT_FOUND";
        }
        if (state.projectOfBuilding(building.getId()) != null) {
            return "PROJECT_IN_PROGRESS";
        }
        if (building.getFormLevel() >= building.getForm().getMaxLevel()) {
            return "FORM_LEVEL_MAX_REACHED";
        }
        if (state.getFunds() < formLevelUpCost(state, building)) {
            return "INSUFFICIENT_FUNDS";
        }
        return null;
    }

    /** 工程完成时形态等级 +1（费用已在开工时扣除） */
    public void completeFormLevelUp(BuildingInstance building) {
        building.setFormLevel(building.getFormLevel() + 1);
    }

    // ===================== 分支进化 =====================

    public double branchEvolveCost(GameState state, BuildingForm target) {
        return target.getEvolveCost() * skillTree.discount(state, SkillEffectKey.EVOLVE_COST_DISCOUNT);
    }

    public String validateBranchEvolve(GameState state, BuildingInstance building, BuildingForm target) {
        if (state == null || !state.isRunning()) {
            return "GAME_NOT_RUNNING";
        }
        if (building == null) {
            return "BUILDING_NOT_FOUND";
        }
        if (target == null) {
            return "TARGET_FORM_NOT_FOUND";
        }
        if (state.projectOfBuilding(building.getId()) != null) {
            return "PROJECT_IN_PROGRESS";
        }
        if (building.getFormLevel() < building.getForm().getMaxLevel()) {
            return "FORM_NOT_MAX_LEVEL";
        }
        if (target.getEvolveFrom() != building.getForm()) {
            return "EVOLVE_PATH_NOT_ALLOWED";
        }
        EvolutionLine currentLine = building.getForm().getLine();
        EvolutionLine targetLine = target.getLine();
        // 分支选定后不可切换、不可回退
        if (currentLine != EvolutionLine.BASE && currentLine != targetLine) {
            return "EVOLVE_LINE_LOCKED";
        }
        if (currentLine == EvolutionLine.MERGE) {
            return "MERGE_FORM_CANNOT_BRANCH";
        }
        Plot plot = registry.plot(building.getPlotId());
        if (plot != null && target.getTier() > plot.getEvolveTierCap()) {
            return "PLOT_EVOLVE_TIER_CAP_EXCEEDED";
        }
        if (state.getFunds() < branchEvolveCost(state, target)) {
            return "INSUFFICIENT_FUNDS";
        }
        return null;
    }

    /** 工程完成时分支进化：形态替换为目标形态并回到 Lv1（费用已在开工时扣除，多余内装等级修剪至当前上限） */
    public void completeBranchEvolve(BuildingInstance building, BuildingForm target) {
        building.setForm(target);
        building.setFormLevel(GameConfig.FORM_MIN_LEVEL);
        int maxInterior = target.maxInteriorLevelOf(GameConfig.FORM_MIN_LEVEL);
        for (InteriorInstance it : building.getInteriors()) {
            if (it.getLevel() > maxInterior) {
                it.setLevel(maxInterior);
            }
        }
    }

    /** 某建筑当前可分支进化的目标形态列表 */
    public List<BuildingForm> branchTargets(GameState state, BuildingInstance building) {
        List<BuildingForm> all = registry.branchTargetsOf(building.getForm());
        List<BuildingForm> ok = new ArrayList<>();
        for (BuildingForm f : all) {
            if (validateBranchEvolve(state, building, f) == null) {
                ok.add(f);
            }
        }
        return ok;
    }

    // ===================== 合并进化 =====================

    public double mergeCost(GameState state, MergeRecipe recipe) {
        return recipe.getMergeCost() * skillTree.discount(state, SkillEffectKey.MERGE_COST_DISCOUNT);
    }

    public String validateMerge(GameState state, BuildingInstance a, BuildingInstance b, MergeRecipe recipe) {
        if (state == null || !state.isRunning()) {
            return "GAME_NOT_RUNNING";
        }
        if (a == null || b == null) {
            return "BUILDING_NOT_FOUND";
        }
        if (a.getId().equals(b.getId())) {
            return "MERGE_SOURCE_DUPLICATED";
        }
        if (state.projectOfBuilding(a.getId()) != null || state.projectOfBuilding(b.getId()) != null) {
            return "PROJECT_IN_PROGRESS";
        }
        if (recipe == null) {
            return "MERGE_RECIPE_NOT_FOUND";
        }
        if (!recipe.matches(a.getForm(), a.getFormLevel(), b.getForm(), b.getFormLevel())) {
            return "MERGE_RECIPE_NOT_MATCHED";
        }
        if (!a.isAdjacentTo(b)) {
            return "MERGE_SOURCE_NOT_ADJACENT";
        }
        Plot plot = registry.plot(a.getPlotId());
        if (plot != null && recipe.getResultForm().getTier() > plot.getEvolveTierCap()) {
            return "PLOT_EVOLVE_TIER_CAP_EXCEEDED";
        }
        if (state.getFunds() < mergeCost(state, recipe)) {
            return "INSUFFICIENT_FUNDS";
        }
        return null;
    }

    /** 工程完成时合并：两个源建筑逻辑删除（保留墓碑记录），产物为新建建筑并记录来源 ID（费用已在开工时扣除） */
    public BuildingInstance completeMerge(GameState state, BuildingInstance a, BuildingInstance b, MergeRecipe recipe) {
        a.setAlive(false);
        b.setAlive(false);

        BuildingInstance result = new BuildingInstance();
        result.setId(idGenerator.next("BLD"));
        result.setMother(null);
        result.setForm(recipe.getResultForm());
        result.setFormLevel(recipe.getResultLevel());
        result.setPlotId(a.getPlotId());
        result.setCellX(a.getCellX());
        result.setCellY(a.getCellY());
        result.setAlive(true);
        result.setBuiltDay(state.getDay());
        result.getMergeSourceIds().add(a.getId());
        result.getMergeSourceIds().add(b.getId());
        state.getBuildings().add(result);
        state.getStats().setBuildingsBuiltCount(state.getStats().getBuildingsBuiltCount() + 1);
        return result;
    }

    /** 根据两个源建筑匹配合并配方（未匹配返回 null） */
    public MergeRecipe matchRecipe(BuildingInstance a, BuildingInstance b) {
        if (a == null || b == null) {
            return null;
        }
        return registry.matchRecipe(a.getForm(), a.getFormLevel(), b.getForm(), b.getFormLevel());
    }
}
