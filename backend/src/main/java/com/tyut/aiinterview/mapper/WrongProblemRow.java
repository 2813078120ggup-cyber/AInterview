package com.tyut.aiinterview.mapper;

import lombok.Data;

@Data
public class WrongProblemRow {
    private Long id;
    private String title;
    private String slug;
    private String difficulty;
    private Integer mySubmitCount;
    private Integer favorited;
    private Integer hasNote;
}
