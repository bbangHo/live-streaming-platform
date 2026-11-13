package com.livestream.stream;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * MediaMTX 연동 설정.
 *
 * @param apiUrl       MediaMTX Control API (path 목록·송출 끊기). compose 내부망에서만 열린다
 * @param hookSecret   MediaMTX → 백엔드 인증 요청에 붙는 공유 비밀값. 비어 있으면 인증 요청을 모두 거부한다(fail closed)
 * @param devBypass    DB에 없는 스트림 키(예: dev-publish.html의 test)로도 송출 허용. 로컬 개발용 — 배포 시 false
 * @param grace        송출이 끊긴 뒤 이 시간 안에 다시 붙으면 방송 중을 유지, 넘으면 종료
 * @param syncEnabled  상태 동기화 스케줄러 사용 여부 (테스트에서 끈다)
 */
@ConfigurationProperties("app.media")
public record MediaProperties(String apiUrl, String hookSecret, boolean devBypass, Duration grace, boolean syncEnabled) {}
