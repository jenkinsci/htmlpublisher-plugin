/*
 * The MIT License
 *
 * Copyright 2015 Jesse Glick.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */

package htmlpublisher.workflow;

import edu.umd.cs.findbugs.annotations.NonNull;
import htmlpublisher.HtmlPublisherTarget;
import hudson.model.FreeStyleProject;
import hudson.model.Node;
import hudson.model.Result;
import hudson.model.queue.QueueTaskFuture;
import hudson.slaves.DumbSlave;
import hudson.slaves.NodeProperty;
import hudson.slaves.RetentionStrategy;
import org.hamcrest.Matchers;
import org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;
import org.jenkinsci.plugins.workflow.steps.Step;
import org.jenkinsci.plugins.workflow.steps.StepConfigTester.StepBuilder;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.jvnet.hudson.test.JenkinsRule;

import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.Collections;

import static org.junit.Assert.*;

public class PublishHTMLStepTest {

    @Rule
    public JenkinsRule r = new JenkinsRule();

    @Rule
    public TemporaryFolder tmp = new TemporaryFolder();

    private WorkflowJob job;
    private WorkflowRun run;
    private File testWorkspace;
    private File testReportDir;

    private static final String TEST_REPORT_DIR = "testReportDir";
    private static final String TEST_PROJECT_NAME = "p";

    @Before
    public void setupReportFolder() throws IOException {
        testWorkspace = tmp.newFolder("workflowWS");
        testReportDir = new File(testWorkspace, "workspace/" + TEST_PROJECT_NAME + "/" + TEST_REPORT_DIR);
    }

    @Test
    public void configRoundTrip() throws Exception {
        configRoundTrip("Foo", "archive", "index.html");
        configRoundTrip("Foo2", "archive/foo/bar", "index2.html");
        configRoundTrip("Foo", "archive", "**");
        configRoundTrip("Report", "archive", "index.html", false, false, false);
        configRoundTrip("Report", "archive", "index.html", true, true, true);
        configRoundTrip("Report", "archive", "index.html", true, false, false);
        configRoundTrip("Report", "archive", "index.html", false, true, false);
        configRoundTrip("Report", "archive", "index.html", false, false, true);
    }

    @Test
    public void testDeprecationWarningDisplayedWhenTryingToResolveParameters() throws Exception {
        writeTestHTML("index.html");
        final HtmlPublisherTarget target = new HtmlPublisherTarget
                ("testReport", TEST_REPORT_DIR, "${TEST}", false, false, false);
        setupAndRunProject(target);

        // Ensure that the report has been attached properly
        r.assertBuildStatus(Result.SUCCESS, run);
        r.assertLogContains("*** WARNING ***", run);
    }

    @Test
    public void testDeprecationWarningNotDisplayedWhenNotTryingToResolveParameters() throws Exception {
        writeTestHTML("index.html");
        final HtmlPublisherTarget target = new HtmlPublisherTarget
                ("testReport", TEST_REPORT_DIR, "test", false, false, false);
        setupAndRunProject(target);

        // Ensure that the report has been attached properly
        r.assertBuildStatus(Result.SUCCESS, run);
        r.assertLogNotContains("*** WARNING ***", run);
    }

    @Test
    public void publishReportOnProjectLevel() throws Exception {

        // Prepare the environment
        writeTestHTML("index.html");

        // Run the project
        final HtmlPublisherTarget target = new HtmlPublisherTarget
            ("testReport", TEST_REPORT_DIR, "index.html",  false, false, false);
        setupAndRunProject(target);

        // Ensure that the report has been attached properly
        r.assertBuildStatus(Result.SUCCESS, run);
        HtmlPublisherTarget.HTMLAction jobReport = job.getAction(HtmlPublisherTarget.HTMLAction.class);
        HtmlPublisherTarget.HTMLBuildAction buildReport = run.getAction(HtmlPublisherTarget.HTMLBuildAction.class);
        assertNotNull("Report should be present at the project level", jobReport);
        assertEquals(target.getReportName(), jobReport.getHTMLTarget().getReportName());
        assertNull("Report should be missing at the run level", buildReport);
    }

