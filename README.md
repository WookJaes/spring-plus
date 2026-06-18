# SPRING PLUS

## 대용량 유저 닉네임 조회 성능 개선

### 1. 테스트 환경

- 실행 환경: 개인 로컬 환경
- 데이터베이스: MySQL
- 테스트 데이터: 유저 1,000,000건
- 데이터 생성 방식: JDBC Bulk Insert
- 조회 도구: Postman
- 검색 조건: 닉네임 정확 일치

<br>

### 2. 대용량 테스트 데이터 생성

JDBC의 `batchUpdate()`를 이용해 유저 데이터 1,000,000건을 Bulk Insert했다.

- 한 번에 10,000건씩 나누어 저장했다.
- 닉네임은 prefix와 suffix를 무작위로 조합했다.
- 실행마다 생성한 UUID와 순번을 닉네임에 포함해 중복을 방지했다.
- 이메일에도 UUID와 순번을 포함해 Unique 제약조건 충돌을 방지했다.
- 테스트 종료 후 이번 실행에서 생성된 데이터 수가 1,000,000건인지 검증했다.
- `BULK_INSERT_USERS=true` 환경변수가 있을 때만 테스트가 실행되도록 안전장치를 적용했다.

```java
@EnabledIfEnvironmentVariable(named = "BULK_INSERT_USERS", matches = "true")
```

<br>

### 3. 닉네임 조회 API

```http
GET /users?nickname={nickname}
Authorization: Bearer {JWT}
```

응답에는 조회된 유저의 `id`, `email`, `nickname`을 포함한다.

<br>

### 4. 측정 방법

- 모든 요청에서 동일한 닉네임을 사용했다.
- Postman에서 동일한 요청을 총 5회 실행했다.
- Postman에 표시되는 전체 HTTP 응답 시간을 기록했다.
- 해당 시간에는 Controller, Service, JPA 조회, JSON 응답 처리 및 로컬 HTTP 통신 시간이 포함된다.
<br>

테스트 닉네임
```text
swift-tiger-124292df03164e7d8f1aeee72e0fc76a-997006
```
<br>

### 5. 최초 조회 성능

최초 조회는 별도의 닉네임 인덱스나 QueryDSL 최적화를 적용하지 않고,
Spring Data JPA 메서드 쿼리로 `User` 엔티티를 조회한 결과이다.

```java
List<User> findAllByNickname(String nickname);
```

| 측정 회차 | 응답 시간 |
|---:|---:|
| 1회 | 3.54초 |
| 2회 | 2.49초 |
| 3회 | 2.47초 |
| 4회 | 1.18초 |
| 5회 | 2.90초 |
| **평균** | **2.516초** |

현재 `nickname` 컬럼에 별도의 인덱스를 적용하지 않았기 때문에,
데이터가 1,000,000건인 환경에서 닉네임이 정확히 일치하는 유저를 찾는 데 평균 2.516초가 소요됐다.

<br>

#### 기본 조회 실행 계획

```sql
EXPLAIN
SELECT id, email, nickname
FROM users
WHERE nickname = 'swift-tiger-124292df03164e7d8f1aeee72e0fc76a-997006';
```

| type | key | rows | Extra |
|---|---|---:|---|
| ALL | NULL | 987,879 | Using where |

- `type`이 `ALL`이므로 전체 테이블 스캔이 발생했다.
- `key`가 `NULL`이므로 조회에 사용된 인덱스가 없다.
- MySQL은 약 987,879개의 행을 확인할 것으로 예상했다.
- 전체 행을 탐색한 후 `WHERE` 조건으로 닉네임이 일치하는 유저를 필터링하고 있다.

이 결과를 이후 인덱스 및 Spring Cache 적용 결과와 비교하기 위한 최초 기준값으로 사용한다.

<br>

### 6. 개선 1: 닉네임 인덱스

`User` 엔티티에 `nickname` 단일 컬럼 인덱스를 선언하고,
애플리케이션 재실행 후 MySQL에서 생성 여부를 확인했다.

```java
@Table(name = "users",
    indexes = {@Index(name = "idx_users_nickname", columnList = "nickname")})
```
<br>
인덱스 확인

```sql
SHOW INDEX FROM users;
```

| 인덱스 이름 | 유일성 | 컬럼 | 방식 |
|---|---|---|---|
| `PRIMARY` | Unique | `id` | BTREE |
| `UK6dotkott2kjsp8vw4d0m25fb7` | Unique | `email` | BTREE |
| `idx_users_nickname` | Non-unique | `nickname` | BTREE |

`idx_users_nickname` 인덱스가 `nickname` 컬럼을 대상으로 정상 생성된 것을 확인했다.
닉네임은 중복될 수 있으므로 Non-unique 인덱스로 생성됐다.

<br>

#### 인덱스 적용 후 조회 성능

기본 조회와 동일한 닉네임으로 Postman 요청을 5회 실행했다.

| 측정 회차 | 응답 시간 |
|---:|---:|
| 1회 | 132ms |
| 2회 | 35ms |
| 3회 | 48ms |
| 4회 | 31ms |
| 5회 | 28ms |
| **평균** | **54.8ms** |

| 구분 | 평균 응답 시간 |
|---|---:|
| 인덱스 적용 전 | 2,516ms |
| 인덱스 적용 후 | 54.8ms |
| 감소한 시간 | 2,461.2ms |
| 개선율 | 약 97.8% |

`nickname` 인덱스를 적용한 뒤 평균 응답 시간이 2,516ms에서 54.8ms로 감소했다.
전체 테이블을 탐색하던 기존 조회와 달리, 닉네임 인덱스를 통해 검색 대상에 빠르게 접근하면서
조회 성능이 크게 개선된 것으로 판단된다.

