package com.eip.similarity.controller;

import com.eip.common.dto.SimilaritySearchRequest;
import com.eip.common.dto.SimilaritySearchResult;
import com.eip.similarity.service.SimilarityService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/similarity")
public class SimilarityController {

    private final SimilarityService similarityService;

    public SimilarityController(SimilarityService similarityService) {
        this.similarityService = similarityService;
    }

    @PostMapping("/search")
    public ResponseEntity<SimilaritySearchResult> searchSimilar(@RequestBody SimilaritySearchRequest request) {
        SimilaritySearchResult result = similarityService.searchSimilar(request);
        return ResponseEntity.ok(result);
    }
}