    @Test
    public void publishReportOnBuildLevel() throws Exception {

        // Prepare the environment
        writeTestHTML("index.html");

        // Run the project
        final HtmlPublisherTarget target = new HtmlPublisherTarget
            ("testReport", TEST_REPORT_DIR, "index.html", true, false, false);
        target.setReportTitles("index");
        setupAndRunProject(target);

        // Ensure that the report has been attached properly
        r.assertBuildStatus(Result.SUCCESS, run);
        HtmlPublisherTarget.HTMLAction jobReport = job.getAction(HtmlPublisherTarget.HTMLAction.class);
        HtmlPublisherTarget.HTMLBuildAction buildReport = run.getAction(HtmlPublisherTarget.HTMLBuildAction.class);
        assertNotNull("Report should be present at the project level", jobReport);
        assertEquals(target.getReportName(), jobReport.getHTMLTarget().getReportName());
        assertNotNull("Report should be present at the run level", buildReport);
        assertEquals(target.getReportName(), buildReport.getHTMLTarget().getReportName());
    }

    @Test
    public void publishMissingReportFolder() throws Exception {
        final String missingReportDir = "testReportDirNonExistent";
        final HtmlPublisherTarget target = new HtmlPublisherTarget
            ("testReport", missingReportDir, "index.html", false, false, false);
        target.setReportTitles("index");
        setupAndRunProject(target);
        File missingReportDirFile = new File(testWorkspace, "workspace/" + TEST_PROJECT_NAME + "/" + missingReportDir);

        // Ensure that the report has been attached
        r.assertBuildStatus(Result.FAILURE, run);
        HtmlPublisherTarget.HTMLBuildAction report = run.getAction(HtmlPublisherTarget.HTMLBuildAction.class);
        assertNull("Report should be missing", report);
        r.assertLogContains("Specified HTML directory '" + missingReportDirFile + "' does not exist.", run);
    }

    @Test
    public void publishMissingReport_allowMissing() throws Exception {
        final HtmlPublisherTarget target = new HtmlPublisherTarget
                ("testReport", "testReportDirNonExistent", "index.html", false, false, true);
        target.setReportTitles("index");
        setupAndRunProject(target);

        // Ensure that the report has been attached
        r.assertBuildStatus(Result.SUCCESS, run);
        HtmlPublisherTarget.HTMLBuildAction report = run.getAction(HtmlPublisherTarget.HTMLBuildAction.class);
        assertNull("Report should be missing", report);
    }

    @Test
    public void testGetIconFileNameSymbolIcon() throws Exception {
        // Prepare the environment
        writeTestHTML("index.html");
        
        // Run the project
        HtmlPublisherTarget target = new HtmlPublisherTarget("testReport", TEST_REPORT_DIR, "index.html", true, false, false);
        target.setIcon("symbol-custom-icon");
        setupAndRunProject(target);
        
        // Verify that getIconFileName() correctly returns the custom icon
        HtmlPublisherTarget.HTMLAction jobReport = target.new HTMLAction(job, target); 
        assertNotNull("Report should exist", jobReport);
        assertEquals("symbol-custom-icon", jobReport.getIconFileName());
    }

    @Test
    public void testGetIconFileNameCustomPath() throws Exception {
        // Prepare the environment
        writeTestHTML("index.html");
        testReportDir.mkdirs();
        File customIconFile = new File(testReportDir, "custom-icon.png");
        customIconFile.createNewFile();

        // Run the project
        HtmlPublisherTarget target = new HtmlPublisherTarget("testReport", TEST_REPORT_DIR, "index.html", true, false, false);
        target.setIcon("custom-icon.png");
        setupAndRunProject(target);
        
        // Verify that getIconFileName() correctly returns the specified file
        HtmlPublisherTarget.HTMLAction jobReport = target.new HTMLAction(job, target); 
        assertNotNull("Report should exist", jobReport);
        assertTrue("Icon should contain project URL", jobReport.getIconFileName().contains("custom-icon.png"));
    }

