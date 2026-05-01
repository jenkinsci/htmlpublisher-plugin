package htmlpublisher;

import hudson.EnvVars;
import hudson.FilePath;
import hudson.Launcher;
import hudson.model.*;
import hudson.model.queue.QueueTaskFuture;
import hudson.remoting.VirtualChannel;
import hudson.slaves.DumbSlave;
import hudson.slaves.EnvironmentVariablesNodeProperty;
import hudson.slaves.JNLPLauncher;
import hudson.slaves.RetentionStrategy;
import jenkins.MasterToSlaveFileCallable;
import org.apache.commons.io.FileUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.TemporaryDirectoryAllocator;
import org.jvnet.hudson.test.TestBuilder;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.GenericContainer;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.collection.IsEmptyCollection.empty;
import static org.hamcrest.core.IsNot.not;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.junit.jupiter.api.Assertions.*;

/**
 * @author Kohsuke Kawaguchi
 */
@WithJenkins
class HtmlPublisherIntegrationTest {

    private JenkinsRule j;

    private final TemporaryDirectoryAllocator tmp = new TemporaryDirectoryAllocator();
    private GenericContainer<?> agentContainer;
    private DumbSlave agent;

    @BeforeEach
    void setUp(JenkinsRule rule) {
        j = rule;
    }

    @AfterEach
    void dispose() throws IOException, InterruptedException {
        tmp.dispose();
        if (agentContainer != null) {
            agentContainer.stop();
        }
    }

    /**
     * Makes sure that the configuration survives the round trip.
     */
    @Test
    void testConfigRoundtrip() throws Exception {
        FreeStyleProject p = j.createFreeStyleProject();
        HtmlPublisherTarget[] l = {
                new HtmlPublisherTarget("a", "b", "c", true, true, false),
                new HtmlPublisherTarget("", "", "", false, false, false)
        };

        p.getPublishersList().add(new HtmlPublisher(Arrays.asList(l)));

        j.submit(j.createWebClient().getPage(p, "configure").getFormByName("config"));

        HtmlPublisher r = p.getPublishersList().get(HtmlPublisher.class);
        assertEquals(2, r.getReportTargets().size());

        j.assertEqualBeans(l[0], r.getReportTargets().get(0), "reportName,reportDir,reportFiles,keepAll,alwaysLinkToLastBuild,allowMissing");
        j.assertEqualBeans(l[1], r.getReportTargets().get(1), "reportName,reportDir,reportFiles,keepAll,alwaysLinkToLastBuild,allowMissing");
    }

    @Test
    void testIncludes() throws Exception {
        FreeStyleProject p = j.createFreeStyleProject("include_job");
        final String reportDir = "autogen";
        p.getBuildersList().add(new TestBuilder() {
            public boolean perform(AbstractBuild<?, ?> build, Launcher launcher,
                    BuildListener listener) throws InterruptedException, IOException {
                FilePath ws = build.getWorkspace().child(reportDir);
                ws.child("tab1.html").write("hello", "UTF-8");
                ws.child("tab2.html").write("hello", "UTF-8");
                ws.child("dummy.html").write("hello", "UTF-8");
                return true;
            }
        });
        HtmlPublisherTarget target1 = new HtmlPublisherTarget("tab1", reportDir, "tab1.html", true, true, false);
        //default behavior is include all
        target1.setIncludes(HtmlPublisherTarget.INCLUDE_ALL_PATTERN);
        HtmlPublisherTarget target2 = new HtmlPublisherTarget("tab2", reportDir, "tab2.html", true, true, false);
        String includes = "tab2.html";
        target2.setIncludes(includes);
        assertEquals(includes, target2.getIncludes());
        HtmlPublisherTarget[] l = { target1, target2 };
        p.getPublishersList().add(new HtmlPublisher(Arrays.asList(l)));
        AbstractBuild build = j.buildAndAssertSuccess(p);
        File base = new File(build.getRootDir(), "htmlreports");
        List<String> tab1Files = Arrays.asList(new File(base, "tab1").list());
        List<String> tab2Files = Arrays.asList(new File(base, "tab2").list());
        // tab1 file copied all files
        assertTrue(tab1Files.contains("dummy.html"));
        assertTrue(tab1Files.contains("tab1.html"));
        // tab2 should not include dummy
        assertTrue(tab2Files.contains("tab2.html"));
        assertFalse(tab2Files.contains("dummy.html"));
    }

