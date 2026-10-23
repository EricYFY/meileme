package com.meileme.resort.service;

import com.meileme.resort.config.GameConfig;
import com.meileme.resort.config.SkillEffectKey;
import com.meileme.resort.config.SkillEffectType;
import com.meileme.resort.config.SkillNode;
import com.meileme.resort.model.GameState;
import com.meileme.resort.repository.GameStateRepository;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 局内技能树：DAG 拓扑（禁止循环依赖），全部前置解锁后方可解锁，不支持洗点。
 */
@Service
public class SkillTreeService {

    private final ConfigRegistry registry;
    private final GameStateRepository repository;

    public SkillTreeService(ConfigRegistry registry, GameStateRepository repository) {
        this.registry = registry;
        this.repository = repository;
    }

    /** 倍率加成聚合：Π(1 + value)，无该键则返回 1.0 */
    public double multiplier(GameState state, SkillEffectKey key) {
        double m = 1.0;
        for (SkillNode n : unlockedNodes(state)) {
            if (n.getEffectType() == SkillEffectType.MULTIPLIER && n.getEffectKey() == key) {
                m *= (1 + n.getValue());
            }
        }
        return m;
    }

    /** 费用减免聚合：Π(1 − value)，无该键则返回 1.0（即不打折） */
    public double discount(GameState state, SkillEffectKey key) {
        double m = 1.0;
        for (SkillNode n : unlockedNodes(state)) {
            if (n.getEffectType() == SkillEffectType.COST_DISCOUNT && n.getEffectKey() == key) {
                m *= Math.max(0, 1 - n.getValue());
            }
        }
        return m;
    }

    /** 固定加成聚合：Σ value */
    public double flat(GameState state, SkillEffectKey key) {
        double sum = 0;
        for (SkillNode n : unlockedNodes(state)) {
            if (n.getEffectType() == SkillEffectType.FLAT && n.getEffectKey() == key) {
                sum += n.getValue();
            }
        }
        return sum;
    }

    /** 区块解锁成就门槛倍率（越小门槛越低） */
    public double achievementTargetMultiplier(GameState state) {
        return discount(state, SkillEffectKey.REGION_ACHIEVEMENT_DISCOUNT);
    }

    /** 校验技能是否可解锁；返回 null 表示可解锁，否则返回失败原因 */
    public String validateUnlock(GameState state, String skillId) {
        if (state == null || !state.isRunning()) {
            return "GAME_NOT_RUNNING";
        }
        SkillNode node = registry.skill(skillId);
        if (node == null) {
            return "SKILL_NOT_FOUND";
        }
        if (state.getUnlockedSkills().contains(skillId)) {
            return "SKILL_ALREADY_UNLOCKED";
        }
        if (state.getSkillPoints() <= 0) {
            return "NO_SKILL_POINT";
        }
        for (String pre : node.getPrerequisites()) {
            if (!state.getUnlockedSkills().contains(pre)) {
                return "PREREQUISITE_NOT_UNLOCKED";
            }
        }
        return null;
    }

    /** 执行解锁（调用前应先校验） */
    public void unlock(GameState state, String skillId) {
        SkillNode node = registry.skill(skillId);
        if (node == null) {
            return;
        }
        state.setSkillPoints(Math.max(0, state.getSkillPoints() - 1));
        state.getUnlockedSkills().add(skillId);
    }

    private List<SkillNode> unlockedNodes(GameState state) {
        List<SkillNode> list = new java.util.ArrayList<>();
        if (state == null) {
            return list;
        }
        for (String id : state.getUnlockedSkills()) {
            SkillNode n = registry.skill(id);
            if (n != null) {
                list.add(n);
            }
        }
        return list;
    }

    /** 供外部读取当前局状态（便捷） */
    public GameState currentState() {
        return repository.get();
    }

    /** 技能点上限提示：当前设计为每解锁一个地块 +1 点 */
    public int skillPointPerPlot() {
        return GameConfig.SKILL_POINT_PER_PLOT;
    }
}
