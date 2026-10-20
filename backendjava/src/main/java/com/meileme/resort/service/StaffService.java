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
 * 雇佣系统：全局共享人力池。招聘 / 解雇无冷却；员工在岗累计工作时长（按游戏日）达标后自动升级。
 */
@Service
public class StaffService {

    private final SkillTreeService skillTree;
    private final IdGenerator idGenerator;

    public StaffService(SkillTreeService skillTree, IdGenerator idGenerator) {
        this.skillTree = skillTree;
        this.idGenerator = idGenerator;
    }

    /** 招聘一次性花费（含技能减免） */
    public double hireCost(GameState state, PositionType position, int level) {
        return position.hireCostOf(level) * skillTree.discount(state, SkillEffectKey.HIRE_COST_DISCOUNT);
    }

    /** 解雇一次性补偿金（复合能力员工按主岗位计算，不叠加） */
    public double fireCompensation(GameState state, StaffInstance staff) {
        double base = staff.getPosition().compensationOf(staff.getLevel(), staff.getWorkDays());
        return base * skillTree.discount(state, SkillEffectKey.FIRE_COMPENSATION_DISCOUNT);
    }

    /** 员工每日薪资合计（含技能减免） */
    public double totalSalary(GameState state) {
        double mult = skillTree.discount(state, SkillEffectKey.STAFF_SALARY_DISCOUNT);
        double sum = 0;
        for (StaffInstance s : state.aliveStaffs()) {
            sum += s.getPosition().salaryOf(s.getLevel());
        }
        return sum * mult;
    }

    /** 校验招聘；返回 null 表示可执行 */
    public String validateHire(GameState state, PositionType position, int level) {
        if (state == null || state.isGameOver()) {
            return "GAME_NOT_RUNNING";
        }
        if (position == null) {
            return "POSITION_NOT_FOUND";
        }
        if (level < GameConfig.STAFF_MIN_LEVEL || level > GameConfig.STAFF_MAX_LEVEL) {
            return "INVALID_STAFF_LEVEL";
        }
        if (state.getFunds() < hireCost(state, position, level)) {
            return "INSUFFICIENT_FUNDS";
        }
        return null;
    }

    /** 执行招聘（招聘必定成功） */
    public StaffInstance hire(GameState state, PositionType position, int level) {
        double cost = hireCost(state, position, level);
        state.setFunds(state.getFunds() - cost);
        StaffInstance s = new StaffInstance();
        s.setId(idGenerator.next("STAFF"));
        s.setPosition(position);
        s.setLevel(level);
        s.setWorkDays(0);
        s.setDaysAtCurrentLevel(0);
        s.setAlive(true);
        s.setHireDay(state.getDay());
        state.getStaffs().add(s);
        return s;
    }

    /** 执行解雇 */
    public void fire(GameState state, StaffInstance staff) {
        double comp = fireCompensation(state, staff);
        state.setFunds(state.getFunds() - comp);
        staff.setAlive(false);
    }

    /** 每日推进：在岗时长累计 + 自动升级判定；返回当日升级提示 */
    public List<String> advanceOneDay(GameState state) {
        List<String> events = new ArrayList<>();
        double daysMult = skillTree.discount(state, SkillEffectKey.STAFF_UPGRADE_DAYS_MULT);
        for (StaffInstance s : state.aliveStaffs()) {
            s.setWorkDays(s.getWorkDays() + 1);
            s.setDaysAtCurrentLevel(s.getDaysAtCurrentLevel() + 1);
            if (s.getLevel() < GameConfig.STAFF_MAX_LEVEL) {
                int required = (int) Math.max(1, Math.round(
                        s.getPosition().workDaysToNextLevel(s.getLevel()) * daysMult));
                if (s.getDaysAtCurrentLevel() >= required) {
                    s.setLevel(s.getLevel() + 1);
                    s.setDaysAtCurrentLevel(0);
                    events.add(String.format("员工 %s（%s）自动升级至 Lv%d",
                            s.getId(), s.getPosition().getDisplayName(), s.getLevel()));
                }
            }
        }
        return events;
    }
}
