/*
 * The MIT License
 * 
 * Copyright (c) 2004-2009, Sun Microsystems, Inc., Kohsuke Kawaguchi, Martin Eigenbrodt, Peter Hayes
 * 
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 * 
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 * 
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package htmlpublisher;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintStream;
import java.io.Reader;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;
import org.kohsuke.stapler.AncestorInPath;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;

import hudson.EnvVars;
import hudson.Extension;
import hudson.FilePath;
import hudson.Launcher;
import hudson.Util;
import hudson.matrix.MatrixConfiguration;
import hudson.matrix.MatrixProject;
import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.model.Action;
import hudson.model.BuildListener;
import hudson.model.Job;
import hudson.model.Result;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.BuildStepMonitor;
import hudson.tasks.Publisher;
import hudson.tasks.Recorder;
import hudson.util.FormValidation;

import org.apache.tools.ant.types.FileSet;
import jenkins.model.Jenkins;

import javax.annotation.Nonnull;


/**
 * Saves HTML reports for the project and publishes them.
 *
 * @author Kohsuke Kawaguchi
 * @author Mike Rooney
 */
public class HtmlPublisher extends Recorder {
    private final List<HtmlPublisherTarget> reportTargets;

    private static final String HEADER = "/htmlpublisher/HtmlPublisher/header.html";
    private static final String FOOTER = "/htmlpublisher/HtmlPublisher/footer.html";
    @DataBoundConstructor
    @Restricted(NoExternalUse.class)
    public HtmlPublisher(List<HtmlPublisherTarget> reportTargets) {
        this.reportTargets = reportTargets != null ? new ArrayList<>(reportTargets) : new ArrayList<>();
    }

    public List<HtmlPublisherTarget> getReportTargets() {
        return this.reportTargets;
    }

    /**
     * @param lines List of String
     * @param path File outputFile
     * @return SHA checksum of the written file
     */
    private static String writeFile(List<String> lines, File path) throws IOException, NoSuchAlgorithmException {
        MessageDigest sha1 = MessageDigest.getInstance("SHA-1");
        //TODO: consider using UTF-8
        try (FileOutputStream fos = new FileOutputStream(path);
                OutputStreamWriter osw = new OutputStreamWriter(fos, Charset.defaultCharset());
                BufferedWriter bw = new BufferedWriter(osw)) {
            for (int i = 0; i < lines.size(); i++) {
                String line = lines.get(i) + "\n";
                bw.write(line);
                sha1.update(line.getBytes(StandardCharsets.UTF_8));
            }
        }

        return Util.toHexString(sha1.digest());
    }

    public List<String> readFile(String filePath) throws 
            java.io.IOException {
        return readFile(filePath, this.getClass());
    }

    @SuppressFBWarnings(value = "RCN_REDUNDANT_NULLCHECK_WOULD_HAVE_BEEN_A_NPE", justification = "Apparent false positive on JDK11 on try block")
    public static List<String> readFile(String filePath, Class<?> publisherClass)
            throws java.io.IOException {
        List<String> aList = new ArrayList<>();
        try (final InputStream is = publisherClass.getResourceAsStream(filePath);
                final Reader r = new InputStreamReader(is, Charset.defaultCharset());
                final BufferedReader br = new BufferedReader(r)){
            // We expect that files have been generated with the default system's charset
            String line;
            while ((line = br.readLine()) != null) {
                aList.add(line);
            }
        }
        return aList;
    }

    protected static String resolveParametersInString(Run<?, ?> build, TaskListener listener, String input) {
        PrintStream logger = listener.getLogger();
        if (build instanceof AbstractBuild) {
            try {
                return build.getEnvironment(listener).expand(input);
            } catch (Exception e) {
                logger.println("Failed to resolve parameters in string \"" +
                        input + "\" due to following error:\n" + e.getMessage());
            }
        } else {
            if (input.matches("\\$\\{.*\\}")) {
                logger.println("***************");
                logger.println("*** WARNING ***");
                logger.println("***************");
                logger.print("You appear to be relying on the HTML Publisher plugin to resolve variables in a Pipeline build. ");
                logger.print("This is not considered best practice and will be removed in a future release. ");
                logger.println("Please use a Groovy mechanism to evaluate the string.");
            }
            try {
                return build.getEnvironment(listener).expand(input);
            } catch (Exception e) {
                logger.println("Failed to resolve parameters in string \"" +
                        input + "\" due to following error:\n" + e.getMessage());
            }
        }

        // If not an AbstractBuild or we don't have an expanded value, just return the input as is
        return input;
    }

    protected static String resolveParametersInString(EnvVars envVars, TaskListener listener, String input) {
        try {
            return envVars.expand(input);
        } catch (Exception e) {
            listener.getLogger().println("Failed to resolve parameters in string \""+
            input+"\" due to following error:\n"+e.getMessage());
        }
        return input;
    }

