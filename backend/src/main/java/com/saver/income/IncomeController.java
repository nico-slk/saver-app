package com.saver.income;

import com.saver.common.Months;
import com.saver.income.IncomeDtos.AmountChangeRequest;
import com.saver.income.IncomeReceiptService.ReceiptRequest;
import com.saver.income.IncomeReceiptService.ReceiptResponse;
import com.saver.income.IncomeDtos.CreateIncomeRequest;
import com.saver.income.IncomeDtos.IncomeResponse;
import com.saver.income.IncomeDtos.UpdateIncomeRequest;
import com.saver.security.CurrentUser;
import com.saver.user.UserTime;
import jakarta.validation.Valid;
import java.time.YearMonth;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/incomes")
public class IncomeController {

    private final IncomeService service;
    private final IncomeReceiptService receipts;
    private final UserTime time;

    public IncomeController(IncomeService service, IncomeReceiptService receipts, UserTime time) {
        this.service = service;
        this.receipts = receipts;
        this.time = time;
    }

    @GetMapping
    public List<IncomeResponse> list(@CurrentUser UUID userId) {
        YearMonth current = time.currentMonth(userId);
        return service.list(userId).stream().map(i -> IncomeService.toResponse(i, current)).toList();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public IncomeResponse create(@CurrentUser UUID userId, @Valid @RequestBody CreateIncomeRequest req) {
        return IncomeService.toResponse(service.create(userId, req), time.currentMonth(userId));
    }

    @PatchMapping("/{id}")
    public IncomeResponse update(@CurrentUser UUID userId, @PathVariable UUID id,
                                 @Valid @RequestBody UpdateIncomeRequest req) {
        return IncomeService.toResponse(service.update(userId, id, req), time.currentMonth(userId));
    }

    @PostMapping("/{id}/amounts")
    public IncomeResponse changeAmount(@CurrentUser UUID userId, @PathVariable UUID id,
                                       @Valid @RequestBody AmountChangeRequest req) {
        return IncomeService.toResponse(service.changeAmount(userId, id, req), time.currentMonth(userId));
    }

    @GetMapping("/{id}/receipts")
    public List<ReceiptResponse> receipts(@CurrentUser UUID userId, @PathVariable UUID id, @RequestParam String month) {
        return receipts.list(userId, id, Months.parse(month));
    }

    @PostMapping("/{id}/receipts")
    @ResponseStatus(HttpStatus.CREATED)
    public List<ReceiptResponse> receive(@CurrentUser UUID userId, @PathVariable UUID id, @Valid @RequestBody ReceiptRequest req) {
        return receipts.receive(userId, id, req);
    }

    @DeleteMapping("/receipts/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteReceipt(@CurrentUser UUID userId, @PathVariable UUID id) {
        receipts.delete(userId, id);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@CurrentUser UUID userId, @PathVariable UUID id) {
        service.delete(userId, id);
    }
}
