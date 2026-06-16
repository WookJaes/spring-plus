package org.example.expert.domain.user.controller;

import org.example.expert.config.JwtUtil;
import org.example.expert.config.SecurityConfig;
import org.example.expert.config.SecurityJwtFilter;
import org.example.expert.domain.user.enums.UserRole;
import org.example.expert.domain.user.service.UserAdminService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.Jwts;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Import({SecurityConfig.class, SecurityJwtFilter.class})
@WebMvcTest(UserAdminController.class)
class UserAdminControllerTest {

    private static final String ADMIN_BEARER_TOKEN = "Bearer admin-token";
    private static final String ADMIN_TOKEN = "admin-token";
    private static final String USER_BEARER_TOKEN = "Bearer user-token";
    private static final String USER_TOKEN = "user-token";

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private UserAdminService userAdminService;

    @MockBean
    private JwtUtil jwtUtil;

    @Test
    void 일반_유저는_관리자_API에_접근할_수_없다() throws Exception {
        // given
        when(jwtUtil.substringToken(USER_BEARER_TOKEN)).thenReturn(USER_TOKEN);
        when(jwtUtil.extractClaims(USER_TOKEN)).thenReturn(createClaims(UserRole.USER));

        // when & then
        mockMvc.perform(patch("/admin/users/{userId}", 1L)
                        .header("Authorization", USER_BEARER_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"role\":\"ADMIN\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void 관리자는_관리자_API에_접근할_수_있다() throws Exception {
        // given
        when(jwtUtil.substringToken(ADMIN_BEARER_TOKEN)).thenReturn(ADMIN_TOKEN);
        when(jwtUtil.extractClaims(ADMIN_TOKEN)).thenReturn(createClaims(UserRole.ADMIN));

        // when & then
        mockMvc.perform(patch("/admin/users/{userId}", 1L)
                        .header("Authorization", ADMIN_BEARER_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"role\":\"USER\"}"))
                .andExpect(status().isOk());

        // then
        verify(userAdminService).changeUserRole(eq(1L), any());
    }

    @Test
    void Bearer_형식이_아닌_토큰은_요청할_수_없다() throws Exception {
        // when & then
        mockMvc.perform(patch("/admin/users/{userId}", 1L)
                        .header("Authorization", "invalid-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"role\":\"USER\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void 만료된_토큰은_요청할_수_없다() throws Exception {
        // given
        when(jwtUtil.substringToken(ADMIN_BEARER_TOKEN)).thenReturn(ADMIN_TOKEN);
        when(jwtUtil.extractClaims(ADMIN_TOKEN))
                .thenThrow(new ExpiredJwtException(null, null, "만료된 JWT 토큰입니다."));

        // when & then
        mockMvc.perform(patch("/admin/users/{userId}", 1L)
                        .header("Authorization", ADMIN_BEARER_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"role\":\"USER\"}"))
                .andExpect(status().isUnauthorized());
    }

    private Claims createClaims(UserRole userRole) {
        Claims claims = Jwts.claims().setSubject("1");
        claims.put("email", "user@email.com");
        claims.put("userRole", userRole.name());
        claims.put("nickname", "nickname");
        return claims;
    }
}