    @Override
    public boolean perform(AbstractBuild<?, ?> build, Launcher launcher, BuildListener listener)
            throws InterruptedException {
        return publishReports(build, build.getWorkspace(), listener, reportTargets, this.getClass());
    }

    /**
     * Runs HTML the publishing operation for specified {@link HtmlPublisherTarget}s.
     * @return False if the operation failed
     */
    public static boolean publishReports(Run<?, ?> build, FilePath workspace, TaskListener listener,
            List<HtmlPublisherTarget> reportTargets, Class<?> publisherClass) throws InterruptedException {
        PrintStream logger = listener.getLogger();
        logger.println("[htmlpublisher] Archiving HTML reports...");

        // Grab the contents of the header and footer as arrays
        List<String> headerLines;
        try {
            headerLines = readFile(HEADER, publisherClass);
        } catch (IOException ex) {
            logger.print("Exception occurred reading file "+HEADER+", message:"+ex.getMessage());
            return false;
        }
        List<String> footerLines;
        try {
            footerLines = readFile(FOOTER, publisherClass);
        } catch (IOException ex) {
            logger.print("Exception occurred reading file "+FOOTER+", message:"+ex.getMessage());
            return false;
        }


        for (int i=0; i < reportTargets.size(); i++) {
            // Create an array of lines we will eventually write out, initially the header.
            List<String> reportLines = new ArrayList<>(headerLines);
            HtmlPublisherTarget reportTarget = reportTargets.get(i);
            boolean keepAll = reportTarget.getKeepAll();
            boolean allowMissing = reportTarget.getAllowMissing();

            FilePath archiveDir = workspace.child(resolveParametersInString(build, listener, reportTarget.getReportDir()));
            FilePath targetDir = reportTarget.getArchiveTarget(build);

            String levelString = keepAll ? "BUILD" : "PROJECT";
            logger.println("[htmlpublisher] Archiving at " + levelString + " level " + archiveDir + " to " + targetDir);

            try {
                if (!archiveDir.exists()) {
                    listener.error("Specified HTML directory '" + archiveDir + "' does not exist.");
                    if (!allowMissing) {
                        build.setResult(Result.FAILURE);
                        return true;
                    }
                }

                if (!keepAll) {
                    // We are only keeping one copy at the project level, so remove the old one.
                    targetDir.deleteRecursive();
                }

                if (archiveDir.copyRecursiveTo(reportTarget.getIncludes(), targetDir) == 0) {
                    if (!allowMissing) {
                        listener.error("Directory '" + archiveDir + "' exists but failed copying to '" + targetDir + "'.");
                        final Result buildResult = build.getResult();
                        if (buildResult != null && buildResult.isBetterOrEqualTo(Result.UNSTABLE)) {
                            listener.error("This is especially strange since your build otherwise succeeded.");
                        }
                        build.setResult(Result.FAILURE);
                        return true;
                    } else {
                        continue;
                    }
                }
            } catch (IOException e) {
                Util.displayIOException(e, listener);
                e.printStackTrace(listener.fatalError("HTML Publisher failure"));
                build.setResult(Result.FAILURE);
                return true;
            }

            // Index files might be a list of ant patterns, e.g. "**/*index.html,**/*otherFile.html"
            // So split them and search for files within the archive directory that match that pattern
            List<String> csvReports = new ArrayList<>();
            File targetDirFile = new File(targetDir.getRemote());
            String[] splitPatterns = resolveParametersInString(build, listener, reportTarget.getReportFiles()).split(",");
            for (String pattern : splitPatterns) {
                FileSet fs = Util.createFileSet(targetDirFile, pattern);
                csvReports.addAll(Arrays.asList(fs.getDirectoryScanner().getIncludedFiles()));
            }

            String[] titles = null;
            if (reportTarget.getReportTitles() != null && reportTarget.getReportTitles().trim().length() > 0 ) {
                titles = reportTarget.getReportTitles().trim().split("\\s*,\\s*");
                for (int j = 0; j < titles.length; j++) {
                    titles[j] = resolveParametersInString(build, listener, titles[j]);
                }
            }

            List<String> reports = new ArrayList<>();
            for (int j=0; j < csvReports.size(); j++) {
                String report = csvReports.get(j);
                report = report.trim();
                // On windows file paths contains back slashes, but
                // in the HTML file we do not want them, so replace them with forward slash
                report = report.replace("\\", "/");
	
                // Ignore blank report names caused by trailing or double commas.
                if (report.isEmpty()) {
                    continue;
                }

                reports.add(report);
                String tabNo = "tab" + (j + 1);
                // Make the report name the filename without the extension.
                int end = report.lastIndexOf('.');
                String reportFile;
                if (end > 0) {
                    reportFile = report.substring(0, end);
                } else {
                    reportFile = report;
                }
                String tabItem = "<li id=\"" + tabNo + "\" class=\"unselected\" onclick=\"updateBody('" + tabNo + "');\" value=\"" + report + "\">" + getTitle(reportFile, titles, j) + "</li>";
                reportLines.add(tabItem);
            }
            // Add the JS to change the link as appropriate.
            String hudsonUrl = Jenkins.get().getRootUrl();
            Job job = build.getParent();
            reportLines.add("<script type=\"text/javascript\">document.getElementById(\"hudson_link\").innerHTML=\"Back to " + job.getName() + "\";</script>");
            // If the URL isn't configured in Hudson, the best we can do is attempt to go Back.
            if (hudsonUrl == null) {
                reportLines.add("<script type=\"text/javascript\">document.getElementById(\"hudson_link\").onclick = function() { history.go(-1); return false; };</script>");
            } else {
                String jobUrl = hudsonUrl + job.getUrl();
                reportLines.add("<script type=\"text/javascript\">document.getElementById(\"hudson_link\").href=\"" + jobUrl + "\";</script>");
            }

            reportLines.add("<script type=\"text/javascript\">document.getElementById(\"zip_link\").href=\"*zip*/" + reportTarget.getSanitizedName() + ".zip\";</script>");

            // Now add the footer.
            reportLines.addAll(footerLines);
            // And write this as the index
            File outputFile = new File(targetDir.getRemote(), reportTarget.getWrapperName());
            try {
                if(archiveDir.exists()) {

                    // check if we should add a link to build for this report, based on the existence of other reports with the same name
                    boolean alreadyPublished = false;
                    String reportName = reportTarget.getReportName();
                    String actionName = null;
                    for (Action action: build.getAllActions()) {
                        if (action == null) {
                            continue;
                        }
                        actionName = action.getDisplayName();
                        if (actionName != null && actionName.equals(reportName)) {
                            alreadyPublished = true;
                        }
                    }

                    if (!reportTarget.getOnlyCreateReportWithDifferentName() || !alreadyPublished) {
                        String checksum = writeFile(reportLines, outputFile);
                        reportTarget.handleAction(build, checksum);
                    } else {
                        logger.println(String.format("[htmlpublisher] Warn: A report with the same name [%s] already exists", reportName));
                    }
                }
            } catch (IOException e) {
                logger.println("Error: IOException occured writing report to file "+outputFile.getAbsolutePath()+" to archiveDir:"+archiveDir.getName()+", error:"+e.getMessage());
            } catch (NoSuchAlgorithmException e) {
                // cannot happen because SHA-1 is guaranteed to exist
                logger.println("Error: NoSuchAlgorithmException occured writing report to file "+outputFile.getAbsolutePath()+" to archiveDir:"+archiveDir.getName()+", error:"+e.getMessage());
            }
        }
        return true;
    }

