package com.meileme.backend.config;

/** 团建大单规格：需要客房数 / 总收益 / 违约罚款 / 必备配套(餐厅 / 温泉 / 观景台) */
public enum TeamOrderSize {
    SMALL ("小型团建",  6, 18000,  6000, true,  false, false),
    MEDIUM("中型团建", 12, 40000, 13000, true,  true,  false),
    LARGE ("大型团建", 20, 75000, 25000, true,  true,  true );

    private final String label;
    private final int requiredRooms;
    private final double revenue;
    private final double penalty;
    private final boolean requiresRestaurant;
    private final boolean requiresSpa;
    private final boolean requiresViewpoint;

    TeamOrderSize(String label, int requiredRooms, double revenue, double penalty,
                  boolean requiresRestaurant, boolean requiresSpa, boolean requiresViewpoint) {
        this.label = label;
        this.requiredRooms = requiredRooms;
        this.revenue = revenue;
        this.penalty = penalty;
        this.requiresRestaurant = requiresRestaurant;
        this.requiresSpa = requiresSpa;
        this.requiresViewpoint = requiresViewpoint;
    }

    public String getLabel() { return label; }

    public int getRequiredRooms() { return requiredRooms; }

    public double getRevenue() { return revenue; }

    public double getPenalty() { return penalty; }

    public boolean isRequiresRestaurant() { return requiresRestaurant; }

    public boolean isRequiresSpa() { return requiresSpa; }

    public boolean isRequiresViewpoint() { return requiresViewpoint; }
}