    @Test
    @Issue("SECURITY-3303")
    void testNotFollowingSymlinks() throws Exception {
        createDockerAgent();
        final File directoryOnController = tmp.allocate();
        FileUtils.write(new File(directoryOnController, "test.txt"), "test", StandardCharsets.UTF_8);
        final String directoryOnControllerPath = directoryOnController.getAbsolutePath();
        FreeStyleProject p = j.createFreeStyleProject();
        p.getBuildersList().add(new TestBuilder() {
            @Override
            public boolean perform(AbstractBuild<?, ?> build, Launcher launcher, BuildListener listener) throws InterruptedException, IOException {
                FilePath workspace = build.getWorkspace();
                workspace.act(new MakeSymlink(directoryOnControllerPath));
                workspace.child("test3.txt").write("Hello", "UTF-8");
                return true;
            }
        });
        HtmlPublisherTarget target1 = new HtmlPublisherTarget("tab1", "", "tab1/test.txt,tab1/test2.txt,**/test3.txt", true, false, true);
        p.getPublishersList().add(new HtmlPublisher(List.of(target1)));
        p.setAssignedLabel(Label.get("agent"));
        FreeStyleBuild build = j.buildAndAssertSuccess(p);
        File base = new File(build.getRootDir(), "htmlreports");
        String[] list = base.list();
        assertNotNull(list);
        assertThat(Arrays.asList(list), not(empty()));
        File tab1 = new File(base, "tab1");
        list = tab1.list();
        assertNotNull(list);
        assertThat(Arrays.asList(list), not(empty()));

        File reports = new File(tab1, "tab1");
        assertFalse(reports.exists());
    }

    @Test
    void testVariableExpansion() throws Exception {
        FreeStyleProject p = j.createFreeStyleProject("variable_job");
        addEnvironmentVariable("MYREPORTNAME", "reportname");
        addEnvironmentVariable("MYREPORTFILES", "afile.html");
        addEnvironmentVariable("MYREPORTTITLE", "A Title");
        final String reportDir = "autogen";
        p.getBuildersList().add(new TestBuilder() {
            public boolean perform(AbstractBuild<?, ?> build, Launcher launcher,
                    BuildListener listener) throws InterruptedException, IOException {
                FilePath ws = build.getWorkspace().child(reportDir);
                ws.child("afile.html").write("hello", "UTF-8");
                return true;
            }
        });
        HtmlPublisherTarget target2 = new HtmlPublisherTarget("reportname", reportDir, "${MYREPORTFILES}", true, true, false );
        target2.setReportTitles("${MYREPORTTITLE}");
        List<HtmlPublisherTarget> targets = new ArrayList<>();
        targets.add(target2);
        p.getPublishersList().add(new HtmlPublisher(targets));
        AbstractBuild build = j.buildAndAssertSuccess(p);
        File base = new File(build.getRootDir(), "htmlreports");
        assertNotNull(new File(base, "reportname").list());
        List<String> tab2Files = Arrays.asList(new File(base, "reportname").list());
        assertTrue(tab2Files.contains("afile.html"));
    }

    @Test
    void testWithWildcardPatterns() throws Exception {
        FreeStyleProject p = j.createFreeStyleProject("variable_job");
        final String reportDir = "autogen";
        p.getBuildersList().add(new TestBuilder() {
            public boolean perform(AbstractBuild<?, ?> build, Launcher launcher, BuildListener listener) throws InterruptedException, IOException {
                FilePath ws = build.getWorkspace().child(reportDir);
                ws.child("nested1").child("aReportDir").child("nested").child("afile.html").write("hello", "UTF-8");
                ws.child("notincluded").child("afile.html").write("hello", "UTF-8");
                ws.child("otherDir").child("afile.html").write("hello", "UTF-8");
                return true;
            }
        });
        HtmlPublisherTarget target2 = new HtmlPublisherTarget("reportname", reportDir, "**/aReportDir/*/afile.html, **/otherDir/afile.html", true, true, false);
        List<HtmlPublisherTarget> targets = new ArrayList<>();
        targets.add(target2);
        p.getPublishersList().add(new HtmlPublisher(targets));
        AbstractBuild build = j.buildAndAssertSuccess(p);
        File wrapperFile = new File(build.getRootDir(), "htmlreports/reportname/htmlpublisher-wrapper.html");
        assertTrue(wrapperFile.exists());
        String content = new String(Files.readAllBytes(wrapperFile.toPath()));
        assertTrue(content.contains("nested1/aReportDir/nested/afile.html"));
        assertTrue(content.contains("otherDir/afile.html"));
        assertFalse(content.contains("notincluded/afile.html"));
    }

