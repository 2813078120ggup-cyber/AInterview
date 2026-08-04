package com.tyut.aiinterview.common;

import java.util.List;

public record PageResult<T>(List<T> records, long total, long pageNo, long pageSize) {
    public static <T> PageResult<T> of(List<T> records, long total, long pageNo, long pageSize) {
        return new PageResult<>(records, total, pageNo, pageSize);
    }
}
