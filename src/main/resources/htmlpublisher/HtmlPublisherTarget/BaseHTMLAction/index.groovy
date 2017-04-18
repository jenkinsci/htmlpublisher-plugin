package htmlpublisher.HtmlPublisherTarget.BaseHTMLAction

import htmlpublisher.HtmlPublisher
import htmlpublisher.HtmlPublisherTarget
import hudson.Util

import java.security.MessageDigest

l = namespace(lib.LayoutTagLib)
st = namespace("jelly:stapler")

def text = new File(my.dir(), my.getHTMLTarget().getWrapperName()).text

def actual = Util.toHexString(MessageDigest.getInstance("SHA-1").digest(text.getBytes("UTF-8")))

def expected = null

def serveWrapper() {
    // don't actually serve the wrapper file, but use it as data source for the tab links only
    // this minimized the potential for mischief in the case of legacy archives without checksum
    st.contentType(value: "text/html;charset=UTF-8")

    def header = HtmlPublisher.class.getResourceAsStream("/htmlpublisher/HtmlPublisher/header.html").text
    def footer = HtmlPublisher.class.getResourceAsStream("/htmlpublisher/HtmlPublisher/footer.html").text

    raw(header)

    def legacyFile = new File(my.dir(), "htmlpublisher-wrapper.html")
    def matcher = legacyFile.text =~ /<li id="tab\d+" class="unselected" onclick="updateBody\('tab\d+'\);" value="([^"]+)">([^<]+)<\/li>/

    def items = []
    def itemsTitle = []
    while (matcher.find()) {
        items.add(matcher.group(1))
        itemsTitle.add(matcher.group(2))
    }

    def idx = 1
    items.each { file ->
        li(itemsTitle[idx-1], id: "tab${idx}", class: "unselected", onclick: "updateBody('tab${idx}')", value: file.trim())
        idx++
    }

    // TODO replace unnecessary JS usage by properly integrating header.html/footer.html in this groovy view
    raw("<script type=\"text/javascript\">document.getElementById(\"hudson_link\").innerHTML=\"Back to ${my.project.displayName}\";</script>")
    raw("<script type=\"text/javascript\">document.getElementById(\"hudson_link\").href=\"${rootURL}/${my.project.url}\";</script>")
    raw("<script type=\"text/javascript\">document.getElementById(\"zip_link\").href=\"*zip*/${my.getHTMLTarget().sanitizedName}.zip\";</script>")

    raw(footer)
}

if (my instanceof HtmlPublisherTarget.HTMLBuildAction) {
    // this is a build action, so needs to have its checksum checked
    expected = my.wrapperChecksum
} else if (my instanceof HtmlPublisherTarget.HTMLAction && my.actualBuildAction) {
    // this is a project action serving a build-level report
    expected = my.actualBuildAction.wrapperChecksum
} // else this is a project action serving a project-level report, which is considered safe

if (expected == null) {
    // no checksum expected
    serveWrapper()
} else {
    if (expected == actual) {
        // checksum expected and matches
        serveWrapper()
    } else {
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