    @Test
    void testAllowMissingStillRunsSubsequentReports() throws Exception {
        FreeStyleProject p = j.createFreeStyleProject("variable_job");
        p.getBuildersList().add(new TestBuilder() {
            public boolean perform(AbstractBuild<?, ?> build, Launcher launcher, BuildListener listener) throws InterruptedException, IOException {
                FilePath ws = build.getWorkspace();
                ws.child("dirA").child("afile.html").write("hello", "UTF-8");
                return true;
            }
        });
        HtmlPublisherTarget target1 = new HtmlPublisherTarget("reportnameB", "dirB", "", true, true, true);
        HtmlPublisherTarget target2 = new HtmlPublisherTarget("reportnameA", "dirA", "", true, true, false);

        List<HtmlPublisherTarget> targets = new ArrayList<>();
        targets.add(target1);
        targets.add(target2);
        p.getPublishersList().add(new HtmlPublisher(targets));
        AbstractBuild build = j.buildAndAssertSuccess(p);
        assertTrue(new File(build.getRootDir(), "htmlreports/reportnameA/htmlpublisher-wrapper.html").exists());
        assertFalse(new File(build.getRootDir(), "htmlreports/reportnameB/htmlpublisher-wrapper.html").exists());
    }

    @Test
    void testNotAllowMissingDoesntRunSubsequentReports() throws Exception {
        FreeStyleProject p = j.createFreeStyleProject("variable_job");
        p.getBuildersList().add(new TestBuilder() {
            public boolean perform(AbstractBuild<?, ?> build, Launcher launcher, BuildListener listener) throws InterruptedException, IOException {
                FilePath ws = build.getWorkspace();
                ws.child("dirA").child("afile.html").write("hello", "UTF-8");
                return true;
            }
        });
        HtmlPublisherTarget target1 = new HtmlPublisherTarget("reportnameB", "dirB", "", true, true, false);
        HtmlPublisherTarget target2 = new HtmlPublisherTarget("reportnameA", "dirA", "", true, true, false);

        List<HtmlPublisherTarget> targets = new ArrayList<>();
        targets.add(target1);
        targets.add(target2);
        p.getPublishersList().add(new HtmlPublisher(targets));
        QueueTaskFuture<FreeStyleBuild> task = p.scheduleBuild2(0);
        AbstractBuild build = j.assertBuildStatus(Result.FAILURE, task);
        assertFalse(new File(build.getRootDir(), "htmlreports/reportnameA/htmlpublisher-wrapper.html").exists());
        assertFalse(new File(build.getRootDir(), "htmlreports/reportnameB/htmlpublisher-wrapper.html").exists());
    }

