package com.saver.account;

import com.saver.account.AccountDtos.AccountResponse;
import com.saver.account.AccountDtos.AccountsOverview;
import com.saver.account.AccountDtos.AdjustmentRequest;
import com.saver.account.AccountDtos.CreateAccountRequest;
import com.saver.account.AccountDtos.MovementResponse;
import com.saver.account.AccountDtos.TransferRequest;
import com.saver.account.AccountDtos.UpdateAccountRequest;
import com.saver.security.CurrentUser;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class AccountController {

    private final AccountService service;

    public AccountController(AccountService service) {
        this.service = service;
    }

    @GetMapping("/accounts")
    public AccountsOverview overview(@CurrentUser UUID userId) {
        return service.overview(userId);
    }

    @PostMapping("/accounts")
    @ResponseStatus(HttpStatus.CREATED)
    public AccountResponse create(@CurrentUser UUID userId, @Valid @RequestBody CreateAccountRequest req) {
        return service.response(userId, service.create(userId, req));
    }

    @PatchMapping("/accounts/{id}")
    public AccountResponse update(@CurrentUser UUID userId, @PathVariable UUID id, @Valid @RequestBody UpdateAccountRequest req) {
        return service.response(userId, service.update(userId, id, req));
    }

    @DeleteMapping("/accounts/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@CurrentUser UUID userId, @PathVariable UUID id) {
        service.delete(userId, id);
    }

    @GetMapping("/accounts/{id}/movements")
    public List<MovementResponse> movements(@CurrentUser UUID userId, @PathVariable UUID id,
                                            @RequestParam(defaultValue = "100") int limit) {
        return service.movements(userId, id, limit);
    }

    @PostMapping("/accounts/{id}/adjustments")
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String, UUID> adjust(@CurrentUser UUID userId, @PathVariable UUID id, @Valid @RequestBody AdjustmentRequest req) {
        return Map.of("id", service.adjust(userId, id, req).getId());
    }

    @DeleteMapping("/adjustments/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteAdjustment(@CurrentUser UUID userId, @PathVariable UUID id) {
        service.deleteAdjustment(userId, id);
    }

    @PostMapping("/transfers")
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String, Object> transfer(@CurrentUser UUID userId, @Valid @RequestBody TransferRequest req) {
        Transfer t = service.transfer(userId, req);
        return Map.of("id", t.getId(), "fromAmount", t.getFromAmount(), "toAmount", t.getToAmount(),
                "rateType", t.getRateType() == null ? "" : t.getRateType());
    }

    @DeleteMapping("/transfers/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteTransfer(@CurrentUser UUID userId, @PathVariable UUID id) {
        service.deleteTransfer(userId, id);
    }
}
