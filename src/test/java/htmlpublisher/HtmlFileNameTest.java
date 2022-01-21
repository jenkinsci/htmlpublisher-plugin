package htmlpublisher;

import static org.junit.Assert.assertEquals;

import java.util.Arrays;

import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.CreateFileBuilder;
import org.jvnet.hudson.test.JenkinsRule;

import com.gargoylesoftware.htmlunit.html.HtmlInlineFrame;
import com.gargoylesoftware.htmlunit.html.HtmlPage;

import hudson.model.FreeStyleProject;

public class HtmlFileNameTest {
    @Rule
    public JenkinsRule j = new JenkinsRule();

    @Test
    public void fileNameWithSpecialCharactersAndSingleSlash() throws Exception {
        final String content = "<html><head><title>test</title></head><body>Hello world!</body></html>";

        FreeStyleProject job = j.createFreeStyleProject();

        job.getBuildersList().add(new CreateFileBuilder("subdir/#$&+,;= @.html", content));
        job.getPublishersList().add(new HtmlPublisher(Arrays.asList(
            new HtmlPublisherTarget("report-name", "", "subdir/*.html", true, true, false))));
        job.save();

        j.buildAndAssertSuccess(job);

        JenkinsRule.WebClient client = j.createWebClient();
        assertEquals(content,
            client.getPage(job, "report-name/subdir/%23%24%26%2B%2C%3B%3D%20%40.html").getWebResponse().getContentAsString());

        // published html page(s)
        HtmlPage page = client.getPage(job, "report-name");
        HtmlInlineFrame iframe = (HtmlInlineFrame) page.getElementById("myframe");
        assertEquals("subdir/%23%24%26%2B%2C%3B%3D%20%40.html", iframe.getAttribute("src"));

        HtmlPage pageInIframe = (HtmlPage) iframe.getEnclosedPage();
        assertEquals("Hello world!", pageInIframe.getBody().asNormalizedText());
    }
    
    @Test
    public void fileNameWithSpecialCharactersAndMultipleSlashes() throws Exception {
        final String content = "<html><head><title>test</title></head><body>Hello world!</body></html>";

        FreeStyleProject job = j.createFreeStyleProject();

        job.getBuildersList().add(new CreateFileBuilder("subdir/subdir2/#$&+,;= @.html", content));
        job.getPublishersList().add(new HtmlPublisher(Arrays.asList(
            new HtmlPublisherTarget("report-name", "", "subdir/subdir2/*.html", true, true, false))));
        job.save();

        j.buildAndAssertSuccess(job);

        JenkinsRule.WebClient client = j.createWebClient();
        assertEquals(content,
            client.getPage(job, "report-name/subdir/subdir2/%23%24%26%2B%2C%3B%3D%20%40.html").getWebResponse().getContentAsString());

        // published html page(s)
        HtmlPage page = client.getPage(job, "report-name");
        HtmlInlineFrame iframe = (HtmlInlineFrame) page.getElementById("myframe");
        assertEquals("subdir/subdir2/%23%24%26%2B%2C%3B%3D%20%40.html", iframe.getAttribute("src"));

        HtmlPage pageInIframe = (HtmlPage) iframe.getEnclosedPage();
        assertEquals("Hello world!", pageInIframe.getBody().asNormalizedText());
    }
}
