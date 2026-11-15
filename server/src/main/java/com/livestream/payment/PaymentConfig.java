package com.livestream.payment;

import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

@Configuration
public class PaymentConfig {
    private static final Logger log = LoggerFactory.getLogger(PaymentConfig.class);

    @Bean
    PaymentGateway paymentGateway(PaymentProperties props) {
        if (props.tossEnabled()) {
            log.info("결제: 토스페이먼츠 ({})", props.tossApiUrl());
            SimpleClientHttpRequestFactory timeouts = new SimpleClientHttpRequestFactory();
            timeouts.setConnectTimeout(Duration.ofSeconds(5));
            timeouts.setReadTimeout(Duration.ofSeconds(30)); // 토스 권장: 승인 응답은 수십 초 걸릴 수 있음
            return new TossPaymentGateway(props, RestClient.builder().requestFactory(timeouts));
        }
        log.info("결제: 내장 테스트 PG (TOSS_CLIENT_KEY/TOSS_SECRET_KEY를 넣으면 토스페이먼츠 테스트 결제)");
        return new TestPaymentGateway();
    }
}
