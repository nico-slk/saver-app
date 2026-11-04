package com.saver.user;

import com.saver.common.ApiException;
import com.saver.security.CurrentUser;
import com.saver.user.UserDtos.UpdateUserRequest;
import com.saver.user.UserDtos.UserResponse;
import jakarta.validation.Valid;
import java.time.DateTimeException;
import java.time.ZoneId;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/me")
public class UserController {

    private final UserRepository users;

    public UserController(UserRepository users) {
        this.users = users;
    }

    @GetMapping
    @Transactional(readOnly = true)
    public UserResponse me(@CurrentUser UUID userId) {
        return UserResponse.from(load(userId));
    }

    @PatchMapping
    @Transactional
    public UserResponse update(@CurrentUser UUID userId, @Valid @RequestBody UpdateUserRequest req) {
        User user = load(userId);
        if (req.displayName() != null) user.setDisplayName(req.displayName().trim());
        if (req.locale() != null) user.setLocale(req.locale());
        if (req.baseCurrency() != null) user.setBaseCurrency(req.baseCurrency());
        if (req.timezone() != null) {
            try {
                user.setTimezone(ZoneId.of(req.timezone()).getId());
            } catch (DateTimeException e) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "user.timezone.invalid");
            }
        }
        return UserResponse.from(user);
    }

    private User load(UUID id) {
        return users.findById(id).orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "auth.user_not_found"));
    }
}
