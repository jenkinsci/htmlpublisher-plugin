package htmlpublisher;

import hudson.EnvVars;
import hudson.FilePath;
import hudson.Launcher;
import hudson.model.AbstractBuild;
import hudson.model.BuildListener;
import hudson.model.FreeStyleProject;
import hudson.slaves.EnvironmentVariablesNodeProperty;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.TestBuilder;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.*;

/**
 * @author Kohsuke Kawaguchi
 */
public class HtmlPublisherIntegrationTest {
    @Rule
    public JenkinsRule j = new JenkinsRule();

    /**
     * Makes sure that the configuration survives the round trip.
     */
    @Test
    public void testConfigRoundtrip() throws Exception {
        FreeStyleProject p = j.createFreeStyleProject();
        HtmlPublisherTarget[] l = {
                new HtmlPublisherTarget("a", "b", "c", "", true, true, false),
                new HtmlPublisherTarget("", "", "", "", false, false, false)
        };

        p.getPublishersList().add(new HtmlPublisher(Arrays.asList(l)));

        j.submit(j.createWebClient().getPage(p, "configure").getFormByName("config"));

        HtmlPublisher r = p.getPublishersList().get(HtmlPublisher.class);
        assertEquals(2, r.getReportTargets().size());

        j.assertEqualBeans(l[0], r.getReportTargets().get(0), "reportName,reportDir,reportFiles,keepAll,alwaysLinkToLastBuild,allowMissing");
        j.assertEqualBeans(l[1], r.getReportTargets().get(1), "reportName,reportDir,reportFiles,keepAll,alwaysLinkToLastBuild,allowMissing");
    }

    @Test
    public void testIncludes() throws Exception {
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
    public void testVariableExpansion() throws Exception {
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
        HtmlPublisherTarget target2 = new HtmlPublisherTarget("reportname", reportDir, "${MYREPORTFILES}", "${MYREPORTTITLE}", true, true, false );
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
	public void testWithWildcardPatterns() throws Exception {
		FreeStyleProject p = j.createFreeStyleProject("variable_job");
		final String reportDir = "autogen";
		p.getBuildersList().add(new TestBuilder() {
			public boolean perform(AbstractBuild<?, ?> build, Launcher launcher, BuildListener listener)
					throws InterruptedException, IOException {
				FilePath ws = build.getWorkspace().child(reportDir);
				ws.child("nested1").child("aReportDir").child("nested").child("afile.html").write("hello", "UTF-8");
				ws.child("notincluded").child("afile.html").write("hello", "UTF-8");
				ws.child("otherDir").child("afile.html").write("hello", "UTF-8");
				return true;
			}
		});
		HtmlPublisherTarget target2 = new HtmlPublisherTarget("reportname", reportDir,
				"**/aReportDir/*/afile.html, **/otherDir/afile.html", "A title", true, true, false);
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
    
    private void addEnvironmentVariable(String key, String value) {
        EnvironmentVariablesNodeProperty prop = new EnvironmentVariablesNodeProperty();
        EnvVars envVars = prop.getEnvVars();
        envVars.put(key, value);
        j.jenkins.getGlobalNodeProperties().add(prop);
    }


}
