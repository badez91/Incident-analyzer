package com.eip.common.model;

import java.util.List;

public record RecommendedActions(
        List<String> immediate,
        List<String> shortTerm,
        List<String> longTerm
) {
}
