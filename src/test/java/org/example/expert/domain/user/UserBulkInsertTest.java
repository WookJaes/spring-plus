package org.example.expert.domain.user;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.BatchPreparedStatementSetter;
import org.springframework.jdbc.core.JdbcTemplate;

@EnabledIfEnvironmentVariable(named = "BULK_INSERT_USERS", matches = "true")
@SpringBootTest
class UserBulkInsertTest {

    private static final int TOTAL_COUNT = 1_000_000;
    private static final int BATCH_SIZE = 10_000;

    // 매번 BCrypt 암호화를 수행하면 데이터 생성 자체가 지나치게 느려지므로,
    // 테스트 데이터에서는 미리 암호화된 동일한 비밀번호를 사용한다.
    private static final String ENCODED_PASSWORD =
            "$2a$10$7EqJtq98hPqEX7fNZaFWoO5Y5g6D7iF1p9KQ3L4M5N6O7P8Q9R0S.";

    private static final String[] NICKNAME_PREFIXES = {
            "swift", "bright", "calm", "clever", "happy",
            "lucky", "mighty", "silent", "sunny", "wild"
    };

    private static final String[] NICKNAME_SUFFIXES = {
            "bear", "cat", "eagle", "fox", "lion",
            "otter", "panda", "rabbit", "tiger", "wolf"
    };

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void jdbcBulkInsert로_유저_100만건을_생성한다() {
        String runId = UUID.randomUUID()
                .toString()
                .replace("-", "");
        LocalDateTime now = LocalDateTime.now();
        long startedAt = System.currentTimeMillis();
        AtomicReference<String> sampleNickname = new AtomicReference<>();

        for (int start = 0; start < TOTAL_COUNT; start += BATCH_SIZE) {
            int currentBatchSize = Math.min(BATCH_SIZE, TOTAL_COUNT - start);
            insertBatch(runId, now, start, currentBatchSize, sampleNickname);

            System.out.printf(
                    "Bulk Insert 진행률: %,d / %,d%n",
                    start + currentBatchSize,
                    TOTAL_COUNT
            );
        }

        Long insertedCount = countInsertedUsers(runId);

        long elapsedMillis = System.currentTimeMillis() - startedAt;

        System.out.printf("Bulk Insert 완료: %,d건, 소요 시간: %,dms%n", insertedCount, elapsedMillis);
        System.out.println("Postman 조회용 닉네임 예시: " + sampleNickname.get());

        assertThat(insertedCount).isEqualTo(TOTAL_COUNT);
    }

    private void insertBatch(
            String runId,
            LocalDateTime now,
            int batchStart,
            int batchSize,
            AtomicReference<String> sampleNickname
    ) {
        String sql = """
                INSERT INTO users (
                    email,
                    password,
                    user_role,
                    nickname,
                    created_at,
                    modified_at
                )
                VALUES (?, ?, ?, ?, ?, ?)
                """;

        jdbcTemplate.batchUpdate(sql, new BatchPreparedStatementSetter() {
            @Override
            public void setValues(PreparedStatement ps, int index) throws SQLException {
                int sequence = batchStart + index;

                ps.setString(1, "bulk-" + runId + "-" + sequence + "@test.com");
                ps.setString(2, ENCODED_PASSWORD);
                ps.setString(3, "USER");

                String nickname = createUniqueRandomNickname(runId, sequence);
                ps.setString(4, nickname);
                ps.setTimestamp(5, Timestamp.valueOf(now));
                ps.setTimestamp(6, Timestamp.valueOf(now));

                if (sequence == 0) {
                    sampleNickname.set(nickname);
                }
            }

            @Override
            public int getBatchSize() {
                return batchSize;
            }
        });
    }

    private Long countInsertedUsers(String runId) {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM users WHERE email LIKE ?",
                Long.class,
                "bulk-" + runId + "-%@test.com"
        );
    }

    private String createUniqueRandomNickname(String runId, int sequence) {
        ThreadLocalRandom random = ThreadLocalRandom.current();
        String prefix = NICKNAME_PREFIXES[random.nextInt(NICKNAME_PREFIXES.length)];
        String suffix = NICKNAME_SUFFIXES[random.nextInt(NICKNAME_SUFFIXES.length)];

        // sequence를 포함해 같은 실행 안에서는 닉네임이 절대 중복되지 않는다.
        return prefix + "-" + suffix + "-" + runId + "-" + sequence;
    }
}
