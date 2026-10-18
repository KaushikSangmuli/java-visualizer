package com.example.demo.service;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RequestAdmissionServiceTest {
    @Test
    void enforcesRateAndConcurrencyLimits() {
        var service = new RequestAdmissionService(1, 10, 60_000, 10);
        var first = service.acquire("client");
        assertThat(first.allowed()).isTrue();
        assertThat(service.acquire("client").allowed()).isFalse();
        service.release();
        assertThat(service.acquire("client").allowed()).isTrue();
        service.release();

        var rateLimited = new RequestAdmissionService(2, 1, 60_000, 10);
        assertThat(rateLimited.acquire("other-client").allowed()).isTrue();
        rateLimited.release();
        assertThat(rateLimited.acquire("other-client").allowed()).isFalse();
    }
}
