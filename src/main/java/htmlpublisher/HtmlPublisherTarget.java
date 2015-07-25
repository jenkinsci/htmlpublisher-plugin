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
    private final String reportName;

    /**
     * The path to the HTML report directory relative to the workspace.
     */
    private final String reportDir;

    /**
     * The file[s] to provide links inside the report directory.
     */
    private final String reportFiles;

    /**
     * If this is true and keepAll is true, publish the link on project level even if build failed.
     */
    private final boolean alwaysLinkToLastBuild;

    /**
     * If true, archive reports for all successful builds, otherwise only the most recent.
     */
    private final boolean keepAll;

    /**
     * If true, will allow report to be missing and build will not fail on missing report.
     */
    private final boolean allowMissing;

    /**
     * The name of the file which will be used as the wrapper index.
     */
    private static final String WRAPPER_NAME = "htmlpublisher-wrapper.html";

    @DataBoundConstructor
    public HtmlPublisherTarget(String reportName, String reportDir, String reportFiles, boolean keepAll, boolean alwaysLinkToLastBuild, boolean allowMissing) {
        this.reportName = reportName;
        this.reportDir = reportDir;
        this.reportFiles = reportFiles;
        this.keepAll = keepAll;
        this.alwaysLinkToLastBuild = alwaysLinkToLastBuild;
        this.allowMissing = allowMissing;
    }

    public String getReportName() {
        return this.reportName;
    }

    public String getReportDir() {
        return this.reportDir;
    }

    public String getReportFiles() {
        return this.reportFiles;
    }

    public boolean getAlwaysLinkToLastBuild() {
        return this.alwaysLinkToLastBuild;
    }

    public boolean getKeepAll() {
        return this.keepAll;
    }

    public boolean getAllowMissing() {
           return this.allowMissing;
    }

    public String getSanitizedName() {
        String safeName = this.reportName;
        safeName = safeName.replace(" ", "_");
        return safeName;
    }

    public String getWrapperName() {
        return WRAPPER_NAME;
    }

    public FilePath getArchiveTarget(AbstractBuild build) {
        return new FilePath(this.keepAll ? getBuildArchiveDir(build) : getProjectArchiveDir(build.getProject()));
    }

    /**
     * Gets the directory where the HTML report is stored for the given project.
     */
    private File getProjectArchiveDir(AbstractItem project) {
        return new File(new File(project.getRootDir(), "htmlreports"), this.getSanitizedName());
    }
    /**
     * Gets the directory where the HTML report is stored for the given build.
     */
    private File getBuildArchiveDir(Run run) {
        return new File(new File(run.getRootDir(), "htmlreports"), this.getSanitizedName());
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
            String action = actualHtmlPublisherTarget.reportName;
            return dir().exists() ? action : null;
        }

        public String getIconFileName() {
            return dir().exists() ? "graph.gif" : null;
        }

        public boolean shouldLinkToLastBuild() {
            return actualHtmlPublisherTarget.getAlwaysLinkToLastBuild();
        }

        /**
         * Serves HTML reports.
         */
        public void doDynamic(StaplerRequest req, StaplerResponse rsp) throws IOException, ServletException {
            DirectoryBrowserSupport dbs = new DirectoryBrowserSupport(this, new FilePath(this.dir()), this.getTitle(), "graph.gif", false);
            dbs.setIndexFileName(HtmlPublisherTarget.WRAPPER_NAME); // Hudson >= 1.312
            dbs.generateResponse(req, rsp, this);
        }

        protected abstract String getTitle();

        protected abstract File dir();
    }

    public class HTMLAction extends BaseHTMLAction implements ProminentProjectAction {
        private final AbstractItem project;

        public HTMLAction(AbstractItem project, HtmlPublisherTarget actualHtmlPublisherTarget) {
            super(actualHtmlPublisherTarget);
            this.project = project;
        }

        @Override
        protected File dir() {
            if (this.project instanceof AbstractProject) {
                AbstractProject abstractProject = (AbstractProject) this.project;

                Run run = getArchiveBuild(abstractProject);

                if (run != null) {
                    File javadocDir = getBuildArchiveDir(run);

                    if (javadocDir.exists()) {
                        return javadocDir;
                    }
                }
            }

            return getProjectArchiveDir(this.project);
        }

        private Run getArchiveBuild(AbstractProject abstractProject) {
            if (shouldLinkToLastBuild()) {
                return abstractProject.getLastBuild();
            } else {
                return abstractProject.getLastSuccessfulBuild();
            }
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

        @Override
        protected File dir() {
            return getBuildArchiveDir(this.build);
        }
    }

    public void handleAction(AbstractBuild<?, ?> build) {
        // Add build action, if coverage is recorded for each build
        if (this.keepAll) {
            build.addAction(new HTMLBuildAction(build, this));
        }
    }

    public Action getProjectAction(AbstractProject project) {
        return new HTMLAction(project, this);
    }

    @Extension
    public static class DescriptorImpl extends Descriptor<HtmlPublisherTarget> {
        public String getDisplayName() { return ""; }
    }
}