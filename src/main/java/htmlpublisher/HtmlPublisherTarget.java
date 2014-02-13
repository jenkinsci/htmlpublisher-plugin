package htmlpublisher;

import hudson.FilePath;
import hudson.model.AbstractBuild;
import hudson.model.AbstractItem;
import hudson.model.AbstractProject;
import hudson.model.AbstractDescribableImpl;
import hudson.model.Action;
import hudson.model.DirectoryBrowserSupport;
import hudson.model.ProminentProjectAction;
import hudson.model.Run;
import hudson.model.Descriptor;
import hudson.Extension;


import java.io.File;
import java.io.IOException;

import javax.servlet.ServletException;

import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerResponse;
import org.kohsuke.stapler.DataBoundConstructor;

/**
 * A representation of an HTML directory to archive and publish.
 *
 * @author Mike Rooney
 *
 */
public class HtmlPublisherTarget extends AbstractDescribableImpl<HtmlPublisherTarget> {
    /**
     * The name of the report to display for the build/project, such as "Code Coverage"
     */
    private final String name;

    /**
     * The path to the HTML report directory relative to the workspace.
     */
    private final String url;

    /**
     * The file[s] to provide links inside the report directory.
     */
    private final String apiKey;

    /**
     * If true, archive reports for all successful builds, otherwise only the most recent.
     */
    private final String json;

    /**
     * The name of the file which will be used as the wrapper index.
     */
    private final String wrapperName = "htmlpublisher-wrapper.html";

    @DataBoundConstructor
    public HtmlPublisherTarget(String name, String url, String apiKey, String json) {
        this.name = name;
        this.url = url;
        this.apiKey = apiKey;
        this.json = json;
    }

    public String getName() {
        return name;
    }

    public String getUrl() {
        return url;
    }

    public String getApiKey() {
        return apiKey;
    }

    public String getJson() {
        return json;
    }

    public String getSanitizedName() {
        String safeName = this.name;
        safeName = safeName.replace(" ", "_");
        return safeName;
    }

    public String getWrapperName() {
        return this.wrapperName;
    }

    protected abstract class BaseHTMLAction implements Action {
        private HtmlPublisherTarget actualHtmlPublisherTarget;

        public BaseHTMLAction(HtmlPublisherTarget actualHtmlPublisherTarget) {
            this.actualHtmlPublisherTarget = actualHtmlPublisherTarget;
        }

        public String getUrlName() {
            return actualHtmlPublisherTarget.getSanitizedName();
        }

        public String getDisplayName() {
            String action = actualHtmlPublisherTarget.name;
            return action;
        }

        public String getIconFileName() {
            return "graph.gif";
        }

        protected abstract String getTitle();
    }

    public class HTMLAction extends BaseHTMLAction implements ProminentProjectAction {
        private final AbstractItem project;

        public HTMLAction(AbstractItem project, HtmlPublisherTarget actualHtmlPublisherTarget) {
            super(actualHtmlPublisherTarget);
            this.project = project;
        }

        @Override
        protected String getTitle() {
            return this.project.getDisplayName() + " html2";
        }
    }

    public class HTMLBuildAction extends BaseHTMLAction {
        private final AbstractBuild<?, ?> build;

        public HTMLBuildAction(AbstractBuild<?, ?> build, HtmlPublisherTarget actualHtmlPublisherTarget) {
            super(actualHtmlPublisherTarget);
            this.build = build;
        }
        
        public final AbstractBuild<?,?> getOwner() {
        	return build;
        }

        @Override
        protected String getTitle() {
            return this.build.getDisplayName() + " html3";
        }
    }

    public void handleAction(AbstractBuild<?, ?> build) {

    }

    public Action getProjectAction(AbstractProject project) {
        return new HTMLAction(project, this);
    }

    @Extension
    public static class DescriptorImpl extends Descriptor<HtmlPublisherTarget> {
        public String getDisplayName() { return ""; }
    }
}