package htmlpublisher;

import hudson.model.FreeStyleProject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;
import org.jvnet.hudson.test.recipes.LocalData;

import java.io.File;

import static hudson.Functions.isWindows;
import static org.junit.jupiter.api.Assumptions.assumeFalse;
import static org.junit.jupiter.api.Assertions.*;

@WithJenkins
class Security3301Test {

    private JenkinsRule j;

    @BeforeEach
    void setUp(JenkinsRule rule) {
        j = rule;
    }

    @Test
    @LocalData
    void security3301sanitizeTest() throws Exception {


        // Skip on windows
        assumeFalse(isWindows());

        FreeStyleProject job = j.jenkins.getItemByFullName("testJob", FreeStyleProject.class);

        assertTrue(new File(job.getRootDir(), "htmlreports/HTML_20Report").exists());

        j.buildAndAssertSuccess(job);

        changeJobReportName(job,"HTML_20Report/javascript:alert(1)");

        job.save();

        j.buildAndAssertSuccess(job);

        HtmlPublisherTarget.HTMLAction action = job.getAction(HtmlPublisherTarget.HTMLAction.class);
        assertNotNull(action);

        //Check that the report name is escaped for the Url
        assertEquals("HTML_20Report/javascript:alert(1)", action.getHTMLTarget().getReportName());
        assertEquals("HTML_5f20Report_2fjavascript_3aalert_281_29", action.getUrlName());

        assertTrue(new File(job.getRootDir(), "htmlreports/HTML_5f20Report_2fjavascript_3aalert_281_29").exists());

        FreeStyleProject anotherJob = j.jenkins.getItemByFullName("anotherJob", FreeStyleProject.class);

        assertTrue(new File(anotherJob.getRootDir(), "htmlreports/HTML_20Report").exists());

        j.buildAndAssertSuccess(anotherJob);

        changeJobReportName(job,"../../anotherJob/htmlreports/HTML_20Report");

        job.save();

        //Check that the build reports is not from the new job (anotherJob)
        assertEquals("../../anotherJob/htmlreports/HTML_20Report", action.getHTMLTarget().getReportName());
        assertEquals("_2e_2e_2f_2e_2e_2fanotherJob_2fhtmlreports_2fHTML_5f20Report", action.getUrlName());
        assertFalse(new File(job.getRootDir(), "htmlreports/_2e_2e_2f_2e_2e_2fanotherJob_2fhtmlreports_2fHTML_5f20Report/test.txt").exists());

    }

    public void changeJobReportName(FreeStyleProject job, String newName) {
        for (Object publisher : job.getPublishersList()) {
            if (publisher instanceof HtmlPublisher existingPublishHTML) {
	            existingPublishHTML.getReportTargets().get(0).setReportName(newName);
            }
        }
    }
}
