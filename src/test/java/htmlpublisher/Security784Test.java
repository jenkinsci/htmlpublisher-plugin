package htmlpublisher;

import org.htmlunit.html.HtmlPage;
import hudson.model.FreeStyleProject;
import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.CreateFileBuilder;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.recipes.LocalData;

import java.io.File;
import java.util.Date;

public class Security784Test {
    @Rule
    public JenkinsRule j = new JenkinsRule();

    @LocalData
    @Test
    public void security784upgradeTest() throws Exception {

        FreeStyleProject job = j.jenkins.getItemByFullName("thejob", FreeStyleProject.class);

        Assert.assertTrue(new File(job.getRootDir(), "htmlreports/foo!!!!bar/index.html").exists());

        HtmlPublisherTarget.HTMLAction action = job.getAction(HtmlPublisherTarget.HTMLAction.class);
        Assert.assertNotNull(action);
        Assert.assertEquals("foo!!!!bar", action.getHTMLTarget().getReportName());
        Assert.assertEquals("foo!!!!bar", action.getUrlName()); // legacy

        JenkinsRule.WebClient client = j.createWebClient();
        HtmlPage page = client.getPage(job, "foo!!!!bar/index.html");
        String text = page.getWebResponse().getContentAsString();
        Assert.assertEquals("Sun Mar 25 15:42:10 CEST 2018", text.trim());

        job.getBuildersList().clear();
        String newDate = new Date().toString();
        job.getBuildersList().add(new CreateFileBuilder("index.html", newDate));

        job.save();

        j.buildAndAssertSuccess(job);

        Assert.assertTrue(new File(job.getRootDir(), "htmlreports/foo_21_21_21_21bar/index.html").exists());

        action = job.getAction(HtmlPublisherTarget.HTMLAction.class);
        Assert.assertNotNull(action);
        Assert.assertEquals("foo!!!!bar", action.getHTMLTarget().getReportName());
        Assert.assertEquals("foo_21_21_21_21bar", action.getUrlName()); // new

        text = client.goTo("job/thejob/foo_21_21_21_21bar/index.html").getWebResponse().getContentAsString();
        Assert.assertEquals(newDate, text.trim());

        // leftovers from legacy naming
        Assert.assertTrue(new File(job.getRootDir(), "htmlreports/foo!!!!bar/index.html").exists());
    }

    @Test
    public void testReportNameSanitization() throws Exception {
        // start with general 'sanity' requirements

        // failed in previous releases
        Assert.assertNotEquals(HtmlPublisherTarget.sanitizeReportName("foo bar", false), HtmlPublisherTarget.sanitizeReportName("foo_bar", false));

        // don't collapse repeated chars
        Assert.assertNotEquals(HtmlPublisherTarget.sanitizeReportName("foo!bar", false), HtmlPublisherTarget.sanitizeReportName("foo!!!!bar", false));

        // now be specific -- we escape non alphanumeric UTF-8 chars to their hex code with a '_' prefix
        Assert.assertEquals("foo_21bar", HtmlPublisherTarget.sanitizeReportName("foo!bar", false));
        Assert.assertEquals("foo_20bar", HtmlPublisherTarget.sanitizeReportName("foo bar", false));
        Assert.assertEquals("_20foo_20bar_20", HtmlPublisherTarget.sanitizeReportName(" foo bar ", false));
        Assert.assertEquals("_e5b79d_e58fa3", HtmlPublisherTarget.sanitizeReportName("川口", false)); // U+5DDD U+53E3
    }
}
