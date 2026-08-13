package com.tyut.aiinterview.mapper;

import lombok.Data;

@Data
public class CompanyAnalyticsScoreBucketRow {
    private String bucketKey;
    private String bucketLabel;
    private Long itemCount;
}
