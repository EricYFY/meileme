package com.meileme.resort.service;

import com.meileme.resort.config.GameConfig;
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

    private final SkillTreeService skillTree;
    private final IdGenerator idGenerator;

    public StaffService(SkillTreeService skillTree, IdGenerator idGenerator) {
        this.skillTree = skillTree;
        this.idGenerator = idGenerator;
    }

    /** 招聘一次性花费（仅 Lv1，含技能减免） */
    public double hireCost(GameState state, PositionType position) {
        return position.hireCostOf(GameConfig.STAFF_MIN_LEVEL)
                * skillTree.discount(state, SkillEffectKey.HIRE_COST_DISCOUNT);
    }

    /** 解雇一次性补偿金（复合能力员工按主岗位计算，不叠加） */
    public double fireCompensation(GameState state, StaffInstance staff) {
        double base = staff.getPosition().compensationOf(staff.getLevel(), staff.getWorkDays());
        return base * skillTree.discount(state, SkillEffectKey.FIRE_COMPENSATION_DISCOUNT);
    }

    /** 当前等级升到下一级所需的在岗累计游戏日（含技能倍率）；已满级返回 0 */
    public int trainingRequiredDays(GameState state, StaffInstance staff) {
        if (staff == null || staff.getPosition() == null || staff.getLevel() >= GameConfig.STAFF_MAX_LEVEL) {
            return 0;
        }
        double daysMult = skillTree.discount(state, SkillEffectKey.STAFF_UPGRADE_DAYS_MULT);
        return (int) Math.max(1, Math.round(
                staff.getPosition().workDaysToNextLevel(staff.getLevel()) * daysMult));
    }

    /** 当前等级升到下一级所需的一次性培训费；已满级返回 0 */
    public double trainingCost(StaffInstance staff) {
        return staff == null || staff.getPosition() == null
                ? 0d : staff.getPosition().trainCostOf(staff.getLevel());
    }

    /** 是否已满足培训时长条件（满级恒为 false） */
    public boolean trainingReady(GameState state, StaffInstance staff) {
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

    /** 执行培训升级：扣培训费、升 1 级并重置本级在岗时长 */
    public void train(GameState state, StaffInstance staff) {
        double cost = trainingCost(staff);
        state.setFunds(state.getFunds() - cost);
        staff.setLevel(staff.getLevel() + 1);
        staff.setDaysAtCurrentLevel(0);
    }

    /** 执行解雇 */
    public void fire(GameState state, StaffInstance staff) {
        double comp = fireCompensation(state, staff);
        state.setFunds(state.getFunds() - comp);
        staff.setAlive(false);
    }

    /** 每日推进：在岗时长累计（不自动升级）；返回当日「达到培训条件」提示 */
    public List<String> advanceOneDay(GameState state) {
        List<String> events = new ArrayList<>();
        for (StaffInstance s : state.aliveStaffs()) {
            s.setWorkDays(s.getWorkDays() + 1);
            int required = trainingRequiredDays(state, s);
            if (required > 0) {
                s.setDaysAtCurrentLevel(s.getDaysAtCurrentLevel() + 1);
                if (s.getDaysAtCurrentLevel() == required) {
                    events.add(String.format("员工 %s（%s）在岗时长已达标，可支付培训费升级至 Lv%d",
                            s.getId(), s.getPosition().getDisplayName(), s.getLevel() + 1));
                }
            }
        }
        return events;
    }
}
