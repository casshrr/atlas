package com.example.atlas.insights;

import org.springframework.web.bind.annotation.*;
import java.time.LocalDate;

@RestController
@RequestMapping("/api/insights/spending")
public class SpendingInsightsController {
    private final SpendingInsightsService service;
    public SpendingInsightsController(SpendingInsightsService service) { this.service = service; }
    @GetMapping
    public SpendingInsightsService.Report report(@RequestParam(required = false) LocalDate from,
            @RequestParam(required = false) LocalDate to) { return service.report(from, to); }
}
