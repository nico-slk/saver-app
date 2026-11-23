package com.saver.card;

import com.saver.common.ApiException;
import com.saver.security.CurrentUser;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.Clock;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Tarjetas (agrupan gastos). Borrar = archivar: los gastos y el historial la siguen mostrando. */
@RestController
@RequestMapping("/api/cards")
public class CardController {

    public record CardRequest(
            @NotBlank(message = "validation.required") @Size(max = 60, message = "validation.too_long") String name,
            @Pattern(regexp = "#[0-9a-fA-F]{6}", message = "validation.color") String color) {}

    public record CardUpdate(
            @Size(min = 1, max = 60, message = "validation.too_long") String name,
            @Pattern(regexp = "#[0-9a-fA-F]{6}", message = "validation.color") String color,
            Boolean archived) {}

    public record CardResponse(UUID id, String name, String color, boolean archived) {
        static CardResponse from(Card c) {
            return new CardResponse(c.getId(), c.getName(), c.getColor(), c.isArchived());
        }
    }

    private final CardRepository cards;
    private final Clock clock;

    public CardController(CardRepository cards, Clock clock) {
        this.cards = cards;
        this.clock = clock;
    }

    @GetMapping
    @Transactional(readOnly = true)
    public List<CardResponse> list(@CurrentUser UUID userId) {
        return cards.findByUserIdOrderByCreatedAtAsc(userId).stream().map(CardResponse::from).toList();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Transactional
    public CardResponse create(@CurrentUser UUID userId, @Valid @RequestBody CardRequest req) {
        return CardResponse.from(cards.save(new Card(userId, req.name().trim(),
                req.color() == null ? "#7c3aed" : req.color(), clock.instant())));
    }

    @PatchMapping("/{id}")
    @Transactional
    public CardResponse update(@CurrentUser UUID userId, @PathVariable UUID id, @Valid @RequestBody CardUpdate req) {
        Card c = owned(userId, id);
        if (req.name() != null) c.setName(req.name().trim());
        if (req.color() != null) c.setColor(req.color());
        if (req.archived() != null) c.setArchivedAt(req.archived() ? clock.instant() : null);
        return CardResponse.from(c);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Transactional
    public void archive(@CurrentUser UUID userId, @PathVariable UUID id) {
        owned(userId, id).setArchivedAt(clock.instant());
    }

    private Card owned(UUID userId, UUID id) {
        return cards.findByIdAndUserId(id, userId).orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "card.not_found"));
    }
}
