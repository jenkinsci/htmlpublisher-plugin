package htmlpublisher;

import com.infradna.tool.bridge_method_injector.WithBridgeMethods;
import hudson.FilePath;
import hudson.model.AbstractItem;
import hudson.model.AbstractDescribableImpl;
import hudson.model.Action;
import hudson.model.DirectoryBrowserSupport;
import hudson.model.ProminentProjectAction;
import hudson.model.Run;
import hudson.model.Descriptor;
import hudson.Extension;
import hudson.model.AbstractBuild;
import hudson.model.InvisibleAction;
import hudson.model.Job;


import java.io.File;
import java.io.IOException;
import javax.annotation.Nonnull;

import javax.servlet.ServletException;

import hudson.util.HttpResponses;
import jenkins.model.RunAction2;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;

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

    private String reportTitles;
    /**
     * If true, archive reports for all successful builds, otherwise only the most recent.
     */
    private final boolean keepAll;

    /**
     * If true, will allow report to be missing and build will not fail on missing report.
     */
    private final boolean allowMissing;

    /**
     * Do not use, but keep to maintain compatibility with older releases. See JENKINS-31366.
     */
    @Deprecated
    private transient String wrapperName;

    /**
     * The name of the file which will be used as the wrapper index.
     */
    private static final String WRAPPER_NAME = "htmlpublisher-wrapper.html";

    /**
     * @deprecated Use {@link #HtmlPublisherTarget(java.lang.String, java.lang.String, java.lang.String, java.lang.String, boolean, boolean, boolean)}.
     */
    @Deprecated
    public HtmlPublisherTarget(String reportName, String reportDir, String reportFiles, boolean keepAll, boolean allowMissing) {
        this(reportName, reportDir, reportFiles, "",keepAll, false, allowMissing);
    }

    public String getReportTitles() {
        return reportTitles;
    }

    /**
     * Constructor.
     * @param reportName Report name
     * @param reportDir Source directory in the job workspace
     * @param reportFiles Files to be published
     * @param reportTitles Files Title to be published
     * @param keepAll True if the report should be stored for all builds
     * @param alwaysLinkToLastBuild If true, the job action will refer the latest build.
     *      Otherwise, the latest successful one will be referenced
     * @param allowMissing If true, blocks the build failure if the report is missing
     * @since 1.4

     */
    @DataBoundConstructor
    public HtmlPublisherTarget(String reportName, String reportDir, String reportFiles,String reportTitles, boolean keepAll, boolean alwaysLinkToLastBuild, boolean allowMissing) {
        this.reportName = reportName;
        this.reportDir = reportDir;
        this.reportFiles = reportFiles;
        this.reportTitles = reportTitles;
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
    
    public FilePath getArchiveTarget(Run build) {
        return new FilePath(this.keepAll ? getBuildArchiveDir(build) : getProjectArchiveDir(build.getParent()));
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

        protected transient AbstractItem project;

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
            if (req.getRestOfPath().equals("")) {
                throw HttpResponses.forwardToView(this, "index.jelly");
            }
            dbs.generateResponse(req, rsp, this);
        }

        protected abstract String getTitle();

        protected abstract File dir();
    }

    public class HTMLAction extends BaseHTMLAction implements ProminentProjectAction {

        private transient HTMLBuildAction actualBuildAction;

        public HTMLAction(AbstractItem project, HtmlPublisherTarget actualHtmlPublisherTarget) {
            super(actualHtmlPublisherTarget);
            this.project = project;
        }

        @Override
        protected File dir() {
            if (this.project instanceof Job) {
                final Job job = (Job) this.project;

                Run run = getArchiveBuild(job);

                if (run != null) {
                    File javadocDir = getBuildArchiveDir(run);

                    if (javadocDir.exists()) {
                        for (HTMLBuildAction a : run.getActions(HTMLBuildAction.class)) {
                            if (a.getHTMLTarget().getReportName().equals(getHTMLTarget().getReportName())) {
                                actualBuildAction = a;
                            }
                        }
                        return javadocDir;
                    }
                }
            }

            return getProjectArchiveDir(this.project);
        }

        private Run getArchiveBuild(@Nonnull Job job) {
            if (shouldLinkToLastBuild()) {
                return job.getLastBuild();
            } else {
                return job.getLastSuccessfulBuild();
            }
        }

        @Override
        protected String getTitle() {
            return this.project.getDisplayName() + " html2";
        }
        
        /**
         * Gets {@link HtmlPublisherTarget}, for which the action has been created.
         * @return HTML Report description
         * @since TODO
         */
        public @Nonnull HtmlPublisherTarget getHTMLTarget() {
            return HtmlPublisherTarget.this;
        }

        @Restricted(NoExternalUse.class) // read by Groovy view
        public HTMLBuildAction getActualBuildAction() {
            return actualBuildAction;
        }

    }

    /**
     * Hidden action, which indicates the build has been published on the project level.
     * This action is not an instance of {@link BaseHTMLAction} , because we want to
     * avoid confusions with actions referring to the data.
     * @since TODO
     */
    public static class HTMLPublishedForProjectMarkerAction extends InvisibleAction implements RunAction2 {
        private transient Run<?, ?> build;
        private final HtmlPublisherTarget actualHtmlPublisherTarget;

        public HTMLPublishedForProjectMarkerAction(Run<?, ?> build, HtmlPublisherTarget actualHtmlPublisherTarget) {
            this.actualHtmlPublisherTarget = actualHtmlPublisherTarget;
            this.build = build;
        }
        
        @WithBridgeMethods(value = AbstractBuild.class, adapterMethod = "getAbstractBuildOwner")
        public final Run<?,?> getOwner() {
            return build;
        }
        
        @Deprecated
        private final Object getAbstractBuildOwner(Run build, Class targetClass) {
            return build instanceof AbstractBuild ? (AbstractBuild) build : null;
        }

        @Override
        public void onAttached(Run<?, ?> r) {
            this.build = r;
        }

        @Override
        public void onLoad(Run<?, ?> r) {
            this.build = r;
        }

        public HtmlPublisherTarget getHTMLTarget() {
            return actualHtmlPublisherTarget;
        }      
    }
    
    public class HTMLBuildAction extends BaseHTMLAction implements RunAction2 {
        private transient Run<?, ?> build;

        private String wrapperChecksum;

        public HTMLBuildAction(Run<?, ?> build, HtmlPublisherTarget actualHtmlPublisherTarget) {
            super(actualHtmlPublisherTarget);
            this.build = build;
        }
        
        @WithBridgeMethods(value = AbstractBuild.class, castRequired = true)
        public final Run<?,?> getOwner() {
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
        
        /**
         * Gets {@link HtmlPublisherTarget}, for which the action has been created.
         * @return HTML Report description
         * @since TODO
         */
        public @Nonnull HtmlPublisherTarget getHTMLTarget() {
            return HtmlPublisherTarget.this;
        }

        @Override
        public void onAttached(Run<?, ?> r) {
            build = r;
            this.project = r.getParent();
        }

        @Override
        public void onLoad(Run<?, ?> r) {
            build = r;
            this.project = r.getParent();
        }

        public String getWrapperChecksum() {
            return wrapperChecksum;
        }

        private void setWrapperChecksum(String wrapperChecksum) {
            this.wrapperChecksum = wrapperChecksum;
        }

    }

    @Deprecated
    public void handleAction(Run<?, ?> build) {
        handleAction(build, null);
    }

    /* package */ void handleAction(Run<?, ?> build, String checksum) {
        // Add build action, if coverage is recorded for each build
        if (this.keepAll) {
            HTMLBuildAction a = new HTMLBuildAction(build, this);
            a.setWrapperChecksum(checksum);
            build.addAction(a);
        } else { // Othwewise we add a hidden marker
            build.addAction(new HTMLPublishedForProjectMarkerAction(build, this));
        }
    }

    public Action getProjectAction(AbstractItem item) {
        return new HTMLAction(item, this);
    }
    
    @Override
    public int hashCode() {
        int hash = 5;
        hash = 97 * hash + (this.reportName != null ? this.reportName.hashCode() : 0);
        hash = 97 * hash + (this.reportDir != null ? this.reportDir.hashCode() : 0);
        hash = 97 * hash + (this.reportFiles != null ? this.reportFiles.hashCode() : 0);
        hash = 97 * hash + (this.alwaysLinkToLastBuild ? 1 : 0);
        hash = 97 * hash + (this.keepAll ? 1 : 0);
        hash = 97 * hash + (this.allowMissing ? 1 : 0);
        return hash;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == null) {
            return false;
        }
        if (getClass() != obj.getClass()) {
            return false;
        }
        final HtmlPublisherTarget other = (HtmlPublisherTarget) obj;
        if ((this.reportName == null) ? (other.reportName != null) : !this.reportName.equals(other.reportName)) {
            return false;
        }
        if ((this.reportDir == null) ? (other.reportDir != null) : !this.reportDir.equals(other.reportDir)) {
            return false;
        }
        if ((this.reportFiles == null) ? (other.reportFiles != null) : !this.reportFiles.equals(other.reportFiles)) {
            return false;
        }
        if (this.alwaysLinkToLastBuild != other.alwaysLinkToLastBuild) {
            return false;
        }
        if (this.keepAll != other.keepAll) {
            return false;
        }
        if (this.allowMissing != other.allowMissing) {
            return false;
        }
        return true;
    }

    @Extension
    public static class DescriptorImpl extends Descriptor<HtmlPublisherTarget> {
        public String getDisplayName() { return ""; }
    }
}