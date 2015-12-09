package htmlpublisher.HtmlPublisherTarget.BaseHTMLAction

import htmlpublisher.HtmlPublisherTarget
import hudson.Util

import java.security.MessageDigest

def text = new File(my.dir(), my.getHTMLTarget().getWrapperName()).text

def actual = Util.toHexString(MessageDigest.getInstance("SHA-1").digest(text.getBytes("UTF-8")))

def expected = null

if (my instanceof HtmlPublisherTarget.HTMLBuildAction) {
    // this is a build action, so needs to have its checksum checked
    expected = my.wrapperChecksum
} else if (my instanceof HtmlPublisherTarget.HTMLAction && my.actualBuildAction) {
    // this is a project action serving a build-level report
    expected = my.actualBuildAction.wrapperChecksum
} // else this is a project action serving a project-level report

if (expected == null) {
    // no checksum expected
    raw(new File(my.dir(), "htmlpublisher-wrapper.html").text)
} else {
    if (expected == actual) {
        // checksum expected and matches
        raw(new File(my.dir(), "htmlpublisher-wrapper.html").text)
    } else {
        def l = namespace(lib.LayoutTagLib)
        def f = namespace(lib.FormTagLib)

        l.layout {
            l.header(title:"Checksum mismatch")
            l.main_panel {
                h1(_("Checksum mismatch")) {
                    l.icon(class: 'icon-error icon-xlg')
                }
                p(raw(_("msg", actual, expected)))
            }
        }
    }
}
