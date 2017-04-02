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

import hudson.EnvVars;
import hudson.Extension;
import hudson.FilePath;
import hudson.Launcher;
import hudson.Util;
import hudson.matrix.MatrixConfiguration;
import hudson.matrix.MatrixProject;
import hudson.model.*;
import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.BuildStepMonitor;
import hudson.tasks.Publisher;
import hudson.tasks.Recorder;
import hudson.util.FormValidation;
import org.kohsuke.stapler.AncestorInPath;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;

import javax.servlet.ServletException;
import java.io.*;
import java.nio.charset.Charset;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

import jenkins.model.Jenkins;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;

/**
 * Saves HTML reports for the project and publishes them.
 *
 * @author Kohsuke Kawaguchi
 * @author Mike Rooney
 */
public class HtmlPublisher extends Recorder {
    private final ArrayList<HtmlPublisherTarget> reportTargets;

    @DataBoundConstructor
    @Restricted(NoExternalUse.class)
    public HtmlPublisher(List<HtmlPublisherTarget> reportTargets) {
        this.reportTargets = reportTargets != null ? new ArrayList<HtmlPublisherTarget>(reportTargets) : new ArrayList<HtmlPublisherTarget>();
    }

    public ArrayList<HtmlPublisherTarget> getReportTargets() {
        return this.reportTargets;
    }

