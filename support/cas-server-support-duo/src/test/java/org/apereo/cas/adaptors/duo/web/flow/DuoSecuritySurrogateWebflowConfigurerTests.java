package org.apereo.cas.adaptors.duo.web.flow;

import org.apereo.cas.config.CasCoreMultifactorAuthenticationConfiguration;
import org.apereo.cas.config.CasMultifactorAuthenticationWebflowConfiguration;
import org.apereo.cas.config.DuoSecurityAuthenticationEventExecutionPlanConfiguration;
import org.apereo.cas.config.DuoSecurityConfiguration;
import org.apereo.cas.config.DuoSecurityMultifactorProviderBypassConfiguration;
import org.apereo.cas.config.SurrogateAuthenticationAuditConfiguration;
import org.apereo.cas.config.SurrogateAuthenticationConfiguration;
import org.apereo.cas.config.SurrogateAuthenticationWebflowConfiguration;
import org.apereo.cas.web.flow.BaseWebflowConfigurerTests;
import org.apereo.cas.web.flow.CasWebflowConfigurer;
import org.apereo.cas.web.flow.CasWebflowConstants;
import org.apereo.cas.web.flow.configurer.CasMultifactorWebflowCustomizer;
import org.apereo.cas.web.support.WebUtils;

import lombok.val;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.webflow.engine.Flow;
import org.springframework.webflow.engine.TransitionableState;

import static org.apereo.cas.web.flow.CasWebflowConstants.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * This is {@link DuoSecuritySurrogateWebflowConfigurerTests}.
 *
 * @author Misagh Moayyed
 * @since 6.2.0
 */
@Tag("DuoSecurity")
class DuoSecuritySurrogateWebflowConfigurerTests {

    @Import({
        CasCoreMultifactorAuthenticationConfiguration.class,
        CasMultifactorAuthenticationWebflowConfiguration.class,
        SurrogateAuthenticationConfiguration.class,
        SurrogateAuthenticationAuditConfiguration.class,
        SurrogateAuthenticationWebflowConfiguration.class
    })
    static class SharedTestConfiguration {
    }

    @Nested
    @SuppressWarnings("ClassCanBeStatic")
    @Import(DuoSecuritySurrogateWebflowConfigurerTests.SharedTestConfiguration.class)
    class DefaultTests extends BaseWebflowConfigurerTests {

        @Test
        void verifyOperation() throws Throwable {
            assertFalse(casWebflowExecutionPlan.getWebflowConfigurers().isEmpty());
            val flow = (Flow) this.loginFlowDefinitionRegistry.getFlowDefinition(CasWebflowConfigurer.FLOW_ID_LOGIN);
            assertNotNull(flow);
            var state = (TransitionableState) flow.getState(STATE_ID_LOAD_SURROGATES_ACTION);
            assertNotNull(state);
            state = (TransitionableState) flow.getState(CasWebflowConstants.STATE_ID_SELECT_SURROGATE);
            assertNotNull(state);
            state = (TransitionableState) flow.getState(CasWebflowConstants.STATE_ID_SURROGATE_VIEW);
            assertNotNull(state);
        }
    }

    @Nested
    @SuppressWarnings("ClassCanBeStatic")
    @Import({
        DuoSecurityConfiguration.class,
        DuoSecurityAuthenticationEventExecutionPlanConfiguration.class,
        DuoSecurityMultifactorProviderBypassConfiguration.class,
        DuoSecuritySurrogateWebflowConfigurerTests.SharedTestConfiguration.class
    })
    @TestPropertySource(properties = {
        "cas.authn.mfa.duo[0].duo-secret-key=aGKL0OndjtknbnVOWaFKosbbinNFEKXHxgXCJEBz",
        "cas.authn.mfa.duo[0].duo-integration-key=DIOXVRQD3UMZ8XXMNFQ8",
        "cas.authn.mfa.duo[0].duo-api-host=theapi.duosecurity.com"
    })
    class DuoSecurityUniversalPromptTests extends BaseWebflowConfigurerTests {
        @Autowired
        @Qualifier("surrogateDuoSecurityMultifactorAuthenticationWebflowConfigurer")
        private CasWebflowConfigurer surrogateDuoSecurityMultifactorAuthenticationWebflowConfigurer;

        @Autowired
        @Qualifier("surrogateDuoSecurityMultifactorWebflowCustomizer")
        private CasMultifactorWebflowCustomizer surrogateDuoSecurityMultifactorWebflowCustomizer;

        @Test
        void verifyOperation() throws Throwable {
            assertNotNull(surrogateDuoSecurityMultifactorAuthenticationWebflowConfigurer);
            assertNotNull(surrogateDuoSecurityMultifactorWebflowCustomizer);
            val flow = (Flow) this.loginFlowDefinitionRegistry.getFlowDefinition(CasWebflowConfigurer.FLOW_ID_LOGIN);
            assertNotNull(flow);
            var state = (TransitionableState) flow.getState(CasWebflowConstants.STATE_ID_DUO_UNIVERSAL_PROMPT_VALIDATE_LOGIN);
            assertEquals(STATE_ID_LOAD_SURROGATES_ACTION, state.getTransition(TRANSITION_ID_SUCCESS).getTargetStateId());

            val mappings = surrogateDuoSecurityMultifactorWebflowCustomizer.getWebflowAttributeMappings();
            assertTrue(mappings.contains(WebUtils.REQUEST_SURROGATE_ACCOUNT_ATTRIBUTE));
        }
    }

    /**
     * @deprecated Since 6.6.
     */
    @Nested
    @SuppressWarnings("ClassCanBeStatic")
    @Import({
        DuoSecurityConfiguration.class,
        DuoSecurityAuthenticationEventExecutionPlanConfiguration.class,
        DuoSecurityMultifactorProviderBypassConfiguration.class,
        DuoSecuritySurrogateWebflowConfigurerTests.SharedTestConfiguration.class
    })
    @TestPropertySource(properties = {
        "cas.authn.mfa.duo[0].duo-secret-key=aGKL0OndjtknbnVOWaFKosiqinNFEKXHxgXCJEBr",
        "cas.authn.mfa.duo[0].application-key=my134nfd46m89",
        "cas.authn.mfa.duo[0].duo-integration-key=SIOXVQQD3UMZ8XXMNZQ8",
        "cas.authn.mfa.duo[0].duo-api-host=theapi.duosecurity.com"
    })
    @Deprecated(since = "6.5.0", forRemoval = true)
    class DuoSecurityWebSdkTests extends BaseWebflowConfigurerTests {
        @Test
        void verifyOperation() throws Throwable {
            val flow = (Flow) this.loginFlowDefinitionRegistry.getFlowDefinition(CasWebflowConfigurer.FLOW_ID_LOGIN);
            var state = (TransitionableState) flow.getState("mfa-duo");
            assertEquals(STATE_ID_LOAD_SURROGATES_ACTION, state.getTransition(TRANSITION_ID_SUCCESS).getTargetStateId());
        }
    }
}
