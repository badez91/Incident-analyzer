package com.eip.common.model;

public record Evidence(
        String type,
        String content,
        double relevance
) {
}
