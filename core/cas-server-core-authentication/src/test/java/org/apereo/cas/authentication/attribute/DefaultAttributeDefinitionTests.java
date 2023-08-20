package org.apereo.cas.authentication.attribute;

import org.apereo.cas.authentication.CoreAuthenticationTestUtils;
import org.apereo.cas.util.scripting.GroovyScriptResourceCacheManager;
import org.apereo.cas.util.scripting.ScriptResourceCacheManager;
import org.apereo.cas.util.spring.ApplicationContextProvider;

import lombok.val;
import org.apereo.services.persondir.util.CaseCanonicalizationMode;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.context.support.StaticApplicationContext;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * This is {@link DefaultAttributeDefinitionTests}.
 *
 * @author Misagh Moayyed
 * @since 6.3.0
 */
@Tag("Authentication")
class DefaultAttributeDefinitionTests {

    private static AttributeDefinitionResolutionContext getAttributeDefinitionResolutionContext() throws Throwable {
        return AttributeDefinitionResolutionContext.builder()
            .attributeValues(List.of("v1", "v2"))
            .scope("example.org")
            .principal(CoreAuthenticationTestUtils.getPrincipal())
            .registeredService(CoreAuthenticationTestUtils.getRegisteredService())
            .service(CoreAuthenticationTestUtils.getService())
            .build();
    }

    @Test
    void verifyCaseCanonicalizationMode() throws Throwable {
        val applicationContext = new StaticApplicationContext();
        applicationContext.registerSingleton(ScriptResourceCacheManager.BEAN_NAME, GroovyScriptResourceCacheManager.class);
        applicationContext.refresh();
        ApplicationContextProvider.holdApplicationContext(applicationContext);

        val defn = DefaultAttributeDefinition.builder()
            .key("computedAttribute")
            .canonicalizationMode(CaseCanonicalizationMode.UPPER.name())
            .script("groovy { return ['value1', 'value2'] }")
            .build();

        val context = getAttributeDefinitionResolutionContext();

        val values = defn.resolveAttributeValues(context);
        assertTrue(values.contains("VALUE1"));
        assertTrue(values.contains("VALUE2"));
    }

    @Test
    void verifyNoCacheEmbeddedScriptOperation() throws Throwable {
        val applicationContext = new StaticApplicationContext();
        applicationContext.refresh();
        ApplicationContextProvider.holdApplicationContext(applicationContext);

        val defn = DefaultAttributeDefinition.builder()
            .key("computedAttribute")
            .script("groovy { return ['hello world'] }")
            .build();
        val context = getAttributeDefinitionResolutionContext();
        val values = defn.resolveAttributeValues(context);
        assertTrue(values.isEmpty());
    }

    @Test
    void verifyBadScript() throws Throwable {
        val applicationContext = new StaticApplicationContext();
        applicationContext.refresh();
        ApplicationContextProvider.holdApplicationContext(applicationContext);

        val defn = DefaultAttributeDefinition.builder()
            .key("computedAttribute")
            .script("badformat ()")
            .build();
        val context = getAttributeDefinitionResolutionContext();
        val values = defn.resolveAttributeValues(context);
        assertTrue(values.isEmpty());
    }

    @Test
    void verifyCachedEmbeddedScriptOperation() throws Throwable {
        val applicationContext = new StaticApplicationContext();
        applicationContext.registerSingleton(ScriptResourceCacheManager.BEAN_NAME, GroovyScriptResourceCacheManager.class);
        applicationContext.refresh();
        ApplicationContextProvider.holdApplicationContext(applicationContext);

        val defn = DefaultAttributeDefinition.builder()
            .key("computedAttribute")
            .script("groovy { return ['hello world'] }")
            .build();
        val context = getAttributeDefinitionResolutionContext();
        var values = defn.resolveAttributeValues(context);
        assertFalse(values.isEmpty());
        values = defn.resolveAttributeValues(context);
        assertFalse(values.isEmpty());
    }

    @Test
    void verifyNoCachedExternalScriptOperation() throws Throwable {
        val applicationContext = new StaticApplicationContext();
        applicationContext.refresh();
        ApplicationContextProvider.holdApplicationContext(applicationContext);

        val defn = DefaultAttributeDefinition.builder()
            .key("computedAttribute")
            .script("classpath:ComputedAttributeDefinition.groovy")
            .build();
        val context = getAttributeDefinitionResolutionContext();
        val values = defn.resolveAttributeValues(context);
        assertTrue(values.isEmpty());
    }

    @Test
    void verifyCachedExternalScriptOperation() throws Throwable {
        val applicationContext = new StaticApplicationContext();
        applicationContext.registerSingleton(ScriptResourceCacheManager.BEAN_NAME, GroovyScriptResourceCacheManager.class);
        applicationContext.refresh();
        ApplicationContextProvider.holdApplicationContext(applicationContext);

        val defn = DefaultAttributeDefinition.builder()
            .key("computedAttribute")
            .script("classpath:ComputedAttributeDefinition.groovy")
            .build();
        val context = getAttributeDefinitionResolutionContext();
        var values = defn.resolveAttributeValues(context);
        assertFalse(values.isEmpty());
        values = defn.resolveAttributeValues(context);
        assertFalse(values.isEmpty());
    }

    @Test
    void verifyBadExternalScriptOperation() throws Throwable {
        val applicationContext = new StaticApplicationContext();
        applicationContext.registerSingleton(ScriptResourceCacheManager.BEAN_NAME, GroovyScriptResourceCacheManager.class);
        applicationContext.refresh();
        ApplicationContextProvider.holdApplicationContext(applicationContext);

        val defn = DefaultAttributeDefinition.builder()
            .key("computedAttribute")
            .script("classpath:BadScript.groovy")
            .build();
        val context = getAttributeDefinitionResolutionContext();
        val values = defn.resolveAttributeValues(context);
        assertTrue(values.isEmpty());
    }

    @Test
    void verifyBadEmbeddedScriptOperation() throws Throwable {
        val applicationContext = new StaticApplicationContext();
        applicationContext.registerSingleton(ScriptResourceCacheManager.BEAN_NAME, GroovyScriptResourceCacheManager.class);
        applicationContext.refresh();
        ApplicationContextProvider.holdApplicationContext(applicationContext);

        val defn = DefaultAttributeDefinition.builder()
            .key("computedAttribute")
            .script("groovy {xyz}")
            .build();
        val context = getAttributeDefinitionResolutionContext();
        val values = defn.resolveAttributeValues(context);
        assertTrue(values.isEmpty());
    }
}
