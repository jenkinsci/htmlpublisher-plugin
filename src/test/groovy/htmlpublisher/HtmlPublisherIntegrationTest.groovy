package htmlpublisher

import hudson.FilePath
import hudson.model.AbstractBuild
import hudson.model.BuildListener
import hudson.Launcher
import org.jvnet.hudson.test.HudsonTestCase
import org.jvnet.hudson.test.TestBuilder

/**
 *
 *
 * @author Kohsuke Kawaguchi
 */
public class HtmlPublisherIntegrationTest extends HudsonTestCase {
    /**
     * Makes sure that the configuration survives the round trip.
     */
    public void testConfigRoundtrip() {
        def p = createFreeStyleProject();
        def l = [new HtmlPublisherTarget("a", "b", "c", "", true, true, false), new HtmlPublisherTarget("", "", "", "", false, false, false)]

        p.publishersList.add(new HtmlPublisher(l));
        submit(createWebClient().getPage(p, "configure").getFormByName("config"));

        def r = p.publishersList.get(HtmlPublisher.class)
        assertEquals(2, r.reportTargets.size())

        (0..1).each {
            assertEqualBeans(l[it], r.reportTargets[it], "reportName,reportDir,reportFiles,keepAll,alwaysLinkToLastBuild,allowMissing");
        }
    }


    public void testIncludes() {
        def p = createFreeStyleProject("include_job");
        def reportDir = "autogen"
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
        target1.setIncludes(HtmlPublisherTarget.INCLUDE_ALL_PATTERN)
        HtmlPublisherTarget target2 = new HtmlPublisherTarget("tab2", reportDir, "tab2.html", true, true, false);
        String includes = "tab2.html"
        target2.setIncludes(includes)
        assertEquals(includes, target2.getIncludes());
        def l = [target1, target2]
        p.publishersList.add(new HtmlPublisher(l));
        AbstractBuild build = buildAndAssertSuccess(p);
        File base = new File(build.getRootDir(), "htmlreports");
        def tab1Files = new File(base, "tab1").list()
        def tab2Files = new File(base, "tab2").list()
        // tab1 file copied all files
        assertTrue(tab1Files.contains("dummy.html"))
        assertTrue(tab1Files.contains("tab1.html"))
        // tab2 should not include dummy
        assertTrue(tab2Files.contains("tab2.html"))
        assertFalse(tab2Files.contains("dummy.html"))
    }


}
