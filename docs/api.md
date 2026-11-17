# API 명세

현재 컨트롤러와 DTO를 기준으로 작성한 계약이다. 예시의 사용자·방 ID와 시각은 설명용이다.

## 공통 규칙

- 기본 경로: `/api`, 운영 및 터널 접속은 HTTPS.
- JSON 요청은 `Content-Type: application/json`을 사용한다.
- 인증은 `JSESSIONID` 세션 쿠키다. 사용자 등록과 세션 확인 경로를 제외한 요청은 인증이 필요하다.
- 사용자 ID는 세션에서 읽는다. 요청 본문의 `userId`로 로그인 사용자를 바꾸지 않는다.
- 정상 REST 응답은 현재 구현에서 HTTP 200이다. 성공 본문은 API에 따라 JSON·문자열·빈 본문으로 다르다.
- 서버가 반환하는 시각은 Unix epoch **밀리초**다. `usageTime`은 **분**, 시간 연장 요청은 **초**다.
- `roomId`는 문자열, 사용자 ID는 정수다. 세션 값은 클라이언트 로그나 문서에 기록하지 않는다.

## 엔드포인트 목록

| 메서드 | 경로 | 입력 | 정상 응답 |
|---|---|---|---|
| POST | `/api/users` | User | `{sessionId, userId}` |
| GET | `/api/users/session` | 없음 | 로그인 상태 문자열; 미인증 시 401 |
| GET | `/api/surveys` | 없음 | `[{id, content}]` |
| POST | `/api/surveys/results` | SurveyResult | 빈 본문 |
| GET | `/api/test-questions/service-type/{serviceTypeId}` | 서비스 유형 ID | Question 배열 |
| POST | `/api/test-results` | TypeTestResult | 유형 설명·제안 |
| POST | `/api/rooms` | CreateRoom | 방 ID·방장 정보 |
| GET | `/api/rooms/{roomId}` | 방 ID | WaitingRoom |
| POST | `/api/rooms/{roomId}/players` | JoinRoom | WaitingRoom |
| DELETE | `/api/rooms/{roomId}/players` | 없음 | 빈 본문 |
| POST | `/api/rooms/{roomId}/ping` | 없음 | `pong` |
| GET | `/api/sse/rooms/connect/{roomId}` | 방 ID | SSE 스트림 |
| DELETE | `/api/sse/{roomId}/players` | 없음 | 연결 해제 문자열 |
| GET | `/api/games/{roomId}/state` | 방 ID | GameState |
| GET | `/api/games/{roomId}/time-sync` | 방 ID | `{serverTime}` |
| POST | `/api/games/{roomId}/round/1/state` | 없음 | 시작 완료 문자열 |
| POST | `/api/games/{roomId}/round/1/usage-time` | query `usageTime` | 입력 완료 문자열 |
| POST | `/api/games/{roomId}/round/2/state` | 없음 | 2라운드 시작 문자열 |
| POST | `/api/games/{roomId}/card/{cardType}` | query `maxId` | 카드 부여 문자열 |
| POST | `/api/games/{roomId}/timer/extensions` | query `additionalTime=120` | 시간 연장 문자열 |
| POST | `/api/games/{roomId}/speaking/end` | 없음 | 발언 종료 문자열 |
| POST | `/api/games/{roomId}/votes` | Vote | 투표 완료 문자열 |
| POST | `/api/games/{roomId}/retry` | 없음 | 다시하기 완료 문자열 |

`SchoolController`는 주석 처리돼 있어 학교 검색 API는 제공하지 않는다. 1인 게임은 앱에 포함된 데이터와 로직을 사용하며 전용 서버 API가 없다.

## 사용자와 세션

`POST /api/users`

```json
{"name":"참가자","gender":0,"schoolType":2}
```

`schoolType`은 `0`(초등학교 1~3학년), `1`(초등학교 4~6학년), `2`(중학생)이며 enum 이름도 서버 역직렬화에 사용할 수 있다. `gender`는 `0`(남자), `1`(여자)다.

```json
{"userId":7,"sessionId":"<session-value>"}
```

