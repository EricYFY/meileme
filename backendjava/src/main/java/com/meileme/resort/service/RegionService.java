package com.meileme.resort.service;

import com.meileme.resort.config.GameConfig;
import com.meileme.resort.config.Plot;
import com.meileme.resort.model.GameState;
import com.meileme.resort.model.Milestone;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * 区块解锁。
 *
 * <p>地块按注册顺序构成一条解锁链：<b>必须先解锁上一地块，再达成自身成就条件</b>（跨区域同样成立），
 * 满足条件后仍需<b>支付该地块的解锁费用</b>才真正解锁；区域内首个地块解锁即视为该大区域解锁。
 * 成就全局累计、新区域不重置。</p>
 */
@Service
public class RegionService {

    private final ConfigRegistry registry;

    public RegionService(ConfigRegistry registry) {
        this.registry = registry;
    }

    /** 开局初始化：解锁默认地块及其所属区域，并发放对应技能点 */
    public List<String> initialize(GameState state) {
        List<String> messages = new ArrayList<>();
        for (Plot plot : registry.allPlots()) {
            if (plot.isUnlockedByDefault()) {
                doUnlock(state, plot, messages);
            }
        }
        return messages;
    }

    /** 校验付费解锁；返回 null 表示可执行 */
    public String validateUnlock(GameState state, Plot plot) {
        if (state == null || !state.isRunning()) {
            return "GAME_NOT_RUNNING";
        }
        if (plot == null) {
            return "PLOT_NOT_FOUND";
        }
        if (state.getUnlockedPlots().contains(plot.getId())) {
            return "PLOT_ALREADY_UNLOCKED";
        }
        if (!previousUnlocked(state, plot)) {
            return "PREVIOUS_PLOT_LOCKED";
        }
        if (!achievementsMet(state, plot)) {
            return "ACHIEVEMENTS_UNMET";
        }
        if (state.getFunds() < plot.getUnlockCost()) {
            return "INSUFFICIENT_FUNDS";
        }
        return null;
    }

    /** 支付解锁费用并解锁地块 */
    public void unlock(GameState state, Plot plot) {
        state.setFunds(state.getFunds() - plot.getUnlockCost());
        doUnlock(state, plot, new ArrayList<>());
    }

    private boolean previousUnlocked(GameState state, Plot plot) {
        List<Plot> all = registry.allPlots();
        int idx = all.indexOf(plot);
        if (idx <= 0) {
            return false;
        }
        return state.getUnlockedPlots().contains(all.get(idx - 1).getId());
    }

    private boolean achievementsMet(GameState state, Plot plot) {
        for (String achId : plot.getUnlockAchievementIds()) {
            if (!state.getUnlockedAchievements().contains(achId)) {
                return false;
            }
        }
        return true;
    }

    private void doUnlock(GameState state, Plot plot, List<String> messages) {
        state.getUnlockedPlots().add(plot.getId());
        state.getUnlockedRegions().add(plot.getRegion());
        state.setSkillPoints(state.getSkillPoints() + GameConfig.SKILL_POINT_PER_PLOT);

        if (state.getCodex() != null && state.getCodex().unlockPlot(plot.getId())) {
            messages.add("📖 图鉴解锁：地块【" + plot.getDisplayName() + "】！");
        }

        Milestone m = new Milestone();
        m.setId("PLOT_" + plot.getId());
        m.setDescription("地块解锁：" + plot.getDisplayName());
        m.setSkillPoints(GameConfig.SKILL_POINT_PER_PLOT);
        m.setDay(state.getDay());
        state.getMilestones().add(m);

        messages.add("🗺️ 地块解锁：" + plot.getDisplayName() + "（技能点 +" + GameConfig.SKILL_POINT_PER_PLOT + "）");
    }
}
