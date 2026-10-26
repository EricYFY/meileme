package com.meileme.resort.service;

import com.meileme.resort.config.GameConfig;
import com.meileme.resort.config.PositionConfig;
import com.meileme.resort.config.PositionType;
import com.meileme.resort.config.SkillEffectKey;
import com.meileme.resort.model.GameState;
import com.meileme.resort.model.StaffInstance;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * 雇佣系统：全局共享人力池。
 *
 * <p>招聘 / 解雇无冷却，招聘仅开放 Lv1 且必定成功；员工在岗累计游戏日达标后<b>不会自动升级</b>，
 * 需由玩家支付「培训费」完成升级。培训费与所需时长均按岗位配置，时长受技能倍率影响。</p>
 */
@Service
public class StaffService {

    private final ConfigRegistry registry;
    private final SkillTreeService skillTree;
    private final IdGenerator idGenerator;

    public StaffService(ConfigRegistry registry, SkillTreeService skillTree, IdGenerator idGenerator) {
        this.registry = registry;
        this.skillTree = skillTree;
        this.idGenerator = idGenerator;
    }

    private PositionConfig positionConfig(PositionType position) {
        if (position == null || registry == null) return null;
        return registry.position(position.name());
    }

    /** 招聘一次性花费（仅 Lv1，含技能减免） */
    public double hireCost(GameState state, PositionType position) {
        PositionConfig cfg = positionConfig(position);
        double base = cfg != null ? cfg.hireCostOf(GameConfig.STAFF_MIN_LEVEL) : position.hireCostOf(GameConfig.STAFF_MIN_LEVEL);
        return base * skillTree.discount(state, SkillEffectKey.HIRE_COST_DISCOUNT);
    }

    /** 解雇一次性补偿金（复合能力员工按主岗位计算，不叠加） */
    public double fireCompensation(GameState state, StaffInstance staff) {
        PositionConfig cfg = positionConfig(staff.getPosition());
        double base = cfg != null ? cfg.compensationOf(staff.getLevel(), staff.getWorkDays())
                : staff.getPosition().compensationOf(staff.getLevel(), staff.getWorkDays());
        return base * skillTree.discount(state, SkillEffectKey.FIRE_COMPENSATION_DISCOUNT);
    }

    /** 当前等级升到下一级所需的在岗累计游戏日（含技能倍率）；已满级返回 0 */
    public int trainingRequiredDays(GameState state, StaffInstance staff) {
        if (staff == null || staff.getPosition() == null || staff.getLevel() >= GameConfig.STAFF_MAX_LEVEL) {
            return 0;
        }
        PositionConfig cfg = positionConfig(staff.getPosition());
        int baseDays = cfg != null ? cfg.workDaysToNextLevel(staff.getLevel()) : staff.getPosition().workDaysToNextLevel(staff.getLevel());
        double daysMult = skillTree.discount(state, SkillEffectKey.STAFF_UPGRADE_DAYS_MULT);
        return (int) Math.max(1, Math.round(baseDays * daysMult));
    }

    /** 当前等级升到下一级所需的培训工期天数；已满级返回 0 */
    public int trainingDays(GameState state, StaffInstance staff) {
        if (staff == null || staff.getPosition() == null || staff.getLevel() >= GameConfig.STAFF_MAX_LEVEL) {
            return 0;
        }
        PositionConfig cfg = positionConfig(staff.getPosition());
        int baseDays = cfg != null ? cfg.trainDaysToNextLevel(staff.getLevel()) : staff.getPosition().trainDaysToNextLevel(staff.getLevel());
        double daysMult = skillTree.discount(state, SkillEffectKey.STAFF_UPGRADE_DAYS_MULT);
        return (int) Math.max(1, Math.round(baseDays * daysMult));
    }

    /** 当前等级升到下一级所需的一次性培训费；已满级返回 0 */
    public double trainingCost(StaffInstance staff) {
        if (staff == null || staff.getPosition() == null) {
            return 0d;
        }
        PositionConfig cfg = positionConfig(staff.getPosition());
        return cfg != null ? cfg.trainCostOf(staff.getLevel()) : staff.getPosition().trainCostOf(staff.getLevel());
    }

    /** 是否已满足培训时长条件（培训中或满级恒为 false） */
    public boolean trainingReady(GameState state, StaffInstance staff) {
        if (staff == null || staff.isTraining()) {
            return false;
        }
        int required = trainingRequiredDays(state, staff);
        return required > 0 && staff.getDaysAtCurrentLevel() >= required;
    }

    /** 校验招聘；返回 null 表示可执行 */
    public String validateHire(GameState state, PositionType position) {
        if (state == null || !state.isRunning()) {
            return "GAME_NOT_RUNNING";
        }
        if (position == null) {
            return "POSITION_NOT_FOUND";
        }
        if (state.getFunds() < hireCost(state, position)) {
            return "INSUFFICIENT_FUNDS";
        }
        return null;
    }

