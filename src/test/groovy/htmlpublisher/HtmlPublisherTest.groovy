package htmlpublisher

import org.jvnet.hudson.test.HudsonTestCase

/**
 *
 *
 * @author Kohsuke Kawaguchi
 */
public class HtmlPublisherTest extends HudsonTestCase {
    /**
     * Makes sure that the configuration survives the round trip.
     */
    public void testConfigRoundtrip() {
        def p = createFreeStyleProject();
        def l = [new HtmlPublisherTarget("a", "b", "c","", true, true, false), new HtmlPublisherTarget("", "", "","", false, false, false)]

        p.publishersList.add(new HtmlPublisher(l));
        submit(createWebClient().getPage(p,"configure").getFormByName("config"));

        def r = p.publishersList.get(HtmlPublisher.class)
        assertEquals(2,r.reportTargets.size())

        (0..1).each {
            assertEqualBeans(l[it],r.reportTargets[it],"reportName,reportDir,reportFiles,keepAll,alwaysLinkToLastBuild,allowMissing");
        }
    }

}
