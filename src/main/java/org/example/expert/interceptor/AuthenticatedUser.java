package org.example.expert.interceptor;

import java.security.Principal;

import org.example.expert.domain.user.entity.User;

import lombok.Getter;

@Getter
public class AuthenticatedUser implements Principal {

    private final User user;
    private final String name;

    public AuthenticatedUser(User user) {
        this.user = user;
        this.name = user.getNickname();
    }

    // Principal에서 User 꺼내기
    public static User fromPrincipal(Principal principal) {
        return ((AuthenticatedUser) principal).getUser();
    }
}
