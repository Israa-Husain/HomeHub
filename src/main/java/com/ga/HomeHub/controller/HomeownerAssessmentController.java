package com.ga.HomeHub.controller;

import com.ga.HomeHub.model.HomeownerAssessment;
import com.ga.HomeHub.service.HomeownerAssessmentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/assessments")
@PreAuthorize("hasRole('PROVIDER')")
public class HomeownerAssessmentController {
    private final HomeownerAssessmentService service;
    public record AssessmentRequest(Integer rating,String comment){}

    @PostMapping("/booking/{bookingId}")
    public Map<String, Object> submitAssessment(@PathVariable Long bookingId, @Valid @RequestBody AssessmentRequest request){
        return response(service.submitHomeownerAssessment(bookingId, request.rating(), request.comment()));
    }

    @GetMapping("/booking/{bookingId}")
    public List<Map<String,Object>> getAssessment(@PathVariable Long bookingId){
        return service.getHomeownerAssessments(bookingId).stream().map(this::response).toList();
    }

    private Map<String, Object> response(HomeownerAssessment assessment){
        Map<String, Object> result = new HashMap<>();
        result.put("id", assessment.getId());
        result.put("rating", assessment.getRating());
        result.put("comment", assessment.getComment());
        return result;
    }
}
