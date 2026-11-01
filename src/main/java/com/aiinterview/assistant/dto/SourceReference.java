package com.aiinterview.assistant.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** A PDF passage shown under a chat answer; {@code sourceNumber} matches the [Source N] citations. */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SourceReference {

    private int sourceNumber;

    private Integer pageNumber;

    private String fileName;

    private String snippet;
}
