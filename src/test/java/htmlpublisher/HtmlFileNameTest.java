package htmlpublisher;

import static org.junit.Assert.assertEquals;

import java.util.List;

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
    public void fileNameWithSpecialCharacters() throws Exception {
        final String content = "<html><head><title>test</title></head><body>Hello world!</body></html>";

        FreeStyleProject job = j.createFreeStyleProject();

        job.getBuildersList().add(new CreateFileBuilder("#$&+,:;= ?@.html", content));
        job.getPublishersList().add(new HtmlPublisher(List.of(
            new HtmlPublisherTarget("report-name", "", "*.html", true, true, false))));
        job.save();

        j.buildAndAssertSuccess(job);

        JenkinsRule.WebClient client = j.createWebClient();
        assertEquals(content,
            client.getPage(job, "report-name/%23%24%26%2B%2C%3A%3B%3D%20%3F%40.html").getWebResponse().getContentAsString());

        // published html page(s)
        HtmlPage page = client.getPage(job, "report-name");
        HtmlInlineFrame iframe = (HtmlInlineFrame) page.getElementById("myframe");
        assertEquals("%23%24%26%2B%2C%3A%3B%3D%20%3F%40.html", iframe.getAttribute("src"));

        HtmlPage pageInIframe = (HtmlPage) iframe.getEnclosedPage();
        assertEquals("Hello world!", pageInIframe.getBody().asText());
    }
}
