package com.livestream.payment;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** 토스페이먼츠 테스트 키(개발자센터에서 무료 발급). 둘 다 있으면 토스, 없으면 내장 테스트 PG. 키는 .env로만 넣는다(커밋 금지). */
@ConfigurationProperties("app.payment")
public record PaymentProperties(String tossClientKey, String tossSecretKey, String tossApiUrl) {
    public boolean tossEnabled() {
        return tossClientKey != null && !tossClientKey.isBlank() && tossSecretKey != null && !tossSecretKey.isBlank();
    }
}
