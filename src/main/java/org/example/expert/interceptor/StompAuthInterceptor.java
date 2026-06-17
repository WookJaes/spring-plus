package org.example.expert.interceptor;

import org.example.expert.config.JwtUtil;
import org.example.expert.domain.common.exception.InvalidRequestException;
import org.example.expert.domain.user.entity.User;
import org.example.expert.domain.user.repository.UserRepository;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class StompAuthInterceptor implements ChannelInterceptor {

	private final JwtUtil jwtUtil;
	private final UserRepository userRepository;

	@Override
	public Message<?> preSend(Message<?> message, MessageChannel channel) {

		StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);

		// CONNECT 연결일 때 JWT 토큰 검증
		if (StompCommand.CONNECT.equals(accessor.getCommand())) {

			String authorization = accessor.getFirstNativeHeader("Authorization");

			if (authorization == null || !authorization.startsWith("Bearer ")) {
				throw new InvalidRequestException("Invalid JWT authorization header");
			}

			String token = jwtUtil.substringToken(authorization);

			Long userId = jwtUtil.getUserId(token);

			User user = userRepository.findById(userId)
				.orElseThrow(() -> new InvalidRequestException("User not found"));

			// 소유자 등록
			accessor.setUser(new AuthenticatedUser(user));
		}

		return message;
	}
}
