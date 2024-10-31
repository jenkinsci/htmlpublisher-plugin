package htmlpublisher.HtmlPublisherTarget.BaseHTMLAction

import htmlpublisher.HtmlPublisher
import htmlpublisher.HtmlPublisherTarget
import hudson.Functions
import hudson.Util
import hudson.model.Descriptor
import jenkins.model.Jenkins

import java.security.MessageDigest

l = namespace(lib.LayoutTagLib)
st = namespace("jelly:stapler")

def text = new File(my.dir(), my.getHTMLTarget().getWrapperName()).text

def actual = Util.toHexString(MessageDigest.getInstance("SHA-1").digest(text.getBytes("UTF-8")))

def expected = null
def useWrapperFileDirectly = null;

def serveWrapper() {
    // don't actually serve the wrapper file, but use it as data source for the tab links only
    // this minimized the potential for mischief in the case of legacy archives without checksum
    st.contentType(value: "text/html;charset=UTF-8")

    def header = HtmlPublisher.class.getResourceAsStream("/htmlpublisher/HtmlPublisher/header.html").text
    def footer = HtmlPublisher.class.getResourceAsStream("/htmlpublisher/HtmlPublisher/footer.html").text

    raw(header)
    script(src: "${Jenkins.get().getRootUrl() + Functions.getResourcePath()}/plugin/htmlpublisher/js/htmlpublisher.js", type: "text/javascript")

    def legacyFile = new File(my.dir(), "htmlpublisher-wrapper.html")
    def matcher = legacyFile.text =~ /<li id="tab\d+" class="unselected"(?: onclick="updateBody\('tab\d+'\);")? value="([^"]+)">([^<]+)<\/li>/

    def items = []
    def itemsTitle = []
    while (matcher.find()) {
        items.add(matcher.group(1))
        itemsTitle.add(matcher.group(2))
    }

    def idx = 1
    items.each { file ->
        li(itemsTitle[idx - 1], id: "tab${idx}", class: "unselected", value: file.trim())
        idx++
    }

    span(class: "links-data-holder", 
            "data-back-to-name": "${my.backToName}",
            "data-root-url": "${rootURL}",
            "data-job-url": "${my.backToUrl}",
            "data-zip-link": "${my.getHTMLTarget().sanitizedName}")

    raw(footer)
}

def serveWrapperLegacyDirectly() {
    // don't actually serve the wrapper file, but use it as data source for the tab links only
    // this minimized the potential for mischief in the case of legacy archives without checksum
    st.contentType(value: "text/html;charset=UTF-8")

    def legacyFile = new File(my.dir(), "htmlpublisher-wrapper.html")

    def scriptPattern = legacyFile.text =~ /(<script type="text\/javascript">document.getElementById\("hudson_link"\).innerHTML="Back to )(.*[<>"\\].*)(";<\/script>)/

    if (scriptPattern.find()) {
        throw new Descriptor.FormException("Can't use illegal character in the Job Name", "JobName")
    }

    def tabPattern = legacyFile.text =~ /(<li id="tab\d+" class="unselected" onclick="updateBody\('tab\d+'\);" value=")(.*[<>"\\].*)(">)(.*[<>"\\].*)(<\/li>)/

    if (tabPattern.find()) {
        throw new Descriptor.FormException("Can't use illegal character in the Report Name", "ReportName")
    }

    def valuePattern = legacyFile.text =~ /(<li id="tab\d+" class="unselected" onclick="updateBody\('tab\d+'\);" value=")([^<]+)(">)(.*[<>"\\].*)(<\/li>)/

    if (valuePattern.find()) {
        throw new Descriptor.FormException("Can't use illegal character in the Report Name", "ReportName")
    }

    def titlePattern = legacyFile.text =~ /(<li id="tab\d+" class="unselected" onclick="updateBody\('tab\d+'\);" value=")(.*[<>"\\].*)(">)([^<]+)(<\/li>)/

    if (titlePattern.find()) {
        throw new Descriptor.FormException("Can't use illegal character in the Report Name", "ReportName")
    }

    raw(legacyFile.text)
}

if (my instanceof HtmlPublisherTarget.HTMLBuildAction) {
    // this is a build action, so needs to have its checksum checked
    expected = my.wrapperChecksum
    useWrapperFileDirectly = my.getHTMLTarget().useWrapperFileDirectly
} else if (my instanceof HtmlPublisherTarget.HTMLAction && my.actualBuildAction) {
    // this is a project action serving a build-level report
    expected = my.actualBuildAction.wrapperChecksum
    useWrapperFileDirectly = my.getHTMLTarget().useWrapperFileDirectly
} // else this is a project action serving a project-level report, which is considered safe

if (expected == null) {
    // no checksum expected
    serveWrapper()
} else {
    if (expected == actual) {
        // checksum expected and matches
        if (useWrapperFileDirectly) {
            serveWrapperLegacyDirectly()
        } else {
            serveWrapper()
        }
    } else {
        l.layout {
            l.header(title: "Checksum mismatch")
            l.main_panel {
                h1(_("Checksum mismatch")) {
                    l.icon(class: 'icon-error icon-xlg')
                }
                p(raw(_("msg", actual, expected)))
            }
        }
    }
}