    @Test
    void testUseWrapperFileDirectly() throws Exception {
        FreeStyleProject p = j.createFreeStyleProject("use_wrapper_job");
        final String reportDir = "autogen_use_wrapper";
        p.getBuildersList().add(new TestBuilder() {
            public boolean perform(AbstractBuild<?, ?> build, Launcher launcher,
                    BuildListener listener) throws InterruptedException, IOException {
                FilePath ws = build.getWorkspace().child(reportDir);
                ws.child("tab1.html").write("hello", "UTF-8");
                ws.child("tab2.html").write("hello", "UTF-8");
                return true;
            }
        });


        HtmlPublisherTarget target1 = new HtmlPublisherTarget("tab1", reportDir, "tab1.html", true, true, false);
        //default behavior is to not use wrapper file directly
        target1.setUseWrapperFileDirectly(false);
        assertFalse(target1.getUseWrapperFileDirectly());
        HtmlPublisherTarget target2 = new HtmlPublisherTarget("tab2", reportDir, "tab2.html", true, true, false);
        target2.setUseWrapperFileDirectly(true);
        assertTrue(target2.getUseWrapperFileDirectly());

        //No impact on wrapper generation
        List<HtmlPublisherTarget> targets = new ArrayList<>();
        targets.add(target1);
        targets.add(target2);
        p.getPublishersList().add(new HtmlPublisher(targets));
        AbstractBuild build = j.buildAndAssertSuccess(p);

        assertTrue(new File(build.getRootDir(), "htmlreports/tab1/htmlpublisher-wrapper.html").exists());
        assertTrue(new File(build.getRootDir(), "htmlreports/tab2/htmlpublisher-wrapper.html").exists());
    }

    @Test
    void testMultithreaded() throws Exception {
        FreeStyleProject p = j.createFreeStyleProject("variable_job");
        p.getBuildersList().add(new TestBuilder() {
            public boolean perform(AbstractBuild<?, ?> build, Launcher launcher, BuildListener listener) throws InterruptedException, IOException {
                FilePath ws = build.getWorkspace();
                ws.child("dirA").child("file1.html").write("hello", "UTF-8");
                ws.child("dirA").child("file2.html").write("hello", "UTF-8");
                return true;
            }
        });
        HtmlPublisherTarget target1 = new HtmlPublisherTarget("reportnameB", "dirB", "", true, true, true);
        target1.setNumberOfWorkers(2);
        HtmlPublisherTarget target2 = new HtmlPublisherTarget("reportnameA", "dirA", "", true, true, false);
        target2.setNumberOfWorkers(2);

        List<HtmlPublisherTarget> targets = new ArrayList<>();
        targets.add(target1);
        targets.add(target2);
        p.getPublishersList().add(new HtmlPublisher(targets));
        AbstractBuild build = j.buildAndAssertSuccess(p);
        assertTrue(new File(build.getRootDir(), "htmlreports/reportnameA/htmlpublisher-wrapper.html").exists(), "reportnameA/htmlpublisher-wrapper.html must exist");
        assertTrue(new File(build.getRootDir(), "htmlreports/reportnameA/file1.html").exists(), "reportnameA/file1.html must exist");
        assertTrue(new File(build.getRootDir(), "htmlreports/reportnameA/file2.html").exists(), "reportnameA/file2.html must exist");
        assertFalse(new File(build.getRootDir(), "htmlreports/reportnameB/htmlpublisher-wrapper.html").exists(), "reportnameB/htmlpublisher-wrapper.html must not exist");
    }

    @Test
    void testIcon() throws Exception {
        FreeStyleProject p = j.createFreeStyleProject("variable_job");
        p.getBuildersList().add(new TestBuilder() {
            public boolean perform(AbstractBuild<?, ?> build, Launcher launcher, BuildListener listener) throws InterruptedException, IOException {
                FilePath ws = build.getWorkspace();
                ws.child("dirA").child("file1.html").write("hello", "UTF-8");
                ws.child("dirA").child("file2.html").write("hello", "UTF-8");
                return true;
            }
        });
        HtmlPublisherTarget target1 = new HtmlPublisherTarget("reportnameB", "dirB", "", true, true, true);
        target1.setIcon("symbol-cube");
        HtmlPublisherTarget target2 = new HtmlPublisherTarget("reportnameA", "dirA", "", true, true, false);
        target2.setIcon("symbol-build");

        List<HtmlPublisherTarget> targets = new ArrayList<>();
        targets.add(target1);
        targets.add(target2);
        p.getPublishersList().add(new HtmlPublisher(targets));
        AbstractBuild build = j.buildAndAssertSuccess(p);
        assertTrue(new File(build.getRootDir(), "htmlreports/reportnameA/htmlpublisher-wrapper.html").exists(), "reportnameA/htmlpublisher-wrapper.html must exist");
        assertTrue(new File(build.getRootDir(), "htmlreports/reportnameA/file1.html").exists(), "reportnameA/file1.html must exist");
        assertTrue(new File(build.getRootDir(), "htmlreports/reportnameA/file2.html").exists(), "reportnameA/file2.html must exist");
        assertFalse(new File(build.getRootDir(), "htmlreports/reportnameB/htmlpublisher-wrapper.html").exists(), "reportnameB/htmlpublisher-wrapper.html must not exist");
    }

