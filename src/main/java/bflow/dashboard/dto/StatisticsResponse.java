package bflow.dashboard.dto;

import bflow.dashboard.enums.StatisticsPeriod;

import java.time.LocalDate;
import java.util.List;

public record StatisticsResponse(
        List<MonthlyPoint> points,
        StatisticsPeriod period,
        LocalDate startDate,
        LocalDate endDate
) { }
