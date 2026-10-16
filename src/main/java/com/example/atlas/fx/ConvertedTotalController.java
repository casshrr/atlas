package com.example.atlas.fx;

import org.springframework.web.bind.annotation.*;
import java.time.LocalDate;

@RestController
@RequestMapping("/api/expenses/converted-total")
public class ConvertedTotalController {
    private final ConvertedTotalService totals;
    public ConvertedTotalController(ConvertedTotalService totals) { this.totals = totals; }

    @GetMapping
    public ConvertedTotalService.ConvertedTotal total(@RequestParam String currency,
            @RequestParam(required = false) String category, @RequestParam(required = false) LocalDate from,
            @RequestParam(required = false) LocalDate to) {
        return totals.calculate(currency, category, from, to);
    }
}