    @Test
    void testPublishesTwoReportsOneJob() throws Exception {
        FreeStyleProject p = j.createFreeStyleProject("variable_job");
        p.getBuildersList().add(new TestBuilder() {
            public boolean perform(AbstractBuild<?, ?> build, Launcher launcher, BuildListener listener) throws InterruptedException, IOException {
                FilePath ws = build.getWorkspace();
                ws.child("dirA").child("afile.html").write("hello", "UTF-8");
                ws.child("dirB").child("bfile.html").write("goodbye", "UTF-8");
                return true;
            }
        });
        HtmlPublisherTarget target1 = new HtmlPublisherTarget("reportnameB", "dirB", "", true, true, false);
        HtmlPublisherTarget target2 = new HtmlPublisherTarget("reportnameA", "dirA", "", true, true, false);

        List<HtmlPublisherTarget> targets = new ArrayList<>();
        targets.add(target1);
        targets.add(target2);
        p.getPublishersList().add(new HtmlPublisher(targets));
        AbstractBuild build = j.buildAndAssertSuccess(p);
        assertTrue(new File(build.getRootDir(), "htmlreports/reportnameA/htmlpublisher-wrapper.html").exists());
        assertTrue(new File(build.getRootDir(), "htmlreports/reportnameB/htmlpublisher-wrapper.html").exists());
    }

    @Test
    void testThrowingIOException() throws Exception {
        HtmlPublisherTarget[] l = {
                new HtmlPublisherTarget("a", "b", "c", true, true, false),
        };

        HtmlPublisher htmlpublisher = new HtmlPublisher(Arrays.asList(l), false);

        // Check, that IOException is propagated
		assertThrows(IOException.class, htmlpublisher::perform);
    }

    private void addEnvironmentVariable(String key, String value) {
        EnvironmentVariablesNodeProperty prop = new EnvironmentVariablesNodeProperty();
        EnvVars envVars = prop.getEnvVars();
        envVars.put(key, value);
        j.jenkins.getGlobalNodeProperties().add(prop);
    }

    private void createDockerAgent() throws Exception {
        assumeTrue(DockerClientFactory.instance().isDockerAvailable(), "Needs Docker");
        j.jenkins.setSlaveAgentPort(0);
        int port = j.jenkins.getTcpSlaveAgentListener().getAdvertisedPort();
        synchronized (j.jenkins) {
            agent = new DumbSlave("dockeragentOne", "/home/jenkins/work", new JNLPLauncher());
            agent.setLabelString("agent");
            agent.setRetentionStrategy(RetentionStrategy.NOOP);
            j.jenkins.addNode(agent);
        }
        Map<String, String> env = Map.of("JENKINS_URL", JNLPLauncher.getInboundAgentUrl(),
                "JENKINS_SECRET", agent.getComputer().getJnlpMac(),
                "JENKINS_AGENT_NAME", agent.getNodeName(),
                "JENKINS_AGENT_WORKDIR", agent.getRemoteFS(),
                "JENKINS_WEB_SOCKET", "true");
        System.out.println(env);

        agentContainer = new GenericContainer<>("jenkins/inbound-agent:jdk" + System.getProperty("java.specification.version"))
                .withEnv(env)
                .withNetworkMode("host").withLogConsumer(outputFrame -> System.out.print(outputFrame.getUtf8String()));
        //agentContainer.getHost()
        agentContainer.start();
        j.waitOnline(agent);
    }

    static class MakeSymlink extends MasterToSlaveFileCallable<Void> {
        final String target;

        MakeSymlink(String target) {
            this.target = target;
        }

        @Override
        public Void invoke(File f, VirtualChannel channel) throws IOException {
            Files.createSymbolicLink(Paths.get(f.getAbsolutePath(), "tab1"), Paths.get(target));
            return null;
        }
    }
}
