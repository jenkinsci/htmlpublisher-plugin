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
import hudson.matrix.MatrixConfiguration;
import hudson.matrix.MatrixProject;
import hudson.model.*;
import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.BuildStepMonitor;
import hudson.tasks.Publisher;
import hudson.tasks.Recorder;
import hudson.util.FormValidation;
import org.apache.http.client.fluent.Request;
import org.apache.http.entity.ContentType;
import org.kohsuke.stapler.AncestorInPath;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;

import javax.servlet.ServletException;
import java.io.*;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

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
        this.reportTargets = reportTargets != null ? new ArrayList<HtmlPublisherTarget>(reportTargets) : new ArrayList<HtmlPublisherTarget>();
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

    protected static String resolveParametersInString(AbstractBuild<?, ?> build, BuildListener listener, String input) {
        try {
            return build.getEnvironment(listener).expand(input);
        } catch (Exception e) {
            listener.getLogger().println("Failed to resolve parameters in string \""+
            input+"\" due to following error:\n"+e.getMessage());
        }
        return input;
    }

    @Override
    public boolean perform(AbstractBuild<?, ?> build, Launcher launcher, BuildListener listener)
            throws InterruptedException {
        listener.getLogger().println("[CloudLock] Publishing Status to Geckoboard...");

        String status = build.getResult() == Result.SUCCESS ? "Up" : "Down";

        String body = "{\n" +
                "  \"api_key\":\"9c58a0146cfb6de499dc731784484dbb\",\n" +
                "  \"data\": {\n" +
                "  \"status\": \"" + status + "\",\n" +
                "  \"downTime\": \"123\",\n" +
                "  \"responseTime\": \"593 ms\"\n" +
                "}\n" +
                "}";

        try {
            Request.Post("https://push.geckoboard.com/v1/send/48864-587e55b0-4639-0131-02b1-22000a9e51f0")
                    .bodyString(body, ContentType.APPLICATION_JSON)
                    .execute().returnContent();
            listener.getLogger().println("[CloudLock] Status published to GeckoBoard");
        } catch (IOException e) {
            listener.getLogger().println("Failed to make request to geckoboard " + e.getMessage());
            return false;
        }

        return true;
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

    public BuildStepMonitor getRequiredMonitorService() {
        return BuildStepMonitor.NONE;
    }
}
