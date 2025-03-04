package htmlpublisher;

import hudson.model.FreeStyleProject;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.CreateFileBuilder;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;
import org.jvnet.hudson.test.recipes.LocalData;

import java.io.File;
import java.util.Date;

@WithJenkins
class Security784Test {

    private JenkinsRule j;

    @BeforeEach
    void setUp(JenkinsRule rule) {
        j = rule;
    }

    @LocalData
    @Test
    void security784upgradeTest() throws Exception {

        FreeStyleProject job = j.jenkins.getItemByFullName("thejob", FreeStyleProject.class);

        assertTrue(new File(job.getRootDir(), "htmlreports/foo!!!!bar/index.html").exists());

        JenkinsRule.WebClient client = j.createWebClient();

        job.getBuildersList().clear();
        String newDate = new Date().toString();
        job.getBuildersList().add(new CreateFileBuilder("index.html", newDate));

        job.save();

        j.buildAndAssertSuccess(job);

        assertTrue(new File(job.getRootDir(), "htmlreports/foo_21_21_21_21bar/index.html").exists());

        HtmlPublisherTarget.HTMLAction action = job.getAction(HtmlPublisherTarget.HTMLAction.class);
        assertNotNull(action);
        assertEquals("foo!!!!bar", action.getHTMLTarget().getReportName());
        assertEquals("foo_21_21_21_21bar", action.getUrlName()); // new

        String text = client.goTo("job/thejob/foo_21_21_21_21bar/index.html").getWebResponse().getContentAsString();
        assertEquals(newDate, text.trim());
    }

    @Test
    void testReportNameSanitization() {
        // start with general 'sanity' requirements

        // failed in previous releases
        assertNotEquals(HtmlPublisherTarget.sanitizeReportName("foo bar", false), HtmlPublisherTarget.sanitizeReportName("foo_bar", false));

        // don't collapse repeated chars
        assertNotEquals(HtmlPublisherTarget.sanitizeReportName("foo!bar", false), HtmlPublisherTarget.sanitizeReportName("foo!!!!bar", false));

        // now be specific -- we escape non alphanumeric UTF-8 chars to their hex code with a '_' prefix
        assertEquals("foo_21bar", HtmlPublisherTarget.sanitizeReportName("foo!bar", false));
        assertEquals("foo_20bar", HtmlPublisherTarget.sanitizeReportName("foo bar", false));
        assertEquals("_20foo_20bar_20", HtmlPublisherTarget.sanitizeReportName(" foo bar ", false));
        assertEquals("_e5b79d_e58fa3", HtmlPublisherTarget.sanitizeReportName("川口", false)); // U+5DDD U+53E3
    }
}
