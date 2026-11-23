package com.saver.expense;

import com.saver.expense.ExpenseDtos.AmountChangeRequest;
import com.saver.expense.ExpenseDtos.CreateExpenseRequest;
import com.saver.expense.ExpenseDtos.ExpenseResponse;
import com.saver.expense.ExpenseDtos.OccurrenceResponse;
import com.saver.expense.ExpenseDtos.UpdateExpenseRequest;
import com.saver.expense.ExpenseDtos.UpdateOccurrenceRequest;
import com.saver.security.CurrentUser;
import com.saver.user.UserTime;
import jakarta.validation.Valid;
import java.time.YearMonth;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class ExpenseController {

    private final ExpenseService service;
    private final OccurrenceService occurrences;
    private final UserTime time;

    public ExpenseController(ExpenseService service, OccurrenceService occurrences, UserTime time) {
        this.service = service;
        this.occurrences = occurrences;
        this.time = time;
    }

    @GetMapping("/api/expenses")
    public List<ExpenseResponse> list(@CurrentUser UUID userId) {
        YearMonth current = time.currentMonth(userId);
        return service.list(userId).stream().map(e -> ExpenseService.toResponse(e, current)).toList();
    }

    @PostMapping("/api/expenses")
    @ResponseStatus(HttpStatus.CREATED)
    public ExpenseResponse create(@CurrentUser UUID userId, @Valid @RequestBody CreateExpenseRequest req) {
        return ExpenseService.toResponse(service.create(userId, req, time.currentMonth(userId)), time.currentMonth(userId));
    }

    @PatchMapping("/api/expenses/{id}")
    public ExpenseResponse update(@CurrentUser UUID userId, @PathVariable UUID id,
                                  @Valid @RequestBody UpdateExpenseRequest req) {
        YearMonth current = time.currentMonth(userId);
        return ExpenseService.toResponse(service.update(userId, id, req, current), current);
    }

    @PostMapping("/api/expenses/{id}/amounts")
    public ExpenseResponse changeAmount(@CurrentUser UUID userId, @PathVariable UUID id,
                                        @Valid @RequestBody AmountChangeRequest req) {
        return ExpenseService.toResponse(service.changeAmount(userId, id, req), time.currentMonth(userId));
    }

    @DeleteMapping("/api/expenses/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@CurrentUser UUID userId, @PathVariable UUID id) {
        service.delete(userId, id);
    }

    @PatchMapping("/api/occurrences/{id}")
    public OccurrenceResponse updateOccurrence(@CurrentUser UUID userId, @PathVariable UUID id,
                                               @Valid @RequestBody UpdateOccurrenceRequest req) {
        return OccurrenceService.toResponse(occurrences.update(userId, id, req), time.today(userId));
    }
}
