package org.example.expert.domain.log.service;

import lombok.RequiredArgsConstructor;
import org.example.expert.domain.log.entity.Log;
import org.example.expert.domain.log.repository.LogRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class LogService {

	private final LogRepository logRepository;

	@Transactional(propagation = Propagation.REQUIRES_NEW)
	public void saveLog(Long requesterId, Long todoId, Long managerUserId) {
		logRepository.save(new Log(createManagerSaveMessage(requesterId, todoId, managerUserId)));
	}

	private String createManagerSaveMessage(Long requesterId, Long todoId, Long managerUserId) {
		return "매니저 등록 요청"
			+ " requesterId=" + requesterId
			+ ", todoId=" + todoId
			+ ", managerUserId=" + managerUserId;
	}
}