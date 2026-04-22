package htmlpublisher;

import hudson.FilePath;
import hudson.Launcher;
import hudson.model.AbstractBuild;
import hudson.model.BuildListener;
import hudson.model.FreeStyleProject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.TestBuilder;
import org.jvnet.hudson.test.junit.jupiter.BuildWatcherExtension;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@WithJenkins
class Security3706Test {

    private JenkinsRule j;

    @SuppressWarnings("unused")
    @RegisterExtension
    private static final BuildWatcherExtension BUILD_WATCHER = new BuildWatcherExtension();

    @BeforeEach
    void setUp(JenkinsRule rule) {
        j = rule;
    }

    @Test
    void jobNameIsEscapedInWrapperHtml() throws Exception {
        String maliciousName = "xss' onfocus='alert(1)' autofocus tabindex='1";
        FreeStyleProject p = j.createFreeStyleProject(maliciousName);
        final String reportDir = "autogen";
        p.getBuildersList().add(new TestBuilder() {
            public boolean perform(AbstractBuild<?, ?> build, Launcher launcher, BuildListener listener)
                    throws InterruptedException, IOException {
                FilePath ws = build.getWorkspace().child(reportDir);
                ws.child("index.html").write("<html><body>test</body></html>", "UTF-8");
                return true;
            }
        });
        HtmlPublisherTarget target = new HtmlPublisherTarget("HTML Report", reportDir, "index.html", true, true, false);
        target.setUseWrapperFileDirectly(true);
        p.getPublishersList().add(new HtmlPublisher(Arrays.asList(target)));

        AbstractBuild build = j.buildAndAssertSuccess(p);

        File wrapperFile = new File(build.getRootDir(), "htmlreports/HTML_20Report/htmlpublisher-wrapper.html");
        assertTrue(wrapperFile.exists(), "Wrapper file should exist");

        String content = Files.readString(wrapperFile.toPath());
        // With proper escaping, the single quotes in the job name must not break out of the attribute value.
        assertFalse(content.contains("data-back-to-name='"), "Attributes should not use single quotes as delimiters");
        assertTrue(content.contains("data-back-to-name=\""), "Attributes should use double quotes as delimiters");
        // The malicious payload should be contained within the attribute value, not parsed as separate attributes
        assertFalse(content.matches("(?s).*\"\\s+onfocus=.*"), "onfocus should not appear as a separate HTML attribute");
    }
}