앱은 응답의 사용자 ID를 보관하고 세션 쿠키를 API 호스트에 설정한다. `GET /api/users/session`으로 유효성을 확인한다. 비밀번호·JWT 기반 로그인이 아니며, 별도의 로그아웃·사용자 목록 API는 없다.

## 이용습관 설문

`GET /api/surveys`는 문항 ID와 내용을 반환한다. `POST /api/surveys/results` 요청 예:

```json
{
  "selectedOptions":[{"surveyId":1,"score":2},{"surveyId":2,"score":1}],
  "totalScore":3
}
```

결과 ID는 서버가 생성한다. DTO의 `surveyResultId`는 저장 시 사용하지 않으므로 클라이언트가 지정할 필요가 없다. 문항 ID는 DB에 존재해야 한다.

## 사용 유형 검사

`GET /api/test-questions/service-type/{serviceTypeId}` 응답 예:

```json
[{"id":1,"content":"문항 내용","testOptions":[{"id":11,"content":"선택지","score":1}]}]
```

`POST /api/test-results` 요청과 응답 예:

```json
{"serviceTypeId":1,"totalScore":3,"selectedOptionIds":[11,21,31]}
```

```json
{"suhatTypeId":1,"name":"유형 이름","description":"유형 설명","suggestion":"사용 습관 제안"}
```

서비스 유형·문항·선택지·결과 유형은 DB 기준 데이터가 필요하다. 현재 구현은 클라이언트의 `totalScore`로 결과 유형을 조회하며 서버에서 선택지 점수를 재합산하지 않는다. 서비스 유형 ID와 점수 범위를 임의로 만들어 호출하지 않는다.

## 게임방

### 생성과 참가

`POST /api/rooms`

```json
{
  "roomName":"우리반",
  "totalPlayers":3,
  "hostInfo":{"name":"참가자","gender":0,"schoolType":2},
  "surveyScore":3,
  "avatarMaxId":4
}
```

앱은 3인·4인 게임을 제공한다. `avatarMaxId`는 앱에 포함된 아바타 개수다. 응답은 `{roomId, roomName, totalPlayers, host}`이며 방장은 생성과 동시에 참가자로 등록된다.

`POST /api/rooms/{roomId}/players`

```json
{"userInfo":{"name":"참가자2","gender":1,"schoolType":2},"surveyScore":4}
```

조회·참가 응답인 WaitingRoom은 `roomId`, `roomName`, `currentPlayers`, `totalPlayers`, `players`, `hostId`, `roomStatus`를 포함한다. 참가 후 SSE에 연결하면 준비 상태가 설정된다.

### 연결 유지와 퇴장

- SSE 연결 후 앱은 30초마다 `POST /api/rooms/{roomId}/ping`을 보낸다. 서버는 REST 응답과 함께 SSE `pong`도 전송한다.
- `DELETE /api/rooms/{roomId}/players`는 **명시적인 퇴장**이다. 게임 중 퇴장은 게임을 종료시킨다.
- `DELETE /api/sse/{roomId}/players`는 연결만 해제한다. 방 퇴장과 다르다.
- 앱 백그라운드 전환은 로컬 연결을 닫는다. 명시적 퇴장 API를 호출하지 않는다.

## 게임 명령

| 동작 | 조건과 처리 |
|---|---|
| 1라운드 시작 | 방장만 요청. 대기 상태에서 정원 충족 및 전원 준비 필요 |
| 사용 시간 입력 | 1라운드에서 제출. 0분도 제출된 값으로 처리 |
| 순서 결정 | 사용 시간 오름차순, 설문 점수 내림차순, 동률은 무작위 |
| 2라운드 시작 | 방장만 요청. 전원 순서가 정해진 뒤 진행 |
| 카드 부여 | 현재 발언자만 요청. `cardType`은 `picture` 또는 `text`, `maxId`는 해당 카드 수 |
| 타이머 시작 | 두 카드가 모두 정해지면 서버 마감 시각 생성 |
| 시간 연장 | 현재 발언자, 120초, 한 번만 가능 |
| 발언 종료 | 현재 발언자만 요청. 또는 서버 마감 시 자동 종료 |
| 투표 | 발언자를 제외한 참가자, 현재 투표 차수에서 제출 |
| 다시하기 | 방 참가자가 요청. 게임 진행 중에는 거절, 대기 상태로 전환 |

