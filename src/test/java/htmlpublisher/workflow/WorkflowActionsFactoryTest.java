/*
 * The MIT License
 *
 * Copyright 2025 jenkinsci.
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

import htmlpublisher.HtmlPublisherTarget;
import hudson.model.Action;
import hudson.model.Job;
import hudson.model.Result;
import org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;

import java.util.Collection;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for WorkflowActionsFactory
 */
@WithJenkins
class WorkflowActionsFactoryTest {

    private JenkinsRule j;

    private WorkflowActionsFactory factory;
    private HtmlPublisherTarget testTarget;
    private static final String TEST_REPORT_DIR = "htmlreports";

    @BeforeEach
    void setUp(JenkinsRule rule) {
        j = rule;
        factory = new WorkflowActionsFactory();
        testTarget = new HtmlPublisherTarget("TestReport", TEST_REPORT_DIR, "index.html", false, false, false);
    }

    @Test
    void testTypeReturnsJobClass() {
        assertEquals(Job.class, factory.type(), "Factory should return Job.class as its type");
    }

    @Test
    void testNonWorkflowJobReturnsEmptyActions() throws Exception {
        // Create a freestyle project (non-workflow)
        hudson.model.FreeStyleProject project = j.createFreeStyleProject("freestyle-test");
        
        Collection<? extends Action> actions = factory.createFor(project);
        
        assertNotNull(actions, "Actions collection should not be null");
        assertTrue(actions.isEmpty(), "Non-workflow job should return empty actions");
    }

    @Test
    void testWorkflowJobWithNoBuildsReturnsEmptyActions() throws Exception {
        // Create a workflow job with no builds
        WorkflowJob job = j.jenkins.createProject(WorkflowJob.class, "workflow-no-builds");
        job.setDefinition(new CpsFlowDefinition("echo 'Hello World'", true));
        
        Collection<? extends Action> actions = factory.createFor(job);
        
        assertNotNull(actions, "Actions collection should not be null");
        assertTrue(actions.isEmpty(), "Job with no builds should return empty actions");
    }

    @Test
    void testWorkflowJobWithSuccessfulBuildAndKeepAllTrue() throws Exception {
        // Create workflow job without trying to run the pipeline
        WorkflowJob job = j.jenkins.createProject(WorkflowJob.class, "workflow-keepall-success");
        
        // Don't set a definition or run the job, just test the factory logic
        // Setup testTarget for this test
        testTarget = new HtmlPublisherTarget("TestReport", TEST_REPORT_DIR, "index.html", true, false, false);
        
        // Create a mock successful build manually
        WorkflowRun build = job.createExecutable();
        build.setResult(Result.SUCCESS);
        
        // Manually add HTMLBuildAction since our test doesn't run actual publisher
        HtmlPublisherTarget.HTMLBuildAction buildAction = testTarget.new HTMLBuildAction(build, testTarget);
        build.addAction(buildAction);
        
        // Test the factory
        Collection<? extends Action> actions = factory.createFor(job);
        
        assertNotNull(actions, "Actions collection should not be null");
        assertEquals(1, actions.size(), "Should return 1 action for keepAll=true with successful build");
        
        Action action = actions.iterator().next();
        assertTrue(action instanceof HtmlPublisherTarget.HTMLAction, "Action should be HTMLAction");
        
        HtmlPublisherTarget.HTMLAction htmlAction = (HtmlPublisherTarget.HTMLAction) action;
        assertEquals("TestReport", htmlAction.getHTMLTarget().getReportName(), "Report name should match");
    }

    @Test
    void testWorkflowJobWithFailedBuildAndKeepAllFalse() throws Exception {
        // Create workflow job with a simple failed build (no writeFile needed)
        WorkflowJob job = j.jenkins.createProject(WorkflowJob.class, "workflow-nokeepall-failure");
        job.setDefinition(new CpsFlowDefinition("error 'Simulated failure'", true));
        
        WorkflowRun build = j.buildAndAssertStatus(Result.FAILURE, job);
        
        // Setup testTarget for this test
        testTarget = new HtmlPublisherTarget("TestReport", TEST_REPORT_DIR, "index.html", false, false, false);
        
        // Manually add the marker action since our test setup doesn't run the actual publisher
        HtmlPublisherTarget.HTMLPublishedForProjectMarkerAction markerAction = 
            new HtmlPublisherTarget.HTMLPublishedForProjectMarkerAction(build, testTarget);
        build.addAction(markerAction);
        
        // Test the factory
        Collection<? extends Action> actions = factory.createFor(job);
        
        assertNotNull(actions, "Actions collection should not be null");
        assertEquals(1, actions.size(), "Should return 1 action for keepAll=false with failed build");
        
        Action action = actions.iterator().next();
        assertTrue(action instanceof HtmlPublisherTarget.HTMLAction, "Action should be HTMLAction");
        
        HtmlPublisherTarget.HTMLAction htmlAction = (HtmlPublisherTarget.HTMLAction) action;
        assertEquals("TestReport", htmlAction.getHTMLTarget().getReportName(), "Report name should match");
    }

