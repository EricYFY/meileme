package com.meileme.backend.model;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class Merchant {
    private String id;
    private Coordinate location;
    private float rating = 5.0f; // 综合评分: 1.0 to 5.0
    private int completedOrders = 0;
    private int ongoingOrders = 0;

    // === 商家自定义经营参数 (支持单店独立配置) ===
    private double minOrderPrice = 15.0;     // 客单价下限 (元)
    private double maxOrderPrice = 35.0;     // 客单价上限 (元)
    private double materialCostRate = 0.50;  // 订单耗材成本比例 (0.10 ~ 0.90，默认 50%)
    private double rentCost = 100.0;         // 每日门面租金 (元/天)
    private double laborCost = 150.0;        // 每日人工成本 (元/天)
    private double adSpend = 20.0;           // 每日广告投入 (元/天)
    private double dailyFranchiseFee = 50.0; // 每日平台加盟费 (元/天)

    // === 商家累计财务收支核算字段 ===
    private double totalRevenue = 0.0;        // 累计总交易流水 (所有订单原价总和)
    private double totalCommissionPaid = 0.0; // 累计支付的平台抽成 (25%)
    private double totalFranchisePaid = 0.0;  // 累计支付的平台加盟费 (每天50)
    private double totalMaterialCost = 0.0;   // 累计耗材成本
    private double totalFixedCost = 0.0;      // 累计门面租金 + 人工成本
    private double totalAdSpend = 0.0;        // 累计广告成本
    private double totalFines = 0.0;          // 累计超时违约罚款
    private double totalIncome = 0.0;         // 累计实际净盈利 (netProfit)

    public Merchant(Coordinate location) {
        this.id = UUID.randomUUID().toString();
        this.location = location;
        this.rating = 5.0f; // 初始满分
        this.completedOrders = 0;
        this.ongoingOrders = 0;
        this.minOrderPrice = 15.0;
        this.maxOrderPrice = 35.0;
        this.materialCostRate = 0.50;
        this.rentCost = 100.0;
        this.laborCost = 150.0;
        this.adSpend = 20.0;
        this.dailyFranchiseFee = 50.0;
        this.totalRevenue = 0.0;
        this.totalCommissionPaid = 0.0;
        this.totalFranchisePaid = 0.0;
        this.totalMaterialCost = 0.0;
        this.totalFixedCost = 0.0;
        this.totalAdSpend = 0.0;
        this.totalFines = 0.0;
        this.totalIncome = 0.0;
    }

    /**
     * 重新根据所有收支项目精确计算当前实际净利润
     */
    public void recalculateTotalIncome() {
        this.totalIncome = Math.round((
            totalRevenue 
            - totalFranchisePaid 
            - totalCommissionPaid 
            - totalMaterialCost 
            - totalFixedCost 
            - totalAdSpend 
            - totalFines
        ) * 100.0) / 100.0;
    }
}
