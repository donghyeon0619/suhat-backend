# 수햇 백엔드

스마트폰 중독 예방 보드게임 서비스의 Spring Boot 서버다. 게임방, 발언 순서, 카드, 투표와 SSE 상태 전달을 관리한다.

- Java 17, Spring Boot 3.3, MySQL
- [재접속과 서버 타이머](docs/game-reconnection.md)
- [개발 규칙](CONTRIBUTING.md)

## 게임 로직 테스트

```sh
./gradlew test --tests '*GameRecoveryTest' --tests '*GameConnectionTest'
```

이 테스트는 DB나 운영 서버에 연결하지 않는다. 애플리케이션을 실행하려면 별도의 DB·HTTPS·프론트 주소 설정이 필요하다. 저장소의 기존 운영 설정을 그대로 사용해 실행하지 말고 개발 환경 설정을 준비한다.

센터의 단일 서버에서 Spring Boot 프로세스 하나를 운영한다. 게임 상태와 연결은 프로세스 메모리에 보관하며, 재접속과 서버 타이머는 이 구조를 기준으로 동작한다. 서버 재시작은 진행 중인 게임이 끝난 뒤 수행한다.