    @Test
    void testWorkflowJobWithSuccessfulAndFailedBuilds() throws Exception {
        // Test scenario: last successful build has build actions, last build (failed) has marker actions
        WorkflowJob job = j.jenkins.createProject(WorkflowJob.class, "workflow-mixed-builds");
        
        // Create first successful build manually
        WorkflowRun successfulBuild = job.createExecutable();
        successfulBuild.setResult(Result.SUCCESS);
        HtmlPublisherTarget successTarget = new HtmlPublisherTarget("SuccessReport", TEST_REPORT_DIR, "index.html", true, false, false);
        HtmlPublisherTarget.HTMLBuildAction buildAction = 
            successTarget.new HTMLBuildAction(successfulBuild, successTarget);
        successfulBuild.addAction(buildAction);
        
        // Create second failed build manually
        WorkflowRun failedBuild = job.createExecutable();
        failedBuild.setResult(Result.FAILURE);
        HtmlPublisherTarget failTarget = new HtmlPublisherTarget("FailReport", TEST_REPORT_DIR, "index.html", false, false, false);
        HtmlPublisherTarget.HTMLPublishedForProjectMarkerAction markerAction = 
            new HtmlPublisherTarget.HTMLPublishedForProjectMarkerAction(failedBuild, failTarget);
        failedBuild.addAction(markerAction);
        
        // Test the factory - should find both reports
        Collection<? extends Action> actions = factory.createFor(job);
        
        assertNotNull(actions, "Actions collection should not be null");
        assertEquals(2, actions.size(), "Should return 2 actions (one from successful build, one from failed build)");
        
        // Verify both reports are present
        boolean foundSuccessReport = false, foundFailReport = false;
        for (Action action : actions) {
            assertTrue(action instanceof HtmlPublisherTarget.HTMLAction, "All actions should be HTMLAction");
            HtmlPublisherTarget.HTMLAction htmlAction = (HtmlPublisherTarget.HTMLAction) action;
            String reportName = htmlAction.getHTMLTarget().getReportName();
            if ("SuccessReport".equals(reportName)) {
                foundSuccessReport = true;
            } else if ("FailReport".equals(reportName)) {
                foundFailReport = true;
            }
        }
        
        assertTrue(foundSuccessReport, "Should find SuccessReport from last successful build");
        assertTrue(foundFailReport, "Should find FailReport from last (failed) build");
    }

    @Test
    void testWorkflowJobWithOnlyFailedBuilds() throws Exception {
        // Test scenario: no successful builds, only failed builds with marker actions
        WorkflowJob job = j.jenkins.createProject(WorkflowJob.class, "workflow-only-failed");
        job.setDefinition(new CpsFlowDefinition(
            "node {\n" +
            "  writeFile file: 'htmlreports/index.html', text: '<html><body>Failed Report</body></html>'\n" +
            "  error 'Simulated failure'\n" +
            "}", true));
        
        WorkflowRun failedBuild = j.buildAndAssertStatus(Result.FAILURE, job);
        
        // Add marker action for keepAll=false scenario
        HtmlPublisherTarget.HTMLPublishedForProjectMarkerAction markerAction = 
            new HtmlPublisherTarget.HTMLPublishedForProjectMarkerAction(failedBuild, testTarget);
        failedBuild.addAction(markerAction);
        
        // Test the factory
        Collection<? extends Action> actions = factory.createFor(job);
        
        assertNotNull(actions, "Actions collection should not be null");
        assertEquals(1, actions.size(), "Should return 1 action from failed build");
        
        Action action = actions.iterator().next();
        assertTrue(action instanceof HtmlPublisherTarget.HTMLAction, "Action should be HTMLAction");
        
        HtmlPublisherTarget.HTMLAction htmlAction = (HtmlPublisherTarget.HTMLAction) action;
        assertEquals("TestReport", htmlAction.getHTMLTarget().getReportName(), "Report name should match");
    }

    @Test
    void testWorkflowJobClassNameMatching() throws Exception {
        // Test that the factory correctly identifies workflow jobs by class name
        WorkflowJob job = j.jenkins.createProject(WorkflowJob.class, "workflow-classname-test");
        
        // The class name should start with "org.jenkinsci.plugins.workflow"
        assertTrue(job.getClass().getCanonicalName().startsWith("org.jenkinsci.plugins.workflow"), 
            "WorkflowJob class name should start with workflow package");
        
        // Even with no builds, it should process workflow jobs (but return empty)
        Collection<? extends Action> actions = factory.createFor(job);
        assertNotNull(actions, "Actions collection should not be null");
        assertTrue(actions.isEmpty(), "No builds should result in empty actions");
    }

    @Test
    void testFactoryLogicWithNullBuilds() throws Exception {
        // Test factory behavior when getLastSuccessfulBuild() and getLastBuild() return null
        WorkflowJob job = j.jenkins.createProject(WorkflowJob.class, "workflow-null-builds");
        // Don't run any builds
        
        Collection<? extends Action> actions = factory.createFor(job);
        
        assertNotNull(actions, "Actions collection should not be null");
        assertTrue(actions.isEmpty(), "No builds should result in empty actions");
    }
}