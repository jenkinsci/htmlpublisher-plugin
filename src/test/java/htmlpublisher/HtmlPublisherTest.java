package htmlpublisher;

import org.junit.Test;

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
    public void testNumberOfThreads() {
        HtmlPublisherTarget target = new HtmlPublisherTarget("tab1", "target", "tab1.html", true, true, false);
        
        // Test default behavior
        assertEquals(target.getNumberOfThreads(), Integer.valueOf(0));
        
        // Test explicit value
        target.setNumberOfThreads(0);
        assertEquals(target.getNumberOfThreads(), Integer.valueOf(0));

        target.setNumberOfThreads(1);
        assertEquals(target.getNumberOfThreads(), Integer.valueOf(1));
    }
}