투표 요청:

```json
{"vote":"agree","votingRound":2}
```

`vote`는 `agree` 또는 `disagree`다. `votingRound`는 가장 최근 GameState에서 가져온다. 이전 차수의 지연 요청은 거절하고, 같은 참가자의 중복 투표는 기존 표를 덮어쓰지 않는다. 마감까지 미제출한 표는 서버가 `abstain`으로 채운다. 클라이언트가 `abstain`을 제출하는 API는 아니다.

3인 게임은 동의 1표, 4인 게임은 동의 2표가 필요하다. 최초 실패 시 한 번 재투표하며, 재투표 후에도 실패하면 다음 발언자로 넘어간다.

## SSE와 GameState

요청:

```http
GET /api/sse/rooms/connect/{roomId}
Accept: text/event-stream
Cookie: JSESSIONID=<session-value>
```

스트림 예시 — 실제 `data`에는 전체 GameState가 들어간다.

```text
id:16
event:game-state
data:{"revision":16,"roomId":"example","roomStatus":"IN_GAME",...}

```

| 필드 | 의미 |
|---|---|
| `revision` | 방 단위 증가 버전. SSE `id`와 일치 |
| `serverTime` | snapshot 생성 시 서버 시각 |
| `roomId`, `roomName`, `hostId` | 방 식별·표시·방장 |
| `roomStatus` | `WAITING`, `IN_GAME`, `ENDED` |
| `currentRound`, `currentTurn` | 현재 라운드와 발언 순번 |
| `currentPhase` | `NONE`, `DISCUSSION`, `VOTING` |
| `players` | 참가자 상태 배열 |
| `totalPlayers` | 정원 |
| `currentPuzzlePieces`, `totalPuzzlePieces` | 퍼즐 진행량·목표 |
| `assignedPictureCardId`, `assignedTextCardId` | 현재 카드 ID, 미선정 시 0 |
| `speakingEndsAt`, `votingEndsAt` | 서버 절대 마감 시각, 비활성 시 0 |
| `timeExtended` | 현재 발언 시간 연장 여부 |
| `votingRound`, `reVoting` | 투표 요청 버전과 재투표 여부 |
| `votedPlayerIds` | 제출한 참가자 ID. 개별 투표 내용은 제외 |
| `lastVoteResult` | 최근 집계 결과·동의·비동의·기권 수 |
| `ranking` | 게임 종료 후 순위 |

Player JSON은 `playerId`, `avatarId`, `playerInfo`, `sequenceNumber`, `collectedPuzzlePieces`, `speaking`, `usageTime`, `usageTimeSubmitted`, `surveyScore`, `ready`, `seatIndex`, `lastPingTime`, `disconnectedAt`을 포함한다. **발언자 필드는 `speaking`이며 `isSpeaking`이 아니다.** `playerInfo.schoolType`의 JSON 출력은 enum 이름이다.

재접속하면 새 전체 상태를 받는다. 클라이언트는 현재 연결의 이벤트만 처리하고 마지막 적용 버전보다 큰 snapshot만 반영한다. 과거 애니메이션을 재생하지 않으며 `Last-Event-ID` 기반 이벤트 이력 재전송은 제공하지 않는다. 대기방 호환용 기존 이벤트는 함께 유지한다.

## 오류 응답

| 상태 | 대표 상황 |
|---|---|
| 400 | 지원하지 않는 투표 값 등 잘못된 입력 |
| 401 | 세션 없음·만료 |
| 403 | 방장·발언자 권한 부족, 방 참가 권한 없음 |
| 404 | 방 또는 참가자를 찾을 수 없음 |
| 409 | 현재 단계·순번과 맞지 않는 명령 |
| 410 | 재접속 유예 만료를 연결 시점에 확인 |
| 500 | 처리되지 않은 내부 오류 |

게임 도메인 오류는 문자열 본문, 일반 예외는 `ProblemDetail` 형태를 사용한다. SSE의 일반 예외 처리에는 204 응답 경로도 있어 모든 오류가 동일한 JSON 형식이라고 가정하지 않는다. 세션·참가 권한 오류를 받은 앱은 무한 재연결을 중단한다.
