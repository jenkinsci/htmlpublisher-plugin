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

import htmlpublisher.HtmlPublisher;
import htmlpublisher.HtmlPublisherTarget;
import hudson.AbortException;
import hudson.FilePath;
import hudson.Launcher;
import hudson.model.Run;
import hudson.model.TaskListener;
import java.util.Arrays;
import javax.inject.Inject;
import org.jenkinsci.plugins.workflow.steps.AbstractSynchronousNonBlockingStepExecution;
import org.jenkinsci.plugins.workflow.steps.StepContextParameter;

/**
 * Execution for {@link PublishHTMLStep}.
 * @author Oleg Nenashev
 * @since TODO
 */
public class PublishHTMLStepExecution extends AbstractSynchronousNonBlockingStepExecution<Void> {
    
    @StepContextParameter
    private transient TaskListener listener;

    @StepContextParameter
    private transient FilePath ws;

    @StepContextParameter
    private transient Run build;

    @StepContextParameter
    private transient Launcher launcher;

    @Inject
    private transient PublishHTMLStep step;
    
    @Override
    protected Void run() throws Exception {             
        final HtmlPublisherTarget target = step.getTarget();
        if (target == null) {
            throw new AbortException("Cannot publish the report. Target is not specified");
        }
        
        boolean res = HtmlPublisher.publishReports(build, ws, launcher, listener, 
                Arrays.asList(target), HtmlPublisher.class);
        if (!res) {
            throw new AbortException("Cannot publish HTML files");
        }      
        return null;
    }

    private static final long serialVersionUID = 1L;
}
