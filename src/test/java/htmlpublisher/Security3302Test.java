package htmlpublisher;

import hudson.model.FreeStyleProject;
import hudson.tasks.Shell;
import org.htmlunit.AlertHandler;
import org.htmlunit.FailingHttpStatusCodeException;
import org.htmlunit.Page;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;
import org.jvnet.hudson.test.recipes.LocalData;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static hudson.Functions.isWindows;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.collection.IsEmptyCollection.empty;
import static org.hamcrest.core.IsNot.not;
import static org.junit.jupiter.api.Assumptions.assumeFalse;
import static org.junit.jupiter.api.Assertions.*;

@WithJenkins
class Security3302Test {

    private JenkinsRule j;

    @BeforeEach
    void setUp(JenkinsRule rule) {
        j = rule;
    }

    @Test
    void security3302sanitizeJobNameTest() throws Exception {

        // Skip on windows
        assumeFalse(isWindows());

        FreeStyleProject job = j.jenkins.createProject(FreeStyleProject.class, "\"+alert(1)+\"");
        job.getBuildersList().add(new Shell("date > index.html"));

        HtmlPublisherTarget target = new HtmlPublisherTarget(
                "HTML Report",
                "",
                "index.html",
                true,
                false,
                false
        );

        target.setUseWrapperFileDirectly(true);
        target.setEscapeUnderscores(true);
        target.setReportTitles("");
        target.setIncludes("**/*");

        List<HtmlPublisherTarget> reportTargets = new ArrayList<>();
        reportTargets.add(target);

        job.getPublishersList().add(new HtmlPublisher(reportTargets));

        j.buildAndAssertSuccess(job);

        HtmlPublisherTarget.HTMLAction action = job.getAction(HtmlPublisherTarget.HTMLAction.class);
        assertNotNull(action);

        assertEquals("HTML Report", action.getHTMLTarget().getReportName());
        assertEquals("HTML_20Report", action.getUrlName());

        JenkinsRule.WebClient client = j.createWebClient();

        // Create an alert handler to check for any alerts
        Alerter alerter = new Alerter();
        client.setAlertHandler(alerter);
        client.goTo("job/\"+alert(1)+\"/HTML_20Report/");

        // Check that the alerter has not been triggered
        client.waitForBackgroundJavaScript(2000);
        assertTrue(alerter.messages.isEmpty());

    }

    @Test
    @LocalData
    @Issue("security-3302")
    void oldReportJobNameTest() throws Exception {
        // Skip on windows
        assumeFalse(isWindows());
        List<FreeStyleProject> items = j.jenkins.getItems(FreeStyleProject.class);
        assertThat(items, not(empty()));
        FreeStyleProject job = items.get(0);
        assertNotNull(job);
        HtmlPublisherTarget.HTMLAction action = job.getAction(HtmlPublisherTarget.HTMLAction.class);
        assertNotNull(action);

        assertEquals("HTML Report", action.getHTMLTarget().getReportName());
        assertEquals("HTML_20Report", action.getUrlName());

        JenkinsRule.WebClient client = j.createWebClient();

        // Create an alert handler to check for any alerts
        Alerter alerter = new Alerter();
        client.setAlertHandler(alerter);

        try {
            client.goTo("job/testJob/1/HTML_20Report/");

        } catch (FailingHttpStatusCodeException e) {
            // Ignore the exception as needed
        } finally {

            client.waitForBackgroundJavaScript(2000);
            assertTrue(alerter.messages.isEmpty());
        }
    }

    @Test
    void security3302sanitizeOptionalNameTest() throws Exception {

        // Skip on windows
        assumeFalse(isWindows());

        FreeStyleProject job = j.jenkins.createProject(FreeStyleProject.class, "testJob");
        job.getBuildersList().add(new Shell("echo \"Test\" > test.txt"));

        HtmlPublisherTarget target = new HtmlPublisherTarget(
                "HTML Report",
                "",
                "test.txt",
                true,
                false,
                false
        );

        target.setUseWrapperFileDirectly(true);
        target.setEscapeUnderscores(true);
        target.setReportTitles("<img src onerror=alert(1)>");
        target.setIncludes("**/*");

        List<HtmlPublisherTarget> reportTargets = new ArrayList<>();
        reportTargets.add(target);

        job.getPublishersList().add(new HtmlPublisher(reportTargets));

        j.buildAndAssertSuccess(job);

        HtmlPublisherTarget.HTMLAction action = job.getAction(HtmlPublisherTarget.HTMLAction.class);
        assertNotNull(action);

        assertEquals("HTML Report", action.getHTMLTarget().getReportName());
        assertEquals("HTML_20Report", action.getUrlName());

        JenkinsRule.WebClient client = j.createWebClient();

        // Create an alert handler to check for any alerts
        Alerter alerter = new Alerter();
        client.setAlertHandler(alerter);
        client.goTo("job/testJob/HTML_20Report/");

        // Check that the alerter has not been triggered
        client.waitForBackgroundJavaScript(2000);
        assertTrue(alerter.messages.isEmpty());

    }

    @Test
    void security3302sanitizeExistingReportTitleTest() throws Exception {

        // Skip on windows
        assumeFalse(isWindows());

        FreeStyleProject job = j.jenkins.createProject(FreeStyleProject.class, "testJob");
        job.getBuildersList().add(new Shell("echo \"Test\" > '\"><img src onerror=alert(1)>'"));

        HtmlPublisherTarget target = new HtmlPublisherTarget(
                "HTML Report",
                "",
                "",
                true,
                false,
                false
        );

        target.setUseWrapperFileDirectly(true);
        target.setEscapeUnderscores(true);
        target.setReportTitles("\"><img src onerror=alert(1)>");
        target.setIncludes("**/*");

        List<HtmlPublisherTarget> reportTargets = new ArrayList<>();
        reportTargets.add(target);

        job.getPublishersList().add(new HtmlPublisher(reportTargets));

        j.buildAndAssertSuccess(job);

        HtmlPublisherTarget.HTMLAction action = job.getAction(HtmlPublisherTarget.HTMLAction.class);
        assertNotNull(action);

        assertEquals("HTML Report", action.getHTMLTarget().getReportName());
        assertEquals("HTML_20Report", action.getUrlName());

        JenkinsRule.WebClient client = j.createWebClient();

        Alerter alerter = new Alerter();
        client.setAlertHandler(alerter);
        client.goTo("job/testJob/HTML_20Report/");

        // Check that the alerter has not been triggered
        client.waitForBackgroundJavaScript(2000);
        assertTrue(alerter.messages.isEmpty());

    }

    // This class is used to check for any alerts that are triggered on a page
    static class Alerter implements AlertHandler {
        final List<String> messages = Collections.synchronizedList(new ArrayList<>());
        @Override
        public void handleAlert(final Page page, final String message) {
            messages.add(message);
        }
    }
}