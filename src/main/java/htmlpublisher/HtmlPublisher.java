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

import hudson.Extension;
import hudson.FilePath;
import hudson.Launcher;
import hudson.Util;
import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.model.Action;
import hudson.model.BuildListener;
import hudson.model.Hudson;
import hudson.model.Result;
import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.BuildStepMonitor;
import hudson.tasks.Publisher;
import hudson.tasks.Recorder;
import hudson.util.FormValidation;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.util.ArrayList;
import java.util.List;

import javax.servlet.ServletException;

import org.kohsuke.stapler.AncestorInPath;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;

/**
 * Saves HTML reports for the project and publishes them.
 * 
 * @author Kohsuke Kawaguchi
 * @author Mike Rooney
 */
public class HtmlPublisher extends Recorder {
    private final ArrayList<HtmlPublisherTarget> reportTargets;

    @DataBoundConstructor
    public HtmlPublisher(List<HtmlPublisherTarget> reportTargets) {
        this.reportTargets = new ArrayList<HtmlPublisherTarget>(reportTargets);
    }
    
    public ArrayList<HtmlPublisherTarget> getReportTargets() {
        return this.reportTargets;
    }

    private static void writeFile(ArrayList<String> lines, File path) throws IOException {
        BufferedWriter bw = new BufferedWriter(new FileWriter(path));
        for (int i = 0; i < lines.size(); i++) {
            bw.write(lines.get(i));
            bw.newLine();
        }
        bw.close();
        return;
    }

    public ArrayList<String> readFile(String filePath) throws java.io.FileNotFoundException,
            java.io.IOException {
        ArrayList<String> aList = new ArrayList<String>();

        try {
            final InputStream is = this.getClass().getResourceAsStream(filePath);
            try {
                final Reader r = new InputStreamReader(is);
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

    @Override
    public boolean perform(AbstractBuild<?, ?> build, Launcher launcher, BuildListener listener)
            throws InterruptedException {
        listener.getLogger().println("[htmlpublisher] Archiving HTML reports...");
        
        // Grab the contents of the header and footer as arrays
        ArrayList<String> headerLines;
        ArrayList<String> footerLines;
        try {
            headerLines = this.readFile("/htmlpublisher/HtmlPublisher/header.html");
            footerLines = this.readFile("/htmlpublisher/HtmlPublisher/footer.html");
        } catch (FileNotFoundException e1) {
            e1.printStackTrace();
            return false;
        } catch (IOException e1) {
            e1.printStackTrace();
            return false;
        }
        
        for (int i=0; i < this.reportTargets.size(); i++) {
            // Create an array of lines we will eventually write out, initially the header.
            ArrayList<String> reportLines = new ArrayList<String>(headerLines);
            HtmlPublisherTarget reportTarget = this.reportTargets.get(i); 
            boolean keepAll = reportTarget.getKeepAll();
            
            FilePath archiveDir = build.getWorkspace().child(reportTarget.getReportDir());
            FilePath targetDir = reportTarget.getArchiveTarget(build);
            
            String levelString = keepAll ? "BUILD" : "PROJECT"; 
            listener.getLogger().println("[htmlpublisher] Archiving at " + levelString + " level " + archiveDir + " to " + targetDir);

            // The index name might be a comma separated list of names, so let's figure out all the pages we should index.
            String[] csvReports = reportTarget.getReportFiles().split(",");
            ArrayList<String> reports = new ArrayList<String>();
            for (int j=0; j < csvReports.length; j++) {
                String report = csvReports[j];
                report = report.trim();
                
                // Ignore blank report names caused by trailing or double commas.
                if (report.equals("")) {continue;}
                
                reports.add(report);
                String tabNo = "tab" + (j + 1);
                // Make the report name the filename without the extension.
                int end = report.lastIndexOf(".");
                String reportName;
                if (end > 0) {
                    reportName = report.substring(0, end);
                } else {
                    reportName = report;
                }
                String tabItem = "<li id=\"" + tabNo + "\" class=\"unselected\" onclick=\"updateBody('" + tabNo + "');\" value=\"" + report + "\">" + reportName + "</li>";
                reportLines.add(tabItem);
            }
            // Add the JS to change the link as appropriate.
            String hudsonUrl = Hudson.getInstance().getRootUrl();
            reportLines.add("<script type=\"text/javascript\">document.getElementById(\"hudson_link\").href=\"" + hudsonUrl + "\";</script>");
    
            try {
                if (!archiveDir.exists()) {
                    listener.error("Specified HTML directory '" + archiveDir + "' does not exist.");
                    build.setResult(Result.FAILURE);
                    return true;
                } else if (!keepAll) {
                    // We are only keeping one copy at the project level, so remove the old one.
                    targetDir.deleteRecursive();
                }
    
                if (archiveDir.copyRecursiveTo("**/*", targetDir) == 0) {
                    listener.error("Directory '" + archiveDir + "' exists but failed copying to '" + targetDir + "'.");
                    if (build.getResult().isBetterOrEqualTo(Result.UNSTABLE)) {
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
    
            reportTarget.handleAction(build);
    
            // Now add the footer.
            reportLines.addAll(footerLines);
            // And write this as the index
            try {
                writeFile(reportLines, new File(targetDir.getRemote(), reportTarget.getWrapperName()));
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        return true;
    }

    @Override
    public Action getProjectAction(AbstractProject project) {
        if (this.reportTargets.isEmpty()) {
            return null;
        } else {
            //TODO: return ALL project actions, not just the first one
            return this.reportTargets.get(0).getProjectAction(project);            
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

    public BuildStepMonitor getRequiredMonitorService() {
        return BuildStepMonitor.NONE;
    }
}
