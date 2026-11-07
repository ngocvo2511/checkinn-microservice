package com.example.userservice.service;

import com.example.userservice.security.TokenRevocationService;
import com.example.userservice.security.SessionStoreUnavailableException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Propagation;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

/** Explicit opt-in: real test-clone PostgreSQL and a Redis ACL denying writes. No scheduler/RPC server. */
@EnabledIfEnvironmentVariable(named="CHECKINN_SESSION_TEST",matches="true")
@DataJpaTest(properties={"spring.jpa.hibernate.ddl-auto=validate","spring.jpa.show-sql=false","spring.flyway.enabled=false"})
@AutoConfigureTestDatabase(replace=AutoConfigureTestDatabase.Replace.NONE)
@Import({UserService.class,OtpService.class,TokenRevocationService.class})
@ImportAutoConfiguration(RedisAutoConfiguration.class)
@Transactional(propagation=Propagation.NOT_SUPPORTED)
class PasswordResetSessionStoreIntegrationTest {
    @MockBean org.springframework.amqp.rabbit.core.RabbitTemplate rabbit;
    @Autowired UserService users;
    @Autowired JdbcTemplate jdbc;
    @DynamicPropertySource static void properties(DynamicPropertyRegistry registry) {
        String url=System.getenv("CHECKINN_SESSION_DB_URL");
        if(url==null||!url.matches("jdbc:postgresql://127\\.0\\.0\\.1:[0-9]+/checkinn_user"))throw new IllegalStateException("Verified clone transport required");
        registry.add("spring.datasource.url",()->url);registry.add("spring.datasource.username",()->"checkinn_test");
        registry.add("spring.datasource.password",()->System.getenv("CHECKINN_SESSION_DB_PASSWORD"));
        registry.add("spring.data.redis.host",()->"127.0.0.1");registry.add("spring.data.redis.port",()->System.getenv("CHECKINN_SESSION_REDIS_PORT"));
        registry.add("spring.data.redis.username",()->System.getenv("CHECKINN_SESSION_REDIS_USER"));registry.add("spring.data.redis.password",()->System.getenv("CHECKINN_SESSION_REDIS_PASSWORD"));
    }
    @Test void deniedSessionWriteRollsBackConsumedProofAndLeavesPasswordUnchanged() {
        UUID id=UUID.fromString(System.getenv("CHECKINN_SESSION_RUN_UUID"));
        String username="sessionit_"+id.toString().replace("-","");String email=username+"@example.invalid";
        String original=new org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder().encode("synthetic-old-password");
        assertEquals(0,jdbc.queryForObject("select count(*) from users where id=?",Integer.class,id));
        try {
            jdbc.update("insert into users(id,username,email,password,role,is_email_verified,is_active,created_at) values (?,?,?,?,'CUSTOMER',true,true,now())",id,username,email,original);
            jdbc.update("insert into otps(id,email,otp_code,created_at,expiry_time,is_verified,attempt_count) values (?,?,'123456',now(),now()+interval '10 minutes',true,0)",UUID.randomUUID(),email);
            assertThrows(SessionStoreUnavailableException.class,()->users.resetPassword(email,"synthetic-new-password"));
            assertTrue(original.equals(jdbc.queryForObject("select password from users where id=?",String.class,id)),"Password must survive a failed revocation-store write");
            assertEquals(1,jdbc.queryForObject("select count(*) from otps where email=? and is_verified",Integer.class,email));
        } finally {jdbc.update("delete from otps where email=?",email);jdbc.update("delete from users where id=? and username=?",id,username);}
    }
}
