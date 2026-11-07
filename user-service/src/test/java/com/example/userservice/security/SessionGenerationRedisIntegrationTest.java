package com.example.userservice.security;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

/** Opt-in checkinn-test transport only. Normal host unit runs must not connect to a real Redis. */
@EnabledIfEnvironmentVariable(named="CHECKINN_SESSION_TEST",matches="true")
class SessionGenerationRedisIntegrationTest {
    @Test void acknowledgedGenerationIsPersistentAndReadOnlyRedisCannotRotateIt() {
        var rootConfig=new RedisStandaloneConfiguration("127.0.0.1",Integer.parseInt(System.getenv("CHECKINN_SESSION_REDIS_PORT")));
        var rootFactory=new LettuceConnectionFactory(rootConfig);rootFactory.afterPropertiesSet();
        var redis=new StringRedisTemplate(rootFactory);redis.afterPropertiesSet();
        UUID id=UUID.fromString(System.getenv("CHECKINN_SESSION_RUN_UUID"));String key="auth:session-generation:"+id;
        var limitedConfig=new RedisStandaloneConfiguration("127.0.0.1",rootConfig.getPort());
        limitedConfig.setUsername(System.getenv("CHECKINN_SESSION_REDIS_USER"));limitedConfig.setPassword(System.getenv("CHECKINN_SESSION_REDIS_PASSWORD"));
        var limitedFactory=new LettuceConnectionFactory(limitedConfig);limitedFactory.afterPropertiesSet();
        try {
            var service=new TokenRevocationService(redis);String generation=service.rotateUserSessions(id);
            assertTrue(generation.equals(redis.opsForValue().get(key)));assertEquals(-1L,redis.getExpire(key));
            var readOnly=new StringRedisTemplate(limitedFactory);readOnly.afterPropertiesSet();
            assertTrue(generation.equals(new TokenRevocationService(readOnly).currentUserGeneration(id)));
            assertThrows(SessionStoreUnavailableException.class,()->new TokenRevocationService(readOnly).rotateUserSessions(id));
            assertTrue(generation.equals(redis.opsForValue().get(key)),"Denied rotation must not replace acknowledged family");
        } finally {redis.delete(key);limitedFactory.destroy();rootFactory.destroy();}
    }
}
