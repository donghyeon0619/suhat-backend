# Android 연결 검증 서버

`mobileSmokeServer`는 실제 애플리케이션을 테스트용 H2 메모리 DB로 실행한다. 운영 `application.yml`을 읽지 않고 `src/test/resources/mobile-smoke.properties`만 사용한다. 테스트 설정과 H2 의존성은 운영 JAR에 포함하지 않는다.

```powershell
$env:SMOKE_ORIGIN = 'https://테스트-호스트'
./gradlew.bat mobileSmokeServer --no-daemon
```

서버는 `127.0.0.1:18080`에만 바인딩한다. HTTPS 임시 터널을 이 포트에 연결하며, 터널은 원래 요청의 HTTPS 스킴을 전달해야 한다. 테스트 계정과 게임방만 생성하고 종료 시 메모리 DB를 폐기한다. H2 콘솔은 제공하지 않는다.

게임 상태와 세션은 센터 운영 환경과 같은 단일 프로세스에 존재한다. 발언 180초, 재접속 유예 180초, 투표 30초의 기본값을 그대로 사용한다.

이 구성으로 네이티브 HTTP, 세션 쿠키, SSE, 게임 상태 전환을 확인할 수 있다. MySQL 고유 동작이나 운영 설문 데이터는 검증하지 않는다. 전체 설문·유형검사 검증에는 별도 테스트 MySQL과 기준 데이터가 필요하다.

검증이 끝나면 터널과 서버를 종료한다. 임시 URL은 앱 배포 주소로 사용하지 않는다.
