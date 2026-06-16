package org.example.expert.domain.log.entity;

import java.time.LocalDateTime;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(name ="log")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Log {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	private String message;
	private LocalDateTime createdAt;

	public Log(String message) {
		this.message = message;
		this.createdAt = LocalDateTime.now();
	}
}
