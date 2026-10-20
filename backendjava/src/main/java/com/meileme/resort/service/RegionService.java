package com.meileme.resort.service;

import com.meileme.resort.config.GameConfig;
import com.meileme.resort.config.Plot;
import com.meileme.resort.config.Region;
import com.meileme.resort.model.GameState;
import com.meileme.resort.model.Milestone;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * 区块升级解锁：大区域（A村 / B镇）下分地块；开局仅开放 A 村乡间地块，
 * 达成指定成就后依次解锁同区域后续地块；整区达成总成就后解锁下一区域。成就全局累计、新区域不重置。
 */
@Service
public class RegionService {

    private final ConfigRegistry registry;

    public RegionService(ConfigRegistry registry) {
        this.registry = registry;
    }

    /** 开局初始化：解锁 A 村与全部默认地块，并发放对应技能点 */
    public List<String> initialize(GameState state) {
        List<String> messages = new ArrayList<>();
        state.getUnlockedRegions().add(Region.VILLAGE_A);
        for (Plot plot : registry.allPlots()) {
            if (plot.isUnlockedByDefault()) {
                unlockPlot(state, plot, messages);
            }
        }
        return messages;
    }

    /** 每次结算后调用：判定区域解锁与地块解锁，返回本次解锁提示 */
    public List<String> checkAndUnlock(GameState state) {
        List<String> messages = new ArrayList<>();

        // 区域解锁：A 村总成就达成 → 解锁 B 镇
        String gate = registry.regionGateAchievement();
        if (state.getUnlockedAchievements().contains(gate) && !state.getUnlockedRegions().contains(Region.TOWN_B)) {
            state.getUnlockedRegions().add(Region.TOWN_B);
            messages.add("🏙️ 大区域解锁：B镇");
        }

        for (Plot plot : registry.allPlots()) {
            if (state.getUnlockedPlots().contains(plot.getId())) {
                continue;
            }
            if (!state.getUnlockedRegions().contains(plot.getRegion())) {
                continue;
            }
            boolean allMet = true;
            for (String achId : plot.getUnlockAchievementIds()) {
                if (!state.getUnlockedAchievements().contains(achId)) {
                    allMet = false;
                    break;
                }
            }
            if (allMet) {
                unlockPlot(state, plot, messages);
            }
        }
        return messages;
    }

    private void unlockPlot(GameState state, Plot plot, List<String> messages) {
        state.getUnlockedPlots().add(plot.getId());
        state.setSkillPoints(state.getSkillPoints() + GameConfig.SKILL_POINT_PER_PLOT);

        Milestone m = new Milestone();
        m.setId("PLOT_" + plot.getId());
        m.setDescription("地块解锁：" + plot.getDisplayName());
        m.setSkillPoints(GameConfig.SKILL_POINT_PER_PLOT);
        m.setDay(state.getDay());
        state.getMilestones().add(m);

        messages.add("🗺️ 地块解锁：" + plot.getDisplayName() + "（技能点 +" + GameConfig.SKILL_POINT_PER_PLOT + "）");
    }
}
