package org.example.expert.domain.chat.controller;

import org.example.expert.domain.chat.entity.ChatRoom;
import org.example.expert.domain.chat.repository.ChatRoomRepository;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/chat/rooms")
@RequiredArgsConstructor
public class ChatRoomController {

	private final ChatRoomRepository chatRoomRepository;

	@PostMapping
	public ChatRoom create(@RequestParam String name) {
		ChatRoom room = new ChatRoom(name);
		return chatRoomRepository.save(room);
	}
}
