package com.meileme.backend.config;

/** 季节：客流系数 春1.0 / 夏(旺季)1.8 / 秋0.9 / 冬(淡季)0.5 */
public enum Season {
    SPRING("春季", 1.0),
    SUMMER("夏季", 1.8),
    AUTUMN("秋季", 0.9),
    WINTER("冬季", 0.5);

    private final String label;
    private final double trafficCoefficient;

    Season(String label, double trafficCoefficient) {
        this.label = label;
        this.trafficCoefficient = trafficCoefficient;
    }

    public String getLabel() { return label; }

    public double getTrafficCoefficient() { return trafficCoefficient; }
}
