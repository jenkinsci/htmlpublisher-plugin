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

import com.google.gson.Gson;
import hudson.Extension;
import hudson.FilePath;
import hudson.Launcher;
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
import java.util.*;

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

        for (HtmlPublisherTarget target : getReportTargets()) {
            listener.getLogger().println("[CloudLock] Publishing " + target.getName() + " to Geckoboard");
            saveStatusFile(build, target, listener);
            postToServer(build, target, listener);
        }

        return true;
    }

    private void saveStatusFile(AbstractBuild<?, ?> build, HtmlPublisherTarget target, BuildListener listener) {
        try {
            String dir = System.getenv("JENKINS_STATUS_DIR");

            Map<String, String> map = new HashMap<String, String>();
            String status = build.getResult() == Result.SUCCESS ? "passed" : "failed";
            map.put("status", status);

            Gson gson = new Gson();

            File parentDir = new File(dir);
            File file = new File(parentDir, build.getDisplayName());
            PrintWriter writer = new PrintWriter(file, "UTF-8");
            writer.println(gson.toJson(map));
            writer.close();

            listener.getLogger().println("Successfully created file: " + file.getAbsolutePath());
        } catch (Exception e) {
            listener.getLogger().println("Failed to create status file." + e.getMessage());
        }
    }

    private void postToServer(AbstractBuild<?, ?> build, HtmlPublisherTarget target, BuildListener listener) {
        try {
            String url = target.getUrl();
            String body = buildStatus(build, target);
            String content = Request.Post(url)
                    .bodyString(body, ContentType.APPLICATION_JSON)
                    .execute().returnContent().asString();
            listener.getLogger().println("[CloudLock] Status published to GeckoBoard");
            listener.getLogger().println(content);
        } catch (IOException e) {
            listener.getLogger().println("Failed to make request to geckoboard " + e.getMessage());
        }
    }

    private String buildStatus(AbstractBuild<?, ?> build, HtmlPublisherTarget target) {
        String status = build.getResult() == Result.SUCCESS ? "passed" : "failed";

        String json_template = target.getJson();

        json_template = json_template.replace("%status%", status);
        json_template = json_template.replace("%api_key%", target.getApiKey());
        return json_template;
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