<br>

#### 인덱스 적용 후 실행 계획

```sql
EXPLAIN
SELECT id, email, nickname
FROM users
WHERE nickname = 'swift-tiger-124292df03164e7d8f1aeee72e0fc76a-997006';
```

| type | possible_keys | key | ref | rows |
|---|---|---|---|---:|
| ref | `idx_users_nickname` | `idx_users_nickname` | const | 1 |

- `type`이 `ALL`에서 `ref`로 변경돼 전체 테이블 스캔이 제거됐다.
- `possible_keys`와 `key`에서 `idx_users_nickname` 인덱스를 사용할 수 있고 실제 사용한 것을 확인했다.
- 검색 값은 상수 조건이므로 `ref`가 `const`로 표시됐다.
- 예상 탐색 행이 약 987,879개에서 1개로 감소했다.

<br>

| 구분 | type | 사용 인덱스 | 예상 탐색 행 |
|---|---|---|---:|
| 인덱스 적용 전 | ALL | 없음 | 987,879 |
| 인덱스 적용 후 | ref | `idx_users_nickname` | 1 |

실행 계획을 통해 응답 시간 개선이 단순한 측정 편차가 아니라,
전체 테이블 스캔이 인덱스 검색으로 변경되면서 발생한 결과임을 확인했다.

<br>

### 7. 개선 2: Spring Cache

동일한 닉네임이 반복 조회되는 경우 DB 접근을 줄이기 위해 Spring Cache와
Caffeine 로컬 캐시를 적용했다.

캐시에 조회 결과가 없는 최초 요청은 Cache Miss가 발생해 DB에서 데이터를 조회한 뒤
그 결과를 캐시에 저장한다. 이후 같은 닉네임 요청은 Cache Hit가 발생해 DB를 거치지 않고
메모리에 저장된 결과를 반환한다.

<br>

#### Cache Miss

| 구분 | 응답 시간 |
|---|---:|
| 최초 조회 | 254ms |

최초 요청은 캐시에 저장된 결과가 없으므로 닉네임 인덱스를 이용해 DB를 조회하고,
조회 결과를 캐시에 저장하는 과정까지 포함됐다.

<br>

#### Cache Hit

Cache Miss 이후 TTL이 만료되기 전에 동일한 닉네임으로 5회 요청했다.

| 측정 회차 | 응답 시간 |
|---:|---:|
| 1회 | 18ms |
| 2회 | 19ms |
| 3회 | 14ms |
| 4회 | 20ms |
| 5회 | 12ms |
| **평균** | **16.6ms** |

| 구분 | 평균 응답 시간 |
|---|---:|
| 인덱스 조회 | 54.8ms |
| Cache Hit | 16.6ms |
| 감소한 시간 | 38.2ms |
| 개선율 | 약 69.7% |

동일한 닉네임을 반복 조회할 때 DB 접근이 생략되면서 평균 응답 시간이
54.8ms에서 16.6ms로 감소했다.

<br>

#### Spring Cache 적용 시 주의사항

- 캐시는 동일한 조건의 반복 조회가 많은 경우에 효과적이다.
- 닉네임이나 이메일이 변경되면 DB와 캐시의 데이터가 달라질 수 있다.
- 유저 정보 변경 및 삭제 시 `@CacheEvict` 또는 `@CachePut`을 이용해 캐시를 삭제하거나 갱신해야 한다.
- TTL이 너무 길면 오래된 데이터가 반환될 수 있고, 너무 짧으면 캐시 적중률이 낮아질 수 있다.
- Caffeine은 로컬 캐시이므로 서버가 재시작되면 데이터가 사라지며, 여러 서버 인스턴스가 캐시를 공유하지 못한다.
- 실제 운영 환경에서는 트래픽, 메모리 사용량, 데이터 변경 주기를 고려해 캐시 크기와 TTL을 조정해야 한다.

<br>

### 8. 개선 결과 비교

| 단계 | 적용 방법 | 평균 응답 시간 |
|---|---|---:|
| 최초 조회 | Spring Data JPA 엔티티 조회 | 2.516초 |
| 개선 1 | 닉네임 인덱스 | 54.8ms |
| 개선 2 | Spring Cache Hit | 16.6ms |

<br>

### 9. 최종 결론

| 단계 | 평균 응답 시간 | 주요 효과 |
|---|---:|---|
| 최초 조회 | 2,516ms | 인덱스가 없어 전체 테이블 스캔 발생 |
| 닉네임 인덱스 | 54.8ms | 예상 탐색 행이 987,879개에서 1개로 감소 |
| Spring Cache Hit | 16.6ms | 동일 닉네임 반복 조회 시 DB 접근 제거 |

- 닉네임 인덱스는 최초 조회를 포함한 DB 검색 자체의 성능을 크게 개선했다.
- Spring Cache는 동일한 닉네임이 반복 조회될 때 추가적인 성능 개선 효과를 보였다.
- 인덱스는 데이터 변경 여부와 관계없이 검색에 사용할 수 있지만, 저장 공간을 사용하고 데이터 쓰기 시 인덱스 갱신 비용이 발생한다.
- 캐시는 반복 조회에 효과적이지만, 데이터 변경 시 캐시 삭제 또는 갱신을 통해 정합성을 관리해야 한다.
- 따라서 기본적인 검색 성능은 인덱스로 확보하고, 반복 조회가 많은 데이터에 한해 캐시를 함께 적용하는 방식이 적절하다고 판단했다.
