package com.meileme.resort.service;

import com.meileme.resort.config.Achievement;
import com.meileme.resort.config.CustomerSegment;
import com.meileme.resort.model.GameState;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * 成就判定：全局跨区域累计统计（新区域不重置）；区块解锁所需成就门槛可被技能减免。
 */
@Service
public class AchievementService {

    private final ConfigRegistry registry;
    private final SkillTreeService skillTree;

    public AchievementService(ConfigRegistry registry, SkillTreeService skillTree) {
        this.registry = registry;
        this.skillTree = skillTree;
    }

    /** 成就是否已达成（阈值含技能「区块通达」减免） */
    public boolean isMet(GameState state, Achievement a) {
        double target = a.getTarget() * skillTree.achievementTargetMultiplier(state);
        switch (a.getType()) {
            case TOTAL_ASSETS:
                return state.getStats().getTotalAssets() >= target;
            case CUMULATIVE_INCOME:
                return state.getStats().getCumulativeIncome() >= target;
            case BUILDING_COUNT:
                return Math.max(state.aliveBuildings().size(), state.getStats().getBuildingsBuiltCount()) >= target;
            case FORM_LEVEL_COUNT:
                return formLevelCount(state, a.getParam()) >= target;
            case CUMULATIVE_GUESTS:
                return state.getStats().getTotalGuests() >= target;
            case SEGMENT_GUESTS:
                return segmentGuests(state, a.getParam()) >= target;
            case STAFF_COUNT:
                return state.aliveStaffs().size() >= target;
            case POSITION_LEVEL_COUNT:
                return positionLevelCount(state, a.getParam()) >= target;
            default:
                return false;
        }
    }

    /** 批量判定并解锁新成就，返回本次新解锁的成就 */
    public List<Achievement> evaluateAndUnlock(GameState state) {
        List<Achievement> unlocked = new ArrayList<>();
        for (Achievement a : registry.allAchievements()) {
            if (state.getUnlockedAchievements().contains(a.getId())) {
                continue;
            }
            if (isMet(state, a)) {
                state.getUnlockedAchievements().add(a.getId());
                if (a.getRewardSkillPoints() > 0) {
                    state.setSkillPoints(state.getSkillPoints() + a.getRewardSkillPoints());
                }
                unlocked.add(a);
            }
        }
        return unlocked;
    }

    private double formLevelCount(GameState state, String param) {
        String p = param == null ? "" : param;
        int idx = p.lastIndexOf('_');
        if (idx <= 0) {
            return 0;
        }
        String formId = p.substring(0, idx);
        int level;
        try {
            level = Integer.parseInt(p.substring(idx + 1));
        } catch (NumberFormatException e) {
            return 0;
        }
        return state.getStats().formLevelCount(formId, level);
    }

    private double positionLevelCount(GameState state, String param) {
        String p = param == null ? "" : param;
        int idx = p.lastIndexOf('_');
        if (idx <= 0) {
            return 0;
        }
        String positionName = p.substring(0, idx);
        int level;
        try {
            level = Integer.parseInt(p.substring(idx + 1));
        } catch (NumberFormatException e) {
            return 0;
        }
        return state.getStats().positionLevelCount(positionName, level);
    }

    private double segmentGuests(GameState state, String param) {
        return state.getStats().segmentGuestsOf(param);
    }
}
