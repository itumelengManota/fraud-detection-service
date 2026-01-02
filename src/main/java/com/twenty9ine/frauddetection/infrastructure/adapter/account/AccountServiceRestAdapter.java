package com.twenty9ine.frauddetection.infrastructure.adapter.account;

import com.twenty9ine.frauddetection.application.port.out.AccountServicePort;
import com.twenty9ine.frauddetection.domain.exception.AccountNotFoundException;
import com.twenty9ine.frauddetection.domain.exception.AccountServiceException;
import com.twenty9ine.frauddetection.domain.valueobject.AccountProfile;
import com.twenty9ine.frauddetection.domain.valueobject.Location;
import com.twenty9ine.frauddetection.infrastructure.adapter.account.dto.AccountDto;
import com.twenty9ine.frauddetection.infrastructure.adapter.account.dto.LocationDto;
import io.github.resilience4j.bulkhead.annotation.Bulkhead;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import io.github.resilience4j.timelimiter.annotation.TimeLimiter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

@Service
@Slf4j
public class AccountServiceRestAdapter implements AccountServicePort {

    private final RestClient restClient;
    private final CacheManager cacheManager;  //TODO: Use RedisCacheManager for distributed caching

    public AccountServiceRestAdapter(RestClient.Builder restClientBuilder,
                                     @Value("${account-service.base-url}")
                                     String accountServiceUrl,
                                     CacheManager cacheManager) {
        this.restClient = restClientBuilder.baseUrl(accountServiceUrl).build();
        this.cacheManager = cacheManager;
    }

    @Override
    @Cacheable(value = "accountProfiles", key = "#accountId", unless = "#result == null")
    @CircuitBreaker(name = "accountService", fallbackMethod = "findAccountProfileFallback")
    @Retry(name = "accountService")
    @TimeLimiter(name = "accountService")
    @Bulkhead(name = "accountService")
    public AccountProfile findAccountProfile(String accountId) {
        log.debug("Fetching account profile from Account Service for: {}", accountId);

        return toDomain(findAccount(accountId));
    }

    private AccountProfile findAccountProfileFallback(String accountId, Exception ex) {
        log.warn("Account service fallback triggered for accountId: {} due to: {}", accountId, ex.getMessage());

        Cache cache = cacheManager.getCache("accountProfiles");

        if (cache != null) {
            AccountProfile cachedProfile = cache.get(accountId, AccountProfile.class);

            if (cachedProfile != null) {
                log.info("Retrieved cached account profile for accountId: {}", accountId);
                return cachedProfile;
            }
        }

        return null;
    }

    private AccountDto findAccount(String accountId) {
        return restClient.get()
                .uri("/accounts/{accountId}/profiles", accountId)
                .retrieve()
                .onStatus(status -> status.value() == 404, (_, _) -> {
                    log.warn("Account profile not found for accountId: {}", accountId);
                    throw new AccountNotFoundException("Account profile not found: " + accountId);
                })
                .onStatus(HttpStatusCode::is5xxServerError, (_, response) -> {
                    log.error("Account service error for accountId: {}, status: {}", accountId, response.getStatusCode());
                    throw new AccountServiceException("Account service unavailable", response.getStatusCode().value());
                })
                .body(AccountDto.class);
    }

    private AccountProfile toDomain(AccountDto accountDto) {
        return new AccountProfile(accountDto.accountId(), toDomain(accountDto.homeLocation()), accountDto.createdAt());
    }

    private static Location toDomain(LocationDto locationDto) {
        return Location.of(locationDto.latitude(), locationDto.longitude(),
                locationDto.country(), locationDto.city());
    }
}