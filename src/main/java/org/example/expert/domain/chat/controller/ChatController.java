package org.example.expert.domain.chat.controller;

import java.security.Principal;

import org.example.expert.domain.chat.dto.request.ChatMessageRequest;
import org.example.expert.domain.chat.entity.ChatMessage;
import org.example.expert.domain.chat.entity.ChatRoom;
import org.example.expert.domain.chat.repository.ChatMessageRepository;
import org.example.expert.domain.chat.repository.ChatRoomRepository;
import org.example.expert.domain.common.exception.InvalidRequestException;
import org.example.expert.domain.user.entity.User;
import org.example.expert.interceptor.AuthenticatedUser;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;

import lombok.RequiredArgsConstructor;

@Controller
@RequiredArgsConstructor
public class ChatController {

    private final ChatMessageRepository chatMessageRepository;
    private final ChatRoomRepository chatRoomRepository;
    private final SimpMessagingTemplate messagingTemplate;


    @MessageMapping("/chat.send")
    public void send(ChatMessageRequest dto, Principal principal) {

        User sender = AuthenticatedUser.fromPrincipal(principal);

        ChatRoom room = chatRoomRepository
            .findById(dto.getRoomId())
            .orElseThrow(() -> new InvalidRequestException("Chat room not found"));

        ChatMessage message= new ChatMessage(sender, room, dto.getContent());

        chatMessageRepository.save(message);

        // 채팅방으로 메시지를 발송한다.
        messagingTemplate.convertAndSend("/sub/chat/" + room.getId(), dto);
    }
}
