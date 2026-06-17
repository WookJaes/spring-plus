package org.example.expert.domain.chat.entity;

import java.time.LocalDateTime;

import org.example.expert.domain.user.entity.User;

import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToOne;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@NoArgsConstructor
public class ChatMessage {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY)
	private User sender;

	@ManyToOne(fetch = FetchType.LAZY)
	private ChatRoom chatRoom;

	private String content;

	private LocalDateTime createdAt = LocalDateTime.now();

	public ChatMessage(User sender, ChatRoom chatRoom, String content) {
		this.sender = sender;
		this.chatRoom = chatRoom;
		this.content = content;
		this.createdAt = LocalDateTime.now();
	}
}