package com.meileme.backend.config;

/** 建筑类型：建造成本 / 每日维护成本 / 容纳人数 / 是否需厨师 / 是否需维修 */
public enum BuildingType {
    STANDARD_ROOM("普通客房", 3000, 120,  2, false, false),
    LUXURY_SUITE ("豪华套房", 8000, 350,  4, false, false),
    RESTAURANT   ("餐厅",     6000, 220,  8, true,  false),
    SPA          ("温泉池",   7500, 280,  6, false, true ),
    VIEWPOINT    ("观景台",   4000, 150, 10, false, false),
    PARKING      ("停车场",   2500,  80, 20, false, false);

    private final String label;
    private final double buildCost;
    private final double dailyMaintenance;
    private final int capacity;
    private final boolean requiresChef;
    private final boolean requiresMaintenance;

    BuildingType(String label, double buildCost, double dailyMaintenance, int capacity,
                 boolean requiresChef, boolean requiresMaintenance) {
        this.label = label;
        this.buildCost = buildCost;
        this.dailyMaintenance = dailyMaintenance;
        this.capacity = capacity;
        this.requiresChef = requiresChef;
        this.requiresMaintenance = requiresMaintenance;
    }

    public String getLabel() { return label; }

    public double getBuildCost() { return buildCost; }

    public double getDailyMaintenance() { return dailyMaintenance; }

    public int getCapacity() { return capacity; }

    public boolean isRequiresChef() { return requiresChef; }

    public boolean isRequiresMaintenance() { return requiresMaintenance; }

    /** 是否为客房类建筑（普通客房 / 豪华套房） */
    public boolean isRoom() { return this == STANDARD_ROOM || this == LUXURY_SUITE; }
}
