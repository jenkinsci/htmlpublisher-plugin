package htmlpublisher;

import hudson.model.FreeStyleProject;
import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.recipes.LocalData;

import java.io.File;

import static hudson.Functions.isWindows;
import static org.junit.Assume.assumeFalse;

public class Security3301Test {

    @Rule
    public JenkinsRule j = new JenkinsRule();

    @Test
    @LocalData
    public void security3301sanitizeTest() throws Exception {


        // Skip on windows
        assumeFalse(isWindows());

        FreeStyleProject job = j.jenkins.getItemByFullName("testJob", FreeStyleProject.class);

        Assert.assertTrue(new File(job.getRootDir(), "htmlreports/HTML_20Report").exists());

        j.buildAndAssertSuccess(job);

        changeJobReportName(job,"HTML_20Report/javascript:alert(1)");

        job.save();

        j.buildAndAssertSuccess(job);

        HtmlPublisherTarget.HTMLAction action = job.getAction(HtmlPublisherTarget.HTMLAction.class);
        Assert.assertNotNull(action);

        //Check that the report name is escaped for the Url
        Assert.assertEquals("HTML_20Report/javascript:alert(1)", action.getHTMLTarget().getReportName());
        Assert.assertEquals("HTML_5f20Report_2fjavascript_3aalert_281_29", action.getUrlName());

        Assert.assertTrue(new File(job.getRootDir(), "htmlreports/HTML_5f20Report_2fjavascript_3aalert_281_29").exists());

        FreeStyleProject anotherJob = j.jenkins.getItemByFullName("anotherJob", FreeStyleProject.class);

        Assert.assertTrue(new File(anotherJob.getRootDir(), "htmlreports/HTML_20Report").exists());

        j.buildAndAssertSuccess(anotherJob);

        changeJobReportName(job,"../../anotherJob/htmlreports/HTML_20Report");

        job.save();

        //Check that the build reports is not from the new job (anotherJob)
        Assert.assertEquals("../../anotherJob/htmlreports/HTML_20Report", action.getHTMLTarget().getReportName());
        Assert.assertEquals("_2e_2e_2f_2e_2e_2fanotherJob_2fhtmlreports_2fHTML_5f20Report", action.getUrlName());
        Assert.assertFalse(new File(job.getRootDir(), "htmlreports/_2e_2e_2f_2e_2e_2fanotherJob_2fhtmlreports_2fHTML_5f20Report/test.txt").exists());

    }

    public void changeJobReportName(FreeStyleProject job, String newName) {
        for (Object publisher : job.getPublishersList()) {
            if (publisher instanceof HtmlPublisher) {
                HtmlPublisher existingPublishHTML = (HtmlPublisher) publisher;
                existingPublishHTML.getReportTargets().get(0).setReportName(newName);
            }
        }
    }
}
