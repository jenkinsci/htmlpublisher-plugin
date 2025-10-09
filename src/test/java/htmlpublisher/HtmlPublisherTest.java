package htmlpublisher;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class
HtmlPublisherTest {

    @Test
    void testDefaultIncludes() {
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
    void testSpacesTrimmed() {
        HtmlPublisherTarget target = new HtmlPublisherTarget("tab1 ", "target ", "tab1.html ", true, true, false);
        assertEquals("tab1", target.getReportName());
        assertEquals("target", target.getReportDir());
        assertEquals("tab1.html", target.getReportFiles());
    }

    @Test
    void testEscapeUnderscores() {
        // test underscores escaped when requested
        assertEquals(HtmlPublisherTarget.sanitizeReportName("foo_bar", true), HtmlPublisherTarget.sanitizeReportName("foo_5fbar", false));

        // test underscores not escaped when not requested
        assertEquals(HtmlPublisherTarget.sanitizeReportName("foo_bar", false), HtmlPublisherTarget.sanitizeReportName("foo_bar", false));

    }

    @Test
    void testNumberOfWorkers() {
        HtmlPublisherTarget target = new HtmlPublisherTarget("tab1", "target", "tab1.html", true, true, false);

        // Test default behavior
        assertEquals(0, target.getNumberOfWorkers());

        // Test explicit value
        target.setNumberOfWorkers(0);
        assertEquals(0, target.getNumberOfWorkers());

        target.setNumberOfWorkers(1);
        assertEquals(1, target.getNumberOfWorkers());
    }
}
