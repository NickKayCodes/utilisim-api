package com.krath.utilisim.sentinel.gateway;

import com.krath.utilisim.sentinel.foundation.AppUser;
import com.krath.utilisim.sentinel.foundation.scrolls.user.UserResponse;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Read-only user surface.
 *
 * <p>The previous {@code GET /search/by-email} handler accepted an {@link AppUser}
 * request body and returned the raw JPA entity, which exposed the BCrypt password
 * hash to any authenticated caller and allowed account enumeration. It has been
 * removed; the only remaining read is the caller's own account, returned as a
 * {@link UserResponse}.</p>
 */
@RestController
@RequestMapping("api/sentinel/users")
public class UserController {

    @GetMapping("/me")
    public UserResponse me(@AuthenticationPrincipal AppUser user) {
        return UserResponse.from(user);
    }
}
