package htmlpublisher;

import htmlpublisher.ContentSecurity.Images;
import htmlpublisher.ContentSecurity.Scripts;
import htmlpublisher.ContentSecurity.Styles;
import hudson.model.Descriptor.FormException;
import net.sf.json.JSONObject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;
import org.kohsuke.stapler.StaplerRequest2;

import static org.assertj.core.api.Assertions.assertThat;

@WithJenkins
class ContentSecurityTest {

    private JenkinsRule jenkinsRule;

    @BeforeEach
    void setUp(JenkinsRule jenkinsRule) throws FormException {
        JSONObject jsonObject = new JSONObject();
        jsonObject.put("allowContentSecurityOverride", true);
        jenkinsRule.jenkins.getDescriptorOrDie(HtmlPublisher.class).configure((StaplerRequest2) null, jsonObject);
        this.jenkinsRule = jenkinsRule;
    }

    @Nested
    class IframeSandbox {
        @ParameterizedTest
        @CsvSource({
                "true, true, allow-scripts allow-same-origin",
                "true, false, allow-scripts",
                "false, true, allow-same-origin",
                "false, false, ''"
        })
        void shouldCreateIframeSandboxDirective(boolean allowScripts, boolean allowSameOrigin, String expected) {
            ContentSecurity underTest = new ContentSecurity(allowScripts ? new Scripts(false, false) : null, null, null, allowSameOrigin);
            String result = underTest.createIframeSandboxAttributes();
            assertThat(result).isEqualTo(expected);
        }

        @Test
        void shouldCreateEmptySandboxDirectiveWhenGlobalOverrideIsDisabled() throws FormException {
            JSONObject jsonObject = new JSONObject();
            jsonObject.put("allowContentSecurityOverride", false);
            jenkinsRule.jenkins.getDescriptorOrDie(HtmlPublisher.class).configure((StaplerRequest2) null, jsonObject);

            ContentSecurity underTest = new ContentSecurity(new Scripts(false, false), null, null, true);
            String result = underTest.createIframeSandboxAttributes();
            assertThat(result).isEmpty();
        }
    }

    @Nested
    class NoExistingPolicy {

        @Test
        void shouldCreateDefaultPolicyWhenEmptyPolicyIsSet() {
            ContentSecurity underTest = new ContentSecurity(null, null, null, false);
            String result = underTest.createAlteredContentSecurityPolicy("");
            assertThat(result).isNull();
        }
    }

    @Nested
    class SandboxOnlyPolicy {

        @Test
        void shouldPreserveSandboxWhenOnlySandboxIsSet() {
            ContentSecurity underTest = new ContentSecurity(new Scripts(false, false), new Styles(false), null, false);
            String policy = "sandbox allow-scripts";
            String result = underTest.createAlteredContentSecurityPolicy(policy);
            assertThat(result).isEqualTo("sandbox allow-scripts; script-src 'self'; style-src 'self';");
        }

        @Test
        void shouldPreserveSandboxWhenGlobalOverrideIsDisabled() throws FormException {
            JSONObject jsonObject = new JSONObject();
            jsonObject.put("allowContentSecurityOverride", false);
            jenkinsRule.jenkins.getDescriptorOrDie(HtmlPublisher.class).configure((StaplerRequest2) null, jsonObject);

            ContentSecurity underTest = new ContentSecurity(new Scripts(false, false), new Styles(false), null, true);
            String policy = "sandbox allow-scripts";
            String result = underTest.createAlteredContentSecurityPolicy(policy);
            assertThat(result).isEqualTo("sandbox allow-scripts");
        }
    }

    @Nested
    class InvalidPolicy {

        @Test
        void shouldReturnDefaultPolicyWhenPolicyIsInvalid() {
            ContentSecurity underTest = new ContentSecurity(new Scripts(false, false), new Styles(false), null, false);
            String policy = "invalid-policy";
            String result = underTest.createAlteredContentSecurityPolicy(policy);
            assertThat(result).isEqualTo("sandbox allow-scripts; script-src 'self'; style-src 'self'; invalid-policy;");
        }
    }

