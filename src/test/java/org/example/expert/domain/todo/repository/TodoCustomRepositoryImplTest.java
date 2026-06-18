package org.example.expert.domain.todo.repository;

import jakarta.persistence.EntityManager;
import org.example.expert.config.QuerydslConfig;
import org.example.expert.domain.comment.entity.Comment;
import org.example.expert.domain.comment.repository.CommentRepository;
import org.example.expert.domain.manager.entity.Manager;
import org.example.expert.domain.manager.repository.ManagerRepository;
import org.example.expert.domain.todo.dto.response.TodoSearchResponse;
import org.example.expert.domain.todo.entity.Todo;
import org.example.expert.domain.user.entity.User;
import org.example.expert.domain.user.enums.UserRole;
import org.example.expert.domain.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest(properties = {
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect",
        "spring.sql.init.mode=never",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
@Import(QuerydslConfig.class)
class TodoCustomRepositoryImplTest {

    @Autowired
    private TodoRepository todoRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ManagerRepository managerRepository;

    @Autowired
    private CommentRepository commentRepository;

    @Autowired
    private EntityManager entityManager;

    @Test
    void 일정_제목_키워드로_검색할_수_있다() {
        // given
        User owner = saveUser("owner-keyword@test.com", "owner-keyword");
        Todo queryDslTodo = saveTodo("QueryDSL projection practice", owner, LocalDateTime.of(2026, 6, 10, 9, 0));
        saveTodo("Spring security practice", owner, LocalDateTime.of(2026, 6, 11, 9, 0));

        // when
        Page<TodoSearchResponse> result = todoRepository.search(
                "QueryDSL",
                null,
                null,
                null,
                PageRequest.of(0, 10)
        );

        // then
        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).getTitle()).isEqualTo(queryDslTodo.getTitle());
    }

    @Test
    void 생성일_범위로_검색하고_최신순으로_정렬한다() {
        // given
        User owner = saveUser("owner-period@test.com", "owner-period");
        saveTodo("Old todo", owner, LocalDateTime.of(2026, 6, 9, 9, 0));
        saveTodo("First range todo", owner, LocalDateTime.of(2026, 6, 10, 9, 0));
        saveTodo("Second range todo", owner, LocalDateTime.of(2026, 6, 11, 9, 0));
        saveTodo("Latest range todo", owner, LocalDateTime.of(2026, 6, 12, 9, 0));
        saveTodo("Future todo", owner, LocalDateTime.of(2026, 6, 13, 9, 0));

        // when
        Page<TodoSearchResponse> result = todoRepository.search(
                null,
                null,
                LocalDateTime.of(2026, 6, 10, 0, 0),
                LocalDateTime.of(2026, 6, 12, 23, 59),
                PageRequest.of(0, 10)
        );

        // then
        assertThat(result.getContent()).extracting(TodoSearchResponse::getTitle)
                .containsExactly(
                        "Latest range todo",
                        "Second range todo",
                        "First range todo"
                );
    }

    @Test
    void 담당자_닉네임으로_검색할_수_있다() {
        // given
        User owner = saveUser("owner-manager@test.com", "owner-manager");
        User managerKim = saveUser("kim@test.com", "manager-kim");
        User managerLee = saveUser("lee@test.com", "manager-lee");

        Todo kimTodo = saveTodo("Kim manager todo", owner, LocalDateTime.of(2026, 6, 10, 9, 0));
        saveManager(managerKim, kimTodo);

        Todo leeTodo = saveTodo("Lee manager todo", owner, LocalDateTime.of(2026, 6, 11, 9, 0));
        saveManager(managerLee, leeTodo);

        // when
        Page<TodoSearchResponse> result = todoRepository.search(
                null,
                "kim",
                null,
                null,
                PageRequest.of(0, 10)
        );

        // then
        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).getTitle()).isEqualTo(kimTodo.getTitle());
    }

    @Test
    void 검색_결과에_담당자_수와_댓글_수를_반환한다() {
        // given
        User owner = saveUser("owner-count@test.com", "owner-count");
        User managerKim = saveUser("kim-count@test.com", "manager-kim-count");
        User managerLee = saveUser("lee-count@test.com", "manager-lee-count");
        User commenter = saveUser("commenter-count@test.com", "commenter-count");

        Todo todo = saveTodo("QueryDSL count test", owner, LocalDateTime.of(2026, 6, 10, 9, 0));
        saveManager(managerKim, todo);
        saveManager(managerLee, todo);
        saveComment("first comment", commenter, todo);
        saveComment("second comment", commenter, todo);

        // when
        Page<TodoSearchResponse> result = todoRepository.search(
                null,
                "kim",
                null,
                null,
                PageRequest.of(0, 10)
        );

        // then
        TodoSearchResponse response = result.getContent().get(0);
        assertThat(response.getTitle()).isEqualTo("QueryDSL count test");
        assertThat(response.getManagerCount()).isEqualTo(3);
        assertThat(response.getCommentCount()).isEqualTo(2);
    }

    @Test
    void 검색_결과를_페이징해서_반환한다() {
        // given
        User owner = saveUser("owner-paging@test.com", "owner-paging");
        saveTodo("Paging todo 1", owner, LocalDateTime.of(2026, 6, 10, 9, 0));
        saveTodo("Paging todo 2", owner, LocalDateTime.of(2026, 6, 11, 9, 0));
        saveTodo("Paging todo 3", owner, LocalDateTime.of(2026, 6, 12, 9, 0));

        // when
        Page<TodoSearchResponse> result = todoRepository.search(
                "Paging",
                null,
                null,
                null,
                PageRequest.of(0, 2)
        );

        // then
        assertThat(result.getContent()).hasSize(2);
        assertThat(result.getTotalElements()).isEqualTo(3);
        assertThat(result.getContent()).extracting(TodoSearchResponse::getTitle)
                .containsExactly("Paging todo 3", "Paging todo 2");
    }

    private User saveUser(String email, String nickname) {
        return userRepository.save(new User(email, "password", UserRole.USER, nickname));
    }

    private Todo saveTodo(String title, User owner, LocalDateTime createdAt) {
        Todo todo = todoRepository.save(new Todo(title, "contents", "Sunny", owner));
        entityManager.flush();

        entityManager.createQuery("UPDATE Todo t SET t.createdAt = :createdAt WHERE t.id = :id")
                .setParameter("createdAt", createdAt)
                .setParameter("id", todo.getId())
                .executeUpdate();

        entityManager.clear();

        return todoRepository.findById(todo.getId()).orElseThrow();
    }

    private Manager saveManager(User user, Todo todo) {
        return managerRepository.save(new Manager(user, todo));
    }

    private Comment saveComment(String contents, User user, Todo todo) {
        return commentRepository.save(new Comment(contents, user, todo));
    }
}
