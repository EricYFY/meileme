package com.meileme.resort.service;

import com.meileme.resort.config.AbilityType;
import com.meileme.resort.config.BuildingForm;
import com.meileme.resort.config.EvolutionLine;
import com.meileme.resort.config.PositionType;
import com.meileme.resort.config.SkillEffectKey;
import com.meileme.resort.model.BuildingInstance;
import com.meileme.resort.model.DailyPreview;
import com.meileme.resort.model.DailySettle;
import com.meileme.resort.model.DemandResult;
import com.meileme.resort.model.GameState;
import com.meileme.resort.model.StaffInstance;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

/**
 * 财务结算：当日总收入 = 房屋收益（按间计价）+ 配套收益；日常运营无固定支出。
 */
@Service
public class FinanceService {

    private final ComfortService comfortService;
    private final SkillTreeService skillTree;

    public FinanceService(ComfortService comfortService, SkillTreeService skillTree) {
        this.comfortService = comfortService;
        this.skillTree = skillTree;
    }

    /**
     * 单间实际房屋收益（含全部乘数）：形态基础房屋收益 × 房屋收益技能倍率 ×（合并产物再加成）×（1 + 内装收益加成）。
     * 今日预计、日结与前端「当前房屋收益（每间）」统一复用本方法，保证展示与结算一致。
     */
    public double pricePerRoom(GameState state, BuildingInstance b) {
        BuildingForm f = b.getForm();
        double price = f.pricePerRoomOf(b.getFormLevel())
                * skillTree.multiplier(state, SkillEffectKey.ROOM_PRICE_MULT);
        if (f.getLine() == EvolutionLine.MERGE) {
            price *= (1 + skillTree.flat(state, SkillEffectKey.MERGE_BONUS));
        }
        price *= (1 + b.interiorRevenueBonus());
        return price;
    }

    /** 今日预计纯投影：复用结算公式但不扣减资金、不修改累计统计 */
    public DailyPreview projection(GameState state, DemandResult demand) {
        DailyPreview p = new DailyPreview();
        p.setPotentialBySegment(new java.util.EnumMap<>(demand.getPotentialBySegment()));
        p.setIntentBySegment(new java.util.EnumMap<>(demand.getIntentBySegment()));
        p.setServedBySegment(new java.util.EnumMap<>(demand.getServedBySegment()));
        p.setTotalRooms(demand.getTotalRooms());
        p.setOccupiedRooms(demand.getOccupiedRooms());
        p.setOccupancyRate(demand.getTotalRooms() <= 0 ? 0
                : (double) demand.getOccupiedRooms() / demand.getTotalRooms());
        p.setExpectedGuests(demand.getTotalServed());

        double supportPriceMult = skillTree.multiplier(state, SkillEffectKey.SUPPORT_PRICE_MULT);

        // —— 房屋收益（按间计价，含技能 / 合并 / 内装乘数） ——
        double accommodationRevenue = 0;
        for (BuildingInstance b : state.aliveAccommodationBuildings()) {
            int sold = demand.getSoldRoomsByBuilding().getOrDefault(b.getId(), 0);
            accommodationRevenue += sold * pricePerRoom(state, b);
        }

        // —— 配套收益 ——
        double supportRevenue = 0;
        for (BuildingInstance b : state.aliveSupportBuildings()) {
            double count = demand.getSupportServiceByBuilding().getOrDefault(b.getId(), 0d);
            BuildingForm f = b.getForm();
            supportRevenue += count * f.unitPriceOf(b.getFormLevel()) * supportPriceMult;
        }

        double totalRevenue = accommodationRevenue + supportRevenue;

        p.setAccommodationRevenue(accommodationRevenue);
        p.setSupportRevenue(supportRevenue);
        p.setRevenue(totalRevenue);
        p.setNetProfit(totalRevenue);
        return p;
    }