    @Nested
    class ScriptsPresent {

        @Test
        void shouldCreatePolicyThatAllowsInlineScriptWhenScriptsAreEnabledWithUnsafeInline() {
            ContentSecurity underTest = new ContentSecurity(new Scripts(true, false), new Styles(false), null, true);
            String policy = "sandbox test-policy";
            String result = underTest.createAlteredContentSecurityPolicy(policy);
            assertThat(result).isEqualTo("sandbox test-policy allow-scripts allow-same-origin; script-src 'self' 'unsafe-inline'; style-src 'self';");
        }

        @Test
        void shouldCreatePolicyThatAllowsInlineScriptWhenScriptsAreEnabledWithUnsafeEval() {
            ContentSecurity underTest = new ContentSecurity(new Scripts(false, true), null, null, false);
            String policy = "sandbox;";
            String result = underTest.createAlteredContentSecurityPolicy(policy);
            assertThat(result).isEqualTo("sandbox allow-scripts; script-src 'self' 'unsafe-eval';");
        }

        @Test
        void shouldNotAllowInlineScriptWhenScriptsAreEnabledWithoutUnsafeInlineBeingSet() {
            ContentSecurity underTest = new ContentSecurity(new Scripts(false, false), new Styles(false), null, false);
            String policy = "sandbox unknown-directive; script-src 'self'";
            String result = underTest.createAlteredContentSecurityPolicy(policy);
            assertThat(result).isEqualTo("sandbox unknown-directive allow-scripts; script-src 'self'; style-src 'self';");
        }

        @Test
        void shouldNotAlterPolicyIfScriptsAreAllowedAndSandboxAlreadyAllowsScripts() {
            ContentSecurity underTest = new ContentSecurity(new Scripts(false, false), null, null, false);
            String policy = "sandbox allow-scripts;";
            String result = underTest.createAlteredContentSecurityPolicy(policy);
            assertThat(result).isEqualTo("sandbox allow-scripts; script-src 'self';");
        }
    }

    @Nested
    class StylesPresent {

        @ParameterizedTest
        @CsvSource({
                "true, true, sandbox test-policy, sandbox test-policy; style-src 'self' 'unsafe-inline';",
                "false, false, sandbox test-policy, sandbox test-policy;",
                "true, false, sandbox test-policy, sandbox test-policy; style-src 'self';",
                "true, false, sandbox allow-same-origin; style-src 'self', sandbox allow-same-origin; style-src 'self';"
        })
        void shouldCreatePolicyThatMatchesStyleConfiguration(boolean allowStyles, boolean allowUnsafeInline,String contentSecurityPolicy, String expected) {
            ContentSecurity underTest = new ContentSecurity(null, allowStyles ? new Styles(allowUnsafeInline) : null, null, false);
            String result = underTest.createAlteredContentSecurityPolicy(contentSecurityPolicy);
            assertThat(result).isEqualTo(expected);
        }

    }


    @Nested
    class ImagesPresent {

        @ParameterizedTest
        @CsvSource({
                "true, true, sandbox test-policy, sandbox test-policy; img-src 'self' data:;",
                "false, false, sandbox test-policy, sandbox test-policy;",
                "true, false, sandbox test-policy, sandbox test-policy; img-src 'self';",
                "true, false, sandbox allow-same-origin; img-src 'self', sandbox allow-same-origin; img-src 'self';"
        })
        void shouldCreatePolicyThatMatchesStyleConfiguration(boolean allowImages, boolean allowData, String contentSecurityPolicy, String expected) {
            ContentSecurity underTest = new ContentSecurity(null, null, allowImages ? new Images(allowData) : null, false);
            String result = underTest.createAlteredContentSecurityPolicy(contentSecurityPolicy);
            assertThat(result).isEqualTo(expected);
        }

    }

}