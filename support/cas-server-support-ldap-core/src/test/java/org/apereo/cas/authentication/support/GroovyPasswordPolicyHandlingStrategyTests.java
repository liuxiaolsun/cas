package org.apereo.cas.authentication.support;

import org.apereo.cas.authentication.support.password.GroovyPasswordPolicyHandlingStrategy;
import org.apereo.cas.authentication.support.password.PasswordPolicyConfiguration;
import org.apereo.cas.config.CasCoreUtilConfiguration;

import lombok.val;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.ldaptive.auth.AuthenticationResponse;
import org.ldaptive.auth.AuthenticationResultCode;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.autoconfigure.RefreshAutoConfiguration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.test.context.junit4.rules.SpringClassRule;
import org.springframework.test.context.junit4.rules.SpringMethodRule;

import javax.security.auth.login.AccountExpiredException;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

/**
 * This is {@link GroovyPasswordPolicyHandlingStrategyTests}.
 *
 * @author Misagh Moayyed
 * @since 5.2.0
 */
@SpringBootTest(classes = {
    RefreshAutoConfiguration.class,
    CasCoreUtilConfiguration.class
})
public class GroovyPasswordPolicyHandlingStrategyTests {
    @ClassRule
    public static final SpringClassRule SPRING_CLASS_RULE = new SpringClassRule();

    @Rule
    public final SpringMethodRule springMethodRule = new SpringMethodRule();

    @Rule
    public ExpectedException thrown = ExpectedException.none();

    @Test
    public void verifyStrategySupportsDefault() {
        val resource = new ClassPathResource("lppe-strategy.groovy");

        val s = new GroovyPasswordPolicyHandlingStrategy(resource);
        val res = mock(AuthenticationResponse.class);
        when(res.getAuthenticationResultCode()).thenReturn(AuthenticationResultCode.INVALID_CREDENTIAL);
        when(res.getResult()).thenReturn(false);

        val results = s.handle(res, mock(PasswordPolicyConfiguration.class));

        assertFalse(s.supports(null));
        assertTrue(s.supports(res));
        assertFalse(results.isEmpty());
    }

    @Test
    public void verifyStrategyHandlesErrors() {
        val resource = new ClassPathResource("lppe-strategy-throws-error.groovy");
        val s = new GroovyPasswordPolicyHandlingStrategy(resource);
        val res = mock(AuthenticationResponse.class);
        thrown.expect(AccountExpiredException.class);
        s.handle(res, mock(PasswordPolicyConfiguration.class));
    }
}
