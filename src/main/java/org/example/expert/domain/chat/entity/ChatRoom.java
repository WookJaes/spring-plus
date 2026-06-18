package org.example.expert.domain.chat.entity;

import java.time.LocalDateTime;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@NoArgsConstructor
public class ChatRoom {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String name;

    private LocalDateTime createdAt = LocalDateTime.now();

    public ChatRoom (String name) {
        this.name = name;
        this.createdAt = LocalDateTime.now();
    }
}