    /**
     *
     * @return SHA checksum of the written file
     */
    private static String writeFile(ArrayList<String> lines, File path) throws IOException, NoSuchAlgorithmException {
        MessageDigest sha1 = MessageDigest.getInstance("SHA-1");

        //TODO: consider using UTF-8
        BufferedWriter bw = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(path), Charset.defaultCharset()));
        try {
            for (int i = 0; i < lines.size(); i++) {
                String line = lines.get(i) + "\n";
                bw.write(line);
                sha1.update(line.getBytes("UTF-8"));
            }
        } finally {
            bw.close();
        }

        return Util.toHexString(sha1.digest());
    }

    public ArrayList<String> readFile(String filePath) throws java.io.FileNotFoundException,
            java.io.IOException {
        return readFile(filePath, this.getClass());
    }

    public static ArrayList<String> readFile(String filePath, Class<?> publisherClass)
            throws java.io.FileNotFoundException, java.io.IOException {
        ArrayList<String> aList = new ArrayList<String>();

        try {
            final InputStream is = publisherClass.getResourceAsStream(filePath);
            try {
                // We expect that files have been generated with the default system's charset
                final Reader r = new InputStreamReader(is, Charset.defaultCharset());
                try {
                    final BufferedReader br = new BufferedReader(r);
                    try {
                        String line = null;
                        while ((line = br.readLine()) != null) {
                            aList.add(line);
                        }
                        br.close();
                        r.close();
                        is.close();
                    } finally {
                        try {
                            br.close();
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                    }
                } finally {
                    try {
                        r.close();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            } finally {
                try {
                    is.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        } catch (IOException e) {
            // failure
            e.printStackTrace();
        }

        return aList;
    }

    protected static String resolveParametersInString(Run<?, ?> build, TaskListener listener, String input) {
        try {
            return build.getEnvironment(listener).expand(input);
        } catch (Exception e) {
            listener.getLogger().println("Failed to resolve parameters in string \""+
            input+"\" due to following error:\n"+e.getMessage());
        }
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
        return publishReports(build, build.getWorkspace(), launcher, listener, reportTargets, this.getClass());
    }

    /**
     * Runs HTML the publishing operation for specified {@link HtmlPublisherTarget}s.
     * @return False if the operation failed
     * @since TODO
     */
    public static boolean publishReports(Run<?, ?> build, FilePath workspace, Launcher launcher, TaskListener listener,
            List<HtmlPublisherTarget> reportTargets, Class<?> publisherClass) throws InterruptedException {
        listener.getLogger().println("[htmlpublisher] Archiving HTML reports...");

        // Grab the contents of the header and footer as arrays
        ArrayList<String> headerLines;
        ArrayList<String> footerLines;
        try {
            headerLines = readFile("/htmlpublisher/HtmlPublisher/header.html", publisherClass);
            footerLines = readFile("/htmlpublisher/HtmlPublisher/footer.html", publisherClass);
        } catch (FileNotFoundException e1) {
            e1.printStackTrace();
            return false;
        } catch (IOException e1) {
            e1.printStackTrace();
            return false;
        }

        for (int i=0; i < reportTargets.size(); i++) {
            // Create an array of lines we will eventually write out, initially the header.
            ArrayList<String> reportLines = new ArrayList<String>(headerLines);
            HtmlPublisherTarget reportTarget = reportTargets.get(i);
            boolean keepAll = reportTarget.getKeepAll();
            boolean allowMissing = reportTarget.getAllowMissing();

            FilePath archiveDir = workspace.child(resolveParametersInString(build, listener, reportTarget.getReportDir()));
            FilePath targetDir = reportTarget.getArchiveTarget(build);

            String levelString = keepAll ? "BUILD" : "PROJECT";
            listener.getLogger().println("[htmlpublisher] Archiving at " + levelString + " level " + archiveDir + " to " + targetDir);

            // The index name might be a comma separated list of names, so let's figure out all the pages we should index.
            String[] csvReports = resolveParametersInString(build, listener, reportTarget.getReportFiles()).split(",");

            String[] titles = null;
            if (reportTarget.getReportTitles() != null && reportTarget.getReportTitles().trim().length() > 0 ) {
                titles = reportTarget.getReportTitles().trim().split(",");
            }

            ArrayList<String> reports = new ArrayList<String>();
            for (int j=0; j < csvReports.length; j++) {
                String report = csvReports[j];
                report = report.trim();

                // Ignore blank report names caused by trailing or double commas.
                if (report.equals("")) {continue;}

                reports.add(report);
                String tabNo = "tab" + (j + 1);
                // Make the report name the filename without the extension.
                int end = report.lastIndexOf('.');
                String reportName;
                if (end > 0) {
                    reportName = report.substring(0, end);
                } else {
                    reportName = report;
                }
                String tabItem = "<li id=\"" + tabNo + "\" class=\"unselected\" onclick=\"updateBody('" + tabNo + "');\" value=\"" + report + "\">" + getTitle(reportName, titles, j) + "</li>";
                reportLines.add(tabItem);
            }
            // Add the JS to change the link as appropriate.
            String hudsonUrl = Jenkins.getActiveInstance().getRootUrl();
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

            try {
                if (!archiveDir.exists() && !allowMissing) {
                    listener.error("Specified HTML directory '" + archiveDir + "' does not exist.");
                    build.setResult(Result.FAILURE);
                    return true;
                } else if (!keepAll) {
                    // We are only keeping one copy at the project level, so remove the old one.
                    targetDir.deleteRecursive();
                }

                if (archiveDir.copyRecursiveTo("**/*", targetDir) == 0 && !allowMissing) {
                    listener.error("Directory '" + archiveDir + "' exists but failed copying to '" + targetDir + "'.");
                    final Result buildResult = build.getResult();
                    if (buildResult != null && buildResult.isBetterOrEqualTo(Result.UNSTABLE)) {
                        // If the build failed, don't complain that there was no coverage.
                        // The build probably didn't even get to the point where it produces coverage.
                        listener.error("This is especially strange since your build otherwise succeeded.");
                    }
                    build.setResult(Result.FAILURE);
                    return true;
                }
            } catch (IOException e) {
                Util.displayIOException(e, listener);
                e.printStackTrace(listener.fatalError("HTML Publisher failure"));
                build.setResult(Result.FAILURE);
                return true;
            }

            // Now add the footer.
            reportLines.addAll(footerLines);
            // And write this as the index
            try {
                if(archiveDir.exists())
                {
                    String checksum = writeFile(reportLines, new File(targetDir.getRemote(), reportTarget.getWrapperName()));
                    reportTarget.handleAction(build, checksum);
                }
            } catch (IOException e) {
                e.printStackTrace();
            } catch (NoSuchAlgorithmException e) {
                // cannot happen because SHA-1 is guaranteed to exist
                e.printStackTrace();
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
    public Collection<? extends Action> getProjectActions(AbstractProject<?, ?> project) {
        if (this.reportTargets.isEmpty()) {
            return Collections.emptyList();
        } else {
            ArrayList<Action> actions = new ArrayList<Action>();
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
        public String getDisplayName() {
            // return Messages.JavadocArchiver_DisplayName();
            return "Publish HTML reports";
        }

        /**
         * Performs on-the-fly validation on the file mask wildcard.
         */
        public FormValidation doCheck(@AncestorInPath AbstractProject project,
                @QueryParameter String value) throws IOException, ServletException {
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
