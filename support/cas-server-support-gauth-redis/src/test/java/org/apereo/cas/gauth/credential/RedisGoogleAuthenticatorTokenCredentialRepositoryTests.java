package org.apereo.cas.gauth.credential;

import org.apereo.cas.authentication.OneTimeTokenAccount;
import org.apereo.cas.config.GoogleAuthenticatorRedisConfiguration;
import org.apereo.cas.otp.repository.credentials.OneTimeTokenCredentialRepository;
import org.apereo.cas.util.CollectionUtils;
import org.apereo.cas.util.junit.EnabledIfListeningOnPort;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import org.apache.commons.lang3.time.StopWatch;
import org.jooq.lambda.Unchecked;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.EnableAspectJAutoProxy;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.stream.Stream;
import static org.junit.jupiter.api.Assertions.*;

/**
 * This is {@link RedisGoogleAuthenticatorTokenCredentialRepositoryTests}.
 *
 * @author Misagh Moayyed
 * @since 5.0.0
 */
@SpringBootTest(classes = {
    GoogleAuthenticatorRedisConfiguration.class,
    BaseOneTimeTokenCredentialRepositoryTests.SharedTestConfiguration.class
}, properties = {
    "cas.authn.mfa.gauth.redis.host=localhost",
    "cas.authn.mfa.gauth.redis.port=6379"
})
@EnableTransactionManagement(proxyTargetClass = false)
@EnableAspectJAutoProxy(proxyTargetClass = false)
@EnableScheduling
@Tag("Redis")
@Getter
@EnabledIfListeningOnPort(port = 6379)
@Slf4j
class RedisGoogleAuthenticatorTokenCredentialRepositoryTests extends BaseOneTimeTokenCredentialRepositoryTests {
    @Autowired
    @Qualifier("googleAuthenticatorAccountRegistry")
    private OneTimeTokenCredentialRepository registry;

    @BeforeEach
    public void cleanUp() {
        registry.deleteAll();
    }

    @Test
    void verifySave() throws Throwable {
        val username = UUID.randomUUID().toString();
        assertNull(registry.get(654321));
        assertNull(registry.get(username, 654321));

        var toSave = OneTimeTokenAccount.builder()
            .username(username)
            .secretKey("secret")
            .validationCode(143211)
            .scratchCodes(CollectionUtils.wrapList(1, 2, 3, 4, 5, 6))
            .name(UUID.randomUUID().toString())
            .build();
        registry.save(toSave);

        val account = registry.get(username).iterator().next();
        assertEquals("secret", account.getSecretKey());
        val accounts = registry.load();
        assertFalse(accounts.isEmpty());
    }

    @Test
    void verifyDelete() throws Throwable {
        val username = UUID.randomUUID().toString();
        val toSave = OneTimeTokenAccount.builder()
            .username(username)
            .secretKey("secret")
            .validationCode(143211)
            .scratchCodes(CollectionUtils.wrapList(1, 2, 3, 4, 5, 6))
            .name(UUID.randomUUID().toString())
            .build();
        registry.save(toSave);
        registry.delete(username);
        assertEquals(0, registry.count());
    }

    @Override
    @Test
    void verifySaveAndUpdate() throws Throwable {
        val username = UUID.randomUUID().toString();
        val toSave = OneTimeTokenAccount.builder()
            .username(username)
            .secretKey("secret")
            .validationCode(222222)
            .scratchCodes(CollectionUtils.wrapList(1, 2, 3, 4, 5, 6))
            .name(UUID.randomUUID().toString())
            .build();
        registry.save(toSave);
        val s = registry.get(username).iterator().next();
        assertNotNull(s.getRegistrationDate());
        assertEquals(222222, s.getValidationCode());
        s.setSecretKey("newSecret");
        s.setValidationCode(999666);
        registry.update(s);
        val s2 = registry.get(username).iterator().next();
        assertEquals(999666, s2.getValidationCode());
        assertEquals("newSecret", s2.getSecretKey());
    }

    @Test
    void verifyLargeDataset() throws Throwable {
        val allAccounts = Stream.generate(
                () -> {
                    val username = UUID.randomUUID().toString();
                    return OneTimeTokenAccount.builder()
                        .username(username)
                        .secretKey("secret")
                        .validationCode(222222)
                        .scratchCodes(CollectionUtils.wrapList(1, 2, 3, 4, 5, 6))
                        .name(UUID.randomUUID().toString())
                        .build();
                })
            .limit(1000);
        executedTimedOperation("Adding accounts", __ -> allAccounts.forEach(registry::save));
        executedTimedOperation("Getting accounts",
            Unchecked.consumer(__ -> {
                val accounts = registry.load();
                assertFalse(accounts.isEmpty());
            }));

        val accountsStream = executedTimedOperation("Getting accounts in bulk",
            Unchecked.supplier(() -> registry.load()));
        executedTimedOperation("Getting accounts individually",
            Unchecked.consumer(__ -> accountsStream.forEach(acct -> assertNotNull(registry.get(acct.getId())))));
        executedTimedOperation("Getting accounts individually for users",
            Unchecked.consumer(__ -> accountsStream.forEach(acct -> assertNotNull(registry.get(acct.getUsername())))));
    }

    private static <T> T executedTimedOperation(final String name, final Supplier<T> operation) {
        val stopwatch = new StopWatch();
        stopwatch.start();
        val result = operation.get();
        stopwatch.stop();
        val time = stopwatch.getTime(TimeUnit.MILLISECONDS);
        LOGGER.info("[{}]: [{}]ms", name, time);
        assertTrue(time <= 8000);
        return result;
    }

    private static void executedTimedOperation(final String name, final Consumer operation) {
        val stopwatch = new StopWatch();
        stopwatch.start();
        operation.accept(null);
        stopwatch.stop();
        val time = stopwatch.getTime(TimeUnit.MILLISECONDS);
        LOGGER.info("[{}]: [{}]ms", name, time);
        assertTrue(time <= 8000);
    }
}
