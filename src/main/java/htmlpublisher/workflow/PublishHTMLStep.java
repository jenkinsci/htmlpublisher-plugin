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
import javax.annotation.CheckForNull;
import org.jenkinsci.plugins.workflow.steps.AbstractStepDescriptorImpl;
import org.jenkinsci.plugins.workflow.steps.AbstractStepImpl;
import org.kohsuke.stapler.DataBoundConstructor;

/**
 * Publishes HTML reports in Workflows.
 * @author Oleg Nenashev
 */
public class PublishHTMLStep extends AbstractStepImpl {
    
    private final HtmlPublisherTarget target;

    /**
     * Constructor.
     * @param target Target report to be published. May be null due if a user specifies an 
     *               improper workflow (e.g. due to JENKINS-29711).
     */
    @DataBoundConstructor
    public PublishHTMLStep(@CheckForNull HtmlPublisherTarget target) {
        this.target = target;
    }

    @CheckForNull
    public HtmlPublisherTarget getTarget() {
        return target;
    }
    
    @Extension
    public static class DescriptorImpl extends AbstractStepDescriptorImpl {

        public DescriptorImpl() {
            super(PublishHTMLStepExecution.class);
        }

        @Override
        public String getFunctionName() {
            return "publishHTML";
        }

        @Override
        public String getDisplayName() {
            return "Publish HTML reports";
        }
    }
}
