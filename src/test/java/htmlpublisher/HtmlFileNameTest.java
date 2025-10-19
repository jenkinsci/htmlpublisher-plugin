package htmlpublisher;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.CreateFileBuilder;
import org.jvnet.hudson.test.JenkinsRule;

import org.htmlunit.html.HtmlInlineFrame;
import org.htmlunit.html.HtmlPage;

import hudson.model.FreeStyleProject;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;

import static org.junit.jupiter.api.Assertions.assertEquals;

@WithJenkins
class HtmlFileNameTest {

    private JenkinsRule j;

    @BeforeEach
    void setUp(JenkinsRule rule) {
        j = rule;
    }

    @Test
    void fileNameWithSpecialCharactersAndSingleSlash() throws Exception {
        final String content = "<html><head><title>test</title></head><body>Hello world!</body></html>";

        FreeStyleProject job = j.createFreeStyleProject();

        job.getBuildersList().add(new CreateFileBuilder("subdir/#$+,;= @.html", content));
        job.getPublishersList().add(new HtmlPublisher(List.of(
		        new HtmlPublisherTarget("report-name", "", "subdir/*.html", true, true, false))));
        job.save();

        j.buildAndAssertSuccess(job);

        JenkinsRule.WebClient client = j.createWebClient();
        assertEquals(content,
            client.getPage(job, "report-name/subdir/%23%24%2B%2C%3B%3D%20%40.html").getWebResponse().getContentAsString());

        // published html page(s)
        HtmlPage page = client.getPage(job, "report-name");
        HtmlInlineFrame iframe = (HtmlInlineFrame) page.getElementById("myframe");
        assertEquals("subdir/%23%24%2B%2C%3B%3D%20%40.html", iframe.getAttribute("src"));

        HtmlPage pageInIframe = (HtmlPage) iframe.getEnclosedPage();
        assertEquals("Hello world!", pageInIframe.getBody().asNormalizedText());
    }

    @Test
    void fileNameWithSpecialCharactersAndMultipleSlashes() throws Exception {
        final String content = "<html><head><title>test</title></head><body>Hello world!</body></html>";

        FreeStyleProject job = j.createFreeStyleProject();

        job.getBuildersList().add(new CreateFileBuilder("subdir/subdir2/#$+,;= @.html", content));
        job.getPublishersList().add(new HtmlPublisher(List.of(
		        new HtmlPublisherTarget("report-name", "", "subdir/subdir2/*.html", true, true, false))));
        job.save();

        j.buildAndAssertSuccess(job);

        JenkinsRule.WebClient client = j.createWebClient();
        assertEquals(content,
            client.getPage(job, "report-name/subdir/subdir2/%23%24%2B%2C%3B%3D%20%40.html").getWebResponse().getContentAsString());

        // published html page(s)
        HtmlPage page = client.getPage(job, "report-name");
        HtmlInlineFrame iframe = (HtmlInlineFrame) page.getElementById("myframe");
        assertEquals("subdir/subdir2/%23%24%2B%2C%3B%3D%20%40.html", iframe.getAttribute("src"));

        HtmlPage pageInIframe = (HtmlPage) iframe.getEnclosedPage();
        assertEquals("Hello world!", pageInIframe.getBody().asNormalizedText());
    }
}
