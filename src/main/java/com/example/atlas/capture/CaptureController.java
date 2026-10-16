package com.example.atlas.capture;

import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;
import java.util.*;

/** Local desktop API only. No Internet-facing receiver or phone-wide notification permission. */
@RestController
@RequestMapping("/api/captures")
public class CaptureController {
    private final CaptureService service;
    public CaptureController(CaptureService service) { this.service = service; }
    @PostMapping public CapturedPayment capture(@Valid @RequestBody CaptureRequest request) { return service.capture(request); }
    @GetMapping public List<CapturedPayment> inbox() { return service.inbox(); }
    @PostMapping("/{id}/approve") public CapturedPayment approve(@PathVariable UUID id, @Valid @RequestBody CaptureService.Approval request) { return service.approve(id, request); }
    @PostMapping("/{id}/dismiss") public CapturedPayment dismiss(@PathVariable UUID id) { return service.dismiss(id); }
}
