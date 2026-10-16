package com.example.atlas.capture;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Pageable;
import java.util.*;

public interface CaptureRepository extends JpaRepository<CapturedPayment, UUID> {
    Optional<CapturedPayment> findFirstByPayloadHashOrderByCreatedAtAsc(String hash);
    List<CapturedPayment> findByStatusOrderByCreatedAtDesc(String status, Pageable page);
}
