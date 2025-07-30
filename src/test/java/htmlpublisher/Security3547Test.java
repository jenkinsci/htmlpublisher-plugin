package htmlpublisher;

import hudson.FilePath;
import hudson.Launcher;
import hudson.model.AbstractBuild;
import hudson.model.BuildListener;
import hudson.model.FreeStyleProject;
import hudson.model.Result;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.BuildWatcher;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.TemporaryDirectoryAllocator;
import org.jvnet.hudson.test.TestBuilder;
import java.io.IOException;
import java.util.Arrays;

/**
 * @author Vishal Wagh
 */
public class Security3547Test {
    @Rule
    public JenkinsRule j = new JenkinsRule();

    @Rule
    public BuildWatcher buildWatcher = new BuildWatcher();

    public TemporaryDirectoryAllocator tmp = new TemporaryDirectoryAllocator();

    /**
     * Makes sure that the configuration survives the round trip.
     */
    @Test
    public void buildSuccessfulScenario() throws Exception {
        FreeStyleProject p = j.createFreeStyleProject("include_job");
        final String reportDir = "autogen";
        p.getBuildersList().add(new TestBuilder() {
            public boolean perform(AbstractBuild<?, ?> build, Launcher launcher, BuildListener listener)
                    throws InterruptedException, IOException {
                FilePath ws = build.getWorkspace().child(reportDir);
                ws.child("tab1.html").write("hello", "UTF-8");
                ws.child("dummy.html").write("hello", "UTF-8");
                return true;
            }
        });
        HtmlPublisherTarget target1 = new HtmlPublisherTarget("tab1", reportDir, "tab1.html", true, true, false);
        // default behavior is include all
        target1.setIncludes(HtmlPublisherTarget.INCLUDE_ALL_PATTERN);
        HtmlPublisherTarget[] l = {target1};
        p.getPublishersList().add(new HtmlPublisher(Arrays.asList(l)));
        AbstractBuild build = j.buildAndAssertSuccess(p);
        j.assertLogNotContains(build.getRootDir().getAbsolutePath(), build);
    }

    @Test
    public void zeroCopyFailureScenario() throws Exception {
        FreeStyleProject p = j.createFreeStyleProject("include_job");
        p.getBuildersList().add(new TestBuilder() {
            public boolean perform(AbstractBuild<?, ?> build, Launcher launcher, BuildListener listener) {
                return false;
            }
        });
        HtmlPublisherTarget target1 = new HtmlPublisherTarget("tab1", "", "tab1.html", true, true, false);
        HtmlPublisherTarget[] l = {target1};
        p.getPublishersList().add(new HtmlPublisher(Arrays.asList(l)));
        AbstractBuild build = j.assertBuildStatus(Result.FAILURE, p.scheduleBuild2(0));
        j.assertLogContains("exists but failed copying to '" + target1.getReportName() + "'", build);
        j.assertLogNotContains(build.getRootDir().getAbsolutePath(), build);
    }
}