    /** 按当日客流分配结果完成资金结算，返回填满数值的日结快照 */
    public DailySettle settle(GameState state, DemandResult demand) {
        DailySettle settle = new DailySettle();
        settle.setDay(state.getDay());
        settle.setDateDisplay(state.currentDateDisplay());

        settle.setPotentialBySegment(new java.util.EnumMap<>(demand.getPotentialBySegment()));
        settle.setConversionBySegment(new java.util.EnumMap<>(demand.getConversionBySegment()));
        settle.setIntentBySegment(new java.util.EnumMap<>(demand.getIntentBySegment()));
        settle.setServedBySegment(new java.util.EnumMap<>(demand.getServedBySegment()));
        settle.setTotalPotential(demand.getTotalPotential());
        settle.setTotalIntent(demand.getTotalIntent());
        settle.setTotalServed(demand.getTotalServed());
        settle.setOverflowGuests(demand.getOverflowGuests());
        settle.setTotalRooms(demand.getTotalRooms());
        settle.setOccupiedRooms(demand.getOccupiedRooms());
        settle.setOccupancyRate(demand.getTotalRooms() <= 0 ? 0
                : (double) demand.getOccupiedRooms() / demand.getTotalRooms());

        double supportPriceMult = skillTree.multiplier(state, SkillEffectKey.SUPPORT_PRICE_MULT);

        // —— 房屋收益（按间计价：售出间数 × 含全部乘数的单间收益） ——
        double accommodationRevenue = 0;
        Map<String, Double> roomRevenueById = new HashMap<>();
        for (BuildingInstance b : state.aliveAccommodationBuildings()) {
            int sold = demand.getSoldRoomsByBuilding().getOrDefault(b.getId(), 0);
            double revenue = sold * pricePerRoom(state, b);
            roomRevenueById.put(b.getId(), revenue);
            accommodationRevenue += revenue;
        }

        // —— 配套收益（服务人次 × 该形态固定单人收益） ——
        double supportRevenue = 0;
        for (BuildingInstance b : state.aliveSupportBuildings()) {
            double count = demand.getSupportServiceByBuilding().getOrDefault(b.getId(), 0d);
            BuildingForm f = b.getForm();
            supportRevenue += count * f.unitPriceOf(b.getFormLevel()) * supportPriceMult;
        }

        double totalRevenue = accommodationRevenue + supportRevenue;
        double netProfit = totalRevenue;

        settle.setAccommodationRevenue(accommodationRevenue);
        settle.setSupportRevenue(supportRevenue);
        settle.setTotalRevenue(totalRevenue);
        settle.setNetProfit(netProfit);

        state.setFunds(state.getFunds() + netProfit);
        settle.setFundsAfter(state.getFunds());

        // —— 累计统计（成就判定） ——
        state.getStats().setCumulativeIncome(state.getStats().getCumulativeIncome() + totalRevenue);
        state.getStats().setTotalAssets(state.getFunds());
        state.getStats().setTotalGuests(state.getStats().getTotalGuests() + demand.getTotalServed());
        for (com.meileme.resort.config.CustomerSegment seg : com.meileme.resort.config.CustomerSegment.values()) {
            state.getStats().addSegmentGuests(seg, demand.getServedBySegment().getOrDefault(seg, 0d));
        }

        // —— 人力供给提示（便于复盘） ——
        Map<AbilityType, Double> ratios = comfortService.supplyRatios(state);
        StringBuilder shortage = new StringBuilder();
        for (Map.Entry<AbilityType, Double> e : ratios.entrySet()) {
            if (e.getValue() < 1.0) {
                if (shortage.length() > 0) {
                    shortage.append("、");
                }
                shortage.append(e.getKey().getDisplayName())
                        .append(String.format("%.0f%%", e.getValue() * 100));
            }
        }
        if (shortage.length() > 0) {
            settle.getEvents().add("人力供给不足：" + shortage + "（仅降低相关建筑舒适度）");
        }

        // —— 员工概况 ——
        int staffCount = state.aliveStaffs().size();
        if (staffCount == 0) {
            settle.getEvents().add("当前无在册员工，相关建筑舒适度将降至保底值");
        } else {
            StringBuilder sb = new StringBuilder("在册员工 " + staffCount + " 人：");
            for (PositionType p : PositionType.values()) {
                int n = 0;
                for (StaffInstance s : state.aliveStaffs()) {
                    if (s.getPosition() == p) {
                        n++;
                    }
                }
                if (n > 0) {
                    sb.append(p.getDisplayName()).append("×").append(n).append(" ");
                }
            }
            settle.getEvents().add(sb.toString().trim());
        }

        return settle;
    }
}
