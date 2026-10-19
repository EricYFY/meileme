package com.meileme.backend.config;

/**
 * 薪资档位：
 * 高薪：薪资+30% / 效率120% / 负面事件概率减半
 * 标准：薪资100%  / 效率100% / 负面事件概率100%
 * 廉价：薪资-30% / 效率 80% / 负面事件概率翻倍
 */
public enum SalaryMode {
    NORMAL(1.00, 1.00, 1.00),
    HIGH  (1.30, 1.20, 0.50),
    LOW   (0.70, 0.80, 2.00);

    private final double salaryMultiplier;
    private final double efficiencyMultiplier;
    private final double negativeEventMultiplier;

    SalaryMode(double salaryMultiplier, double efficiencyMultiplier, double negativeEventMultiplier) {
        this.salaryMultiplier = salaryMultiplier;
        this.efficiencyMultiplier = efficiencyMultiplier;
        this.negativeEventMultiplier = negativeEventMultiplier;
    }

    public double getSalaryMultiplier() { return salaryMultiplier; }

    public double getEfficiencyMultiplier() { return efficiencyMultiplier; }

    public double getNegativeEventMultiplier() { return negativeEventMultiplier; }

    /** 前端传参 normal / high / low */
    public static SalaryMode fromString(String s) {
        if (s == null) return NORMAL;
        switch (s.trim().toLowerCase()) {
            case "high": return HIGH;
            case "low":  return LOW;
            default:     return NORMAL;
        }
    }
}