    /** 校验培训升级；返回 null 表示可执行 */
    public String validateTrain(GameState state, StaffInstance staff) {
        if (state == null || !state.isRunning()) {
            return "GAME_NOT_RUNNING";
        }
        if (staff == null) {
            return "STAFF_NOT_FOUND";
        }
        if (staff.getLevel() >= GameConfig.STAFF_MAX_LEVEL) {
            return "STAFF_MAX_LEVEL";
        }
        if (staff.isTraining()) {
            return "ALREADY_TRAINING";
        }
        if (!trainingReady(state, staff)) {
            return "TRAINING_NOT_READY";
        }
        if (state.getFunds() < trainingCost(staff)) {
            return "INSUFFICIENT_FUNDS";
        }
        return null;
    }

    /** 校验解雇；返回 null 表示可执行 */
    public String validateFire(GameState state, StaffInstance staff) {
        if (state == null || !state.isRunning()) {
            return "GAME_NOT_RUNNING";
        }
        if (staff == null) {
            return "STAFF_NOT_FOUND";
        }
        if (state.getFunds() < fireCompensation(state, staff)) {
            return "INSUFFICIENT_FUNDS";
        }
        return null;
    }

    /** 执行招聘（仅 Lv1，招聘必定成功） */
    public StaffInstance hire(GameState state, PositionType position) {
        double cost = hireCost(state, position);
        state.setFunds(state.getFunds() - cost);
        StaffInstance s = new StaffInstance();
        s.setId(idGenerator.next("STAFF"));
        s.setPosition(position);
        s.setLevel(GameConfig.STAFF_MIN_LEVEL);
        s.setWorkDays(0);
        s.setDaysAtCurrentLevel(0);
        s.setAlive(true);
        s.setHireDay(state.getDay());
        state.getStaffs().add(s);
        return s;
    }

    /** 开始升级培训：扣培训费、进入培训状态（培训期间不脱产，按原等级正常提供能力值） */
    public void startTrain(GameState state, StaffInstance staff) {
        double cost = trainingCost(staff);
        state.setFunds(state.getFunds() - cost);
        int days = trainingDays(state, staff);
        if (days <= 0) {
            staff.setLevel(staff.getLevel() + 1);
            staff.setDaysAtCurrentLevel(0);
            staff.setTraining(false);
            staff.setTrainingDaysRemaining(0);
            staff.setTrainingTotalDays(0);
        } else {
            staff.setTraining(true);
            staff.setTrainingDaysRemaining(days);
            staff.setTrainingTotalDays(days);
            staff.setTrainingTargetLevel(staff.getLevel() + 1);
        }
    }

    /** 执行培训升级（兼容旧方法调用） */
    public void train(GameState state, StaffInstance staff) {
        startTrain(state, staff);
    }

    /** 执行解雇 */
    public void fire(GameState state, StaffInstance staff) {
        double comp = fireCompensation(state, staff);
        state.setFunds(state.getFunds() - comp);
        staff.setAlive(false);
    }

    /** 每日推进：在岗时长累计 / 培训天数推进（培训期间原等级能力正常提供）；返回当日提示 */
    public List<String> advanceOneDay(GameState state) {
        List<String> events = new ArrayList<>();
        for (StaffInstance s : state.aliveStaffs()) {
            s.setWorkDays(s.getWorkDays() + 1);
            if (s.isTraining()) {
                s.setTrainingDaysRemaining(s.getTrainingDaysRemaining() - 1);
                if (s.getTrainingDaysRemaining() <= 0) {
                    s.setTraining(false);
                    s.setLevel(s.getTrainingTargetLevel() > 0 ? s.getTrainingTargetLevel() : s.getLevel() + 1);
                    s.setDaysAtCurrentLevel(0);
                    s.setTrainingDaysRemaining(0);
                    s.setTrainingTotalDays(0);
                    events.add(String.format("员工 %s（%s）已完成晋升培训，正式升至 Lv%d！",
                            s.getId(), s.getPosition().getDisplayName(), s.getLevel()));
                }
            } else {
                int required = trainingRequiredDays(state, s);
                if (required > 0) {
                    s.setDaysAtCurrentLevel(s.getDaysAtCurrentLevel() + 1);
                    if (s.getDaysAtCurrentLevel() == required) {
                        events.add(String.format("员工 %s（%s）在岗时长已达标，可支付培训费升级至 Lv%d",
                                s.getId(), s.getPosition().getDisplayName(), s.getLevel() + 1));
                    }
                }
            }
        }
        return events;
    }
}
