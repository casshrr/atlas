package com.example.atlas.reimbursement;

import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import java.math.BigDecimal;
import java.util.*;

public interface ReimbursementRepository extends JpaRepository<Reimbursement, UUID> {
    List<Reimbursement> findByExpenseIdOrderByReceivedOnDescCreatedAtDesc(UUID expenseId);
    Optional<Reimbursement> findByIdAndExpenseId(UUID id, UUID expenseId);
    boolean existsByExpenseId(UUID expenseId);

    @Query("select r.expenseId as expenseId, sum(r.amount) as amount from Reimbursement r where r.expenseId in :ids group by r.expenseId")
    List<ExpenseTotal> totalsFor(@Param("ids") Collection<UUID> ids);
    interface ExpenseTotal { UUID getExpenseId(); BigDecimal getAmount(); }
}
