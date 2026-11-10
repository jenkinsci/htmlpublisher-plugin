/*
 * The MIT License
 *
 * Copyright (c) 2026, CloudBees, Inc.
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
import java.net.MalformedURLException;
import java.net.URL;
import jenkins.security.ResourceDomainConfiguration;
import jenkins.security.csp.Contributor;
import jenkins.security.csp.CspBuilder;
import jenkins.security.csp.FetchDirective;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;
import org.kohsuke.accmod.restrictions.suppressions.SuppressRestrictedWarnings;
import org.kohsuke.stapler.Stapler;
import org.kohsuke.stapler.StaplerRequest2;

/**
 * Allow {@code frame-src} to the Resource Root URL if and only if we're serving a {@link HtmlPublisherTarget.BaseHTMLAction}.
 */
@Restricted(NoExternalUse.class)
@Extension
public class CspContributor implements Contributor {
    @Override
    @SuppressRestrictedWarnings({ResourceDomainConfiguration.class})
    public void apply(CspBuilder builder) {
        if (!ResourceDomainConfiguration.isResourceDomainConfigured()) {
            return;
        }
        StaplerRequest2 request = Stapler.getCurrentRequest2();
        if (request == null) {
            return;
        }

        // Limit handling to when we're in an affected action
        final HtmlPublisherTarget.BaseHTMLAction action = request.findAncestorObject(HtmlPublisherTarget.BaseHTMLAction.class);
        if (action == null) {
            return;
        }

        try {
            String url = ResourceDomainConfiguration.get().getUrl();
            if (url == null) {
                // to be safe (e.g., concurrent change after #isResourceDomainConfigured)
                return;
            }
            URL parsed = new URL(url);
            int port = parsed.getPort();
            String origin = parsed.getProtocol() + "://" + parsed.getHost()
                    + (port != -1 ? ":" + port : "");
            builder.add(FetchDirective.FRAME_SRC.toKey(), origin);
        } catch (MalformedURLException ignored) {
            // misconfigured RRU — nothing to contribute
        }
    }
}