    private static String getTitle(String report, String[] titles, int j) {
        if (titles != null && titles.length > j) {
            return titles[j];
        }
        return report;
    }

    @Override
    @Nonnull
    public Collection<? extends Action> getProjectActions(AbstractProject<?, ?> project) {
        if (this.reportTargets.isEmpty()) {
            return Collections.emptyList();
        } else {
            List<Action> actions = new ArrayList<>();
            for (HtmlPublisherTarget target : this.reportTargets) {
                actions.add(target.getProjectAction(project));
                if (project instanceof MatrixProject && ((MatrixProject) project).getActiveConfigurations() != null){
                    for (MatrixConfiguration mc : ((MatrixProject) project).getActiveConfigurations()){
                        try {
                          mc.onLoad(mc.getParent(), mc.getName());
                        }
                        catch (IOException e){
                            //Could not reload the configuration.
                        }
                    }
                }
            }
            return actions;
        }
    }

    @Extension
    public static class DescriptorImpl extends BuildStepDescriptor<Publisher> {
        @Override
        @Nonnull
        public String getDisplayName() {
            return "Publish HTML reports";
        }

        /**
         * Performs on-the-fly validation on the file mask wildcard.
         */
        public FormValidation doCheck(@AncestorInPath AbstractProject project,
                @QueryParameter String value) throws IOException {
            FilePath ws = project.getSomeWorkspace();
            return ws != null ? ws.validateRelativeDirectory(value) : FormValidation.ok();
        }

        @Override
        public boolean isApplicable(Class<? extends AbstractProject> jobType) {
            return true;
        }
    }

    @Override
    public BuildStepMonitor getRequiredMonitorService() {
        return BuildStepMonitor.NONE;
    }
}
