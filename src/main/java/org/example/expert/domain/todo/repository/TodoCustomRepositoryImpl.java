package org.example.expert.domain.todo.repository;

import com.querydsl.core.types.Projections;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.jpa.JPAExpressions;
import com.querydsl.jpa.impl.JPAQueryFactory;
import lombok.RequiredArgsConstructor;
import org.example.expert.domain.manager.entity.QManager;
import org.example.expert.domain.todo.dto.response.TodoSearchResponse;
import org.example.expert.domain.todo.entity.Todo;
import org.example.expert.domain.user.entity.QUser;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.example.expert.domain.comment.entity.QComment.comment;
import static org.example.expert.domain.manager.entity.QManager.manager;
import static org.example.expert.domain.todo.entity.QTodo.todo;
import static org.example.expert.domain.user.entity.QUser.user;

@RequiredArgsConstructor
public class TodoCustomRepositoryImpl implements TodoCustomRepository {

    private final JPAQueryFactory jpaFactory;

    @Override
    public Optional<Todo> findByIdWithUser(Long todoId) {

        Todo result = jpaFactory
                .selectFrom(todo)
                .leftJoin(todo.user, user).fetchJoin()
                .where(todo.id.eq(todoId))
                .fetchOne();

        return Optional.ofNullable(result);
    }

    @Override
    public Page<TodoSearchResponse> search(
            String keyword, String managerNickname, LocalDateTime startAt, LocalDateTime endAt, Pageable pageable) {

        List<TodoSearchResponse> content = jpaFactory
                .select(Projections.constructor(
                        TodoSearchResponse.class,
                        todo.title,
                        manager.id.countDistinct(),
                        comment.id.countDistinct()
                ))
                .from(todo)
                .leftJoin(todo.managers, manager)
                .leftJoin(todo.comments, comment)
                .where(
                        titleContains(keyword),
                        managerNicknameContains(managerNickname),
                        createdAtBetween(startAt, endAt)
                )
                .groupBy(todo.id, todo.title)
                .orderBy(todo.createdAt.desc())
                .offset(pageable.getOffset())
                .limit(pageable.getPageSize())
                .fetch();

        Long totalCount = jpaFactory
                .select(todo.id.countDistinct())
                .from(todo)
                .where(
                        titleContains(keyword),
                        managerNicknameContains(managerNickname),
                        createdAtBetween(startAt, endAt)
                )
                .fetchOne();

        return new PageImpl<>(content, pageable, totalCount != null ? totalCount : 0);
    }

    private BooleanExpression createdAtBetween(LocalDateTime startAt, LocalDateTime endAt) {
        if (startAt != null && endAt != null) {
            return todo.createdAt.between(startAt, endAt);
        } else if (startAt != null) {
            return todo.createdAt.goe(startAt);
        } else if (endAt != null) {
            return todo.createdAt.loe(endAt);
        } else {
            return null;
        }
    }

    private BooleanExpression managerNicknameContains(String managerNickname) {
        if (managerNickname == null) {
            return null;
        }

        QManager searchManager = new QManager("searchManager");
        QUser searchUser = new QUser("searchUser");

        return JPAExpressions
                .selectOne()
                .from(searchManager)
                .join(searchManager.user, searchUser)
                .where(
                        searchManager.todo.eq(todo),
                        searchUser.nickname.containsIgnoreCase(managerNickname)
                )
                .exists();
    }

    private BooleanExpression titleContains(String keyword) {
        return keyword != null ? todo.title.containsIgnoreCase(keyword) : null;
    }
}
