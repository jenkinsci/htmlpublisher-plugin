/*
 * The MIT License
 *
 * Copyright 2015 CloudBees Inc.
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
package htmlpublisher.workflow;

import htmlpublisher.HtmlPublisherTarget;
import hudson.Extension;
import hudson.model.Action;
import hudson.model.Job;
import hudson.model.Run;
import java.util.Collection;
import java.util.LinkedList;
import java.util.List;
import jenkins.model.TransientActionFactory;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.DoNotUse;

import javax.annotation.Nonnull;

/**
 * Produces actions for workflow jobs.
 * @author Oleg Nenashev
 */
@Restricted(DoNotUse.class)
@Extension
public class WorkflowActionsFactory extends TransientActionFactory<Job> {

    @Override 
    public Class<Job> type() {
        return Job.class;
    }

    @Override
    @Nonnull
    public Collection<? extends Action> createFor(Job j) {
        List<Action> actions = new LinkedList<>();
        if (j.getClass().getCanonicalName().startsWith("org.jenkinsci.plugins.workflow"))
        {
            final Run<?,?> r = j.getLastSuccessfulBuild();
            if (r != null) {
                // If reports are being saved on the build level (keep for all builds)
                List<HtmlPublisherTarget.HTMLBuildAction> reports = r.getActions(HtmlPublisherTarget.HTMLBuildAction.class);
                for (HtmlPublisherTarget.HTMLBuildAction report : reports) {
                    actions.add(report.getHTMLTarget().getProjectAction(j));
                }
                
                // If reports are being saved on the project level
                List<HtmlPublisherTarget.HTMLPublishedForProjectMarkerAction> projectLevelReports = 
                        r.getActions(HtmlPublisherTarget.HTMLPublishedForProjectMarkerAction.class);
                for (HtmlPublisherTarget.HTMLPublishedForProjectMarkerAction report : projectLevelReports) {
                    actions.add(report.getHTMLTarget().getProjectAction(j));
                }
            }       
        }
        return actions;
    }
}
