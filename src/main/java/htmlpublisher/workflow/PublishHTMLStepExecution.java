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

import java.util.Collections;

import edu.umd.cs.findbugs.annotations.NonNull;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import org.jenkinsci.plugins.workflow.steps.StepContext;
import org.jenkinsci.plugins.workflow.steps.SynchronousNonBlockingStepExecution;

import htmlpublisher.HtmlPublisher;
import htmlpublisher.HtmlPublisherTarget;
import hudson.AbortException;
import hudson.FilePath;
import hudson.model.Run;
import hudson.model.TaskListener;

/**
 * Execution for {@link PublishHTMLStep}.
 * @author Oleg Nenashev
 */
public class PublishHTMLStepExecution extends SynchronousNonBlockingStepExecution<Void> {
    private static final long serialVersionUID = 1L;

    @SuppressFBWarnings(value = "SE_TRANSIENT_FIELD_NOT_RESTORED")
    private final transient PublishHTMLStep step;

    PublishHTMLStepExecution(PublishHTMLStep step, @NonNull StepContext context) {
        super(context);
        this.step = step;
    }

    @Override
    protected Void run() throws Exception {             
        final HtmlPublisherTarget target = step.getTarget();
        if (target == null) {
            throw new AbortException("Cannot publish the report. Target is not specified");
        }
        
        boolean res =
                HtmlPublisher.publishReports(
                        getContext().get(Run.class),
                        getContext().get(FilePath.class),
                        getContext().get(TaskListener.class),
                        Collections.singletonList(target),
                        HtmlPublisher.class);
        if (!res) {
            throw new AbortException("Cannot publish HTML files");
        }      
        return null;
    }

}
