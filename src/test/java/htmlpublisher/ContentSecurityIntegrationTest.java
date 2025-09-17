package htmlpublisher;

import java.util.List;

import htmlpublisher.ContentSecurity.Scripts;
import hudson.model.FreeStyleProject;
import net.sf.json.JSONObject;
import org.htmlunit.html.HtmlInlineFrame;
import org.htmlunit.html.HtmlPage;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.CreateFileBuilder;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;
import org.kohsuke.stapler.StaplerRequest2;

import static org.assertj.core.api.Assertions.assertThat;

@WithJenkins
class ContentSecurityIntegrationTest {

    @Test
    void shouldExecuteJavascriptInPermittedFiles(JenkinsRule jenkinsRule) throws Exception {
        JSONObject jsonObject = new JSONObject();
        jsonObject.put("allowContentSecurityOverride", true);
        jenkinsRule.jenkins.getDescriptorOrDie(HtmlPublisher.class).configure((StaplerRequest2) null, jsonObject);

        final String content = """
                    <html>
                        <head>
                            <title>test</title>
                        </head>
                        <body onload="document.getElementsByTagName('body')[0].innerHTML='Hello Jenkins!'">
                            Hello world!
                        </body>
                    </html>
                """;

        FreeStyleProject job = jenkinsRule.createFreeStyleProject();

        job.getBuildersList().add(new CreateFileBuilder("subdir/index.html", content));
        HtmlPublisherTarget target = new HtmlPublisherTarget("report-name", "", "subdir/*.html", true, true, false);
        target.setContentSecurity(new ContentSecurity(new Scripts(true, true), null, null, false));
        HtmlPublisher htmlPublisher = new HtmlPublisher(List.of(target));
        job.getPublishersList().add(htmlPublisher);
        job.save();

        jenkinsRule.buildAndAssertSuccess(job);

        JenkinsRule.WebClient client = jenkinsRule.createWebClient();
        assertThat(client.getPage(job, "report-name/subdir/index.html").getWebResponse().getContentAsString()).isEqualTo(content);
        assertThat(client.getPage(job, "report-name/subdir/index.html").getWebResponse().getResponseHeaderValue("Content-Security-Policy")).isEqualTo("sandbox allow-same-origin allow-scripts; script-src 'self' 'unsafe-inline' 'unsafe-eval';  style-src 'self';  img-src 'self';  default-src 'none';");

        HtmlPage page = client.getPage(job, "report-name");

        HtmlInlineFrame iframe = (HtmlInlineFrame) page.getElementById("myframe");
        assertThat(iframe.getAttribute("src")).isEqualTo("subdir/index.html");
        assertThat(iframe.getAttribute("sandbox")).isEqualTo("allow-scripts");

        HtmlPage pageInIframe = (HtmlPage) iframe.getEnclosedPage();
        assertThat(pageInIframe.getBody().asNormalizedText()).isEqualTo("Hello Jenkins!");
    }
}
