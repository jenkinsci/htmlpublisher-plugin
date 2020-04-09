package htmlpublisher;

import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.model.FreeStyleProject;
import org.junit.Test;
import org.mockito.Mockito;

import static org.junit.Assert.assertEquals;

public class HtmlPublisherTest {
    @Test
    public void testDefaultIncludes() {
        HtmlPublisherTarget target1 = new HtmlPublisherTarget("tab1", "target", "tab1.html", true, true, false);
        assertEquals(HtmlPublisherTarget.INCLUDE_ALL_PATTERN, target1.getIncludes());
        target1.setIncludes(null);
        assertEquals(HtmlPublisherTarget.INCLUDE_ALL_PATTERN, target1.getIncludes());
        target1.setIncludes("hello");
        assertEquals("hello", target1.getIncludes());
        target1.setIncludes("");
        assertEquals(HtmlPublisherTarget.INCLUDE_ALL_PATTERN, target1.getIncludes());
    }

    @Test
    public void testSpacesTrimmed() {
        HtmlPublisherTarget target = new HtmlPublisherTarget("tab1 ", "target ", "tab1.html ", true, true, false);
        assertEquals(target.getReportName(), "tab1");
        assertEquals(target.getReportDir(), "target");
        assertEquals(target.getReportFiles(), "tab1.html");
    }

    @Test
    public void testEscapeUnderscores() {
        // test underscores escaped when requested
        assertEquals(HtmlPublisherTarget.sanitizeReportName("foo_bar", true), HtmlPublisherTarget.sanitizeReportName("foo_5fbar", false));

        // test underscores not escaped when not requested
        assertEquals(HtmlPublisherTarget.sanitizeReportName("foo_bar", false), HtmlPublisherTarget.sanitizeReportName("foo_bar", false));

    }

    @Test
    public void testActionEqual() {
        AbstractBuild build = Mockito.mock(AbstractBuild.class);

        HtmlPublisherTarget targetOne = new HtmlPublisherTarget("tab1 ", "target ", "tab1.html ", true, true, false);
        assertEquals(targetOne.getReportName(), "tab1");
        assertEquals(targetOne.getReportDir(), "target");
        assertEquals(targetOne.getReportFiles(), "tab1.html");

        HtmlPublisherTarget targetTwo = new HtmlPublisherTarget("tab1 ", "target ", "tab1.html ", true, true, false);
        assertEquals(targetTwo.getReportName(), "tab1");
        assertEquals(targetTwo.getReportDir(), "target");
        assertEquals(targetTwo.getReportFiles(), "tab1.html");

        HtmlPublisherTarget.HTMLBuildAction actionOne = targetOne.new HTMLBuildAction(build, targetOne);
        HtmlPublisherTarget.HTMLBuildAction actionTwo = targetOne.new HTMLBuildAction(build, targetTwo);

        assertEquals(actionOne, actionTwo);
    }
}