    @Test
    public void testGetIconFileNameDefaultIfMissingFile() throws Exception {
        // Prepare the environment
        writeTestHTML("index.html");

        // Run the project
        HtmlPublisherTarget target = new HtmlPublisherTarget("testReport", TEST_REPORT_DIR, "index.html", true, false, false);
        target.setIcon("custom-icon.png");
        setupAndRunProject(target);
        
        // Verify that getIconFileName() correctly returns the default icon
        HtmlPublisherTarget.HTMLAction jobReport = target.new HTMLAction(job, target); 
        assertNotNull("Report should exist", jobReport);
        assertEquals("symbol-document-text", jobReport.getIconFileName());
    }

    private void writeTestHTML(String fileName) throws Exception {
        // Prepare the test file
        if (!testReportDir.exists() && !testReportDir.mkdirs()) {
            fail("Cannot create a temporary directory for the test");
        }
        final File index = new File(testReportDir, fileName);
        try (BufferedWriter bw = new BufferedWriter(new PrintWriter(index, "UTF-8"))) {
            bw.write("<html><head><title>Test page</title></head><body><p>Jenkins Rocks!</p></body></html>");
        }
    }

    private void setupAndRunProject(@NonNull HtmlPublisherTarget target) throws Exception {

        // Test node for the workflow
        r.jenkins.addNode(new DumbSlave("slave", "dummy", testWorkspace.getPath(), "1",
                Node.Mode.NORMAL, "", r.createComputerLauncher(null), RetentionStrategy.NOOP,
                Collections.emptyList())); // TODO JENKINS-26398 clumsy

        job = r.jenkins.createProject(WorkflowJob.class, TEST_PROJECT_NAME);
        job.setDefinition(new CpsFlowDefinition(""
                + "node('slave') {\n"
                + "  publishHTML(target: [allowMissing: " + target.getAllowMissing() +
                  ", keepAll: " + target.getKeepAll() + ", reportDir: '" + target.getReportDir() +
                  "', reportFiles: '" + target.getReportFiles() + "', reportName: '" + target.getReportName() + "']) \n"
                + "}", true));
        QueueTaskFuture<WorkflowRun> runFuture = job.scheduleBuild2(0);
        assertThat("build was actually scheduled", runFuture, Matchers.notNullValue());
        run = runFuture.get();
    }

    private void configRoundTrip(String reportName, String reportDir, String reportFiles) throws Exception {
        final HtmlPublisherTarget target = new HtmlPublisherTarget
                (reportName, reportDir, reportFiles, false, false, false);
        target.setReportTitles("index");
        configRoundTrip(target);
    }

    private void configRoundTrip(String reportName, String reportDir, String reportFiles,
            boolean keepAll, boolean alwaysLinkToLastBuild, boolean allowMissing) throws Exception {
        final HtmlPublisherTarget target = new HtmlPublisherTarget
                (reportName, reportDir, reportFiles, false, false, false);
        target.setReportTitles("index");
        configRoundTrip(target);
    }

    private void configRoundTrip(@NonNull HtmlPublisherTarget target) throws Exception {
        configRoundTrip(new PublishHTMLStep(target));
    }

    private void configRoundTrip(@NonNull PublishHTMLStep step) throws Exception {
        FreeStyleProject p = r.createFreeStyleProject();
        p.getBuildersList().add(new StepBuilder(step));
        // workaround for eclipse compiler Ambiguous method call
        p.save();
        r.jenkins.reload();

        FreeStyleProject reloaded = r.jenkins.getItemByFullName(p.getFullName(), FreeStyleProject.class);
        assertNotNull(reloaded);
        StepBuilder b = reloaded.getBuildersList().get(StepBuilder.class);
        assertNotNull(b);
        Step after = b.s;
        assertNotNull(after);
        assertEquals(step.getClass(), after.getClass());
        assertEquals("Initial and reloaded target configurations differ", step.getTarget(),
                ((PublishHTMLStep)after).getTarget());
    }
}
