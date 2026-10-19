package com.meileme.backend.config;

/** 岗位：每日底薪 / 工作效率(百分比) / 每日负面事件概率(百分比) */
public enum StaffType {
    FRONT_DESK ("前台", 180, 100, 5),
    CLEANING   ("保洁", 160, 100, 8),
    CHEF       ("厨师", 220, 100, 6),
    MAINTENANCE("维修", 200, 100, 3);

    private final String label;
    private final double baseSalary;
    private final double baseEfficiency;   // 百分比，如 100
    private final double baseNegativeProb; // 百分比，如 5

    StaffType(String label, double baseSalary, double baseEfficiency, double baseNegativeProb) {
        this.label = label;
        this.baseSalary = baseSalary;
        this.baseEfficiency = baseEfficiency;
        this.baseNegativeProb = baseNegativeProb;
    }

    public String getLabel() { return label; }

    public double getBaseSalary() { return baseSalary; }

    public double getBaseEfficiency() { return baseEfficiency; }

    public double getBaseNegativeProb() { return baseNegativeProb; }
}
