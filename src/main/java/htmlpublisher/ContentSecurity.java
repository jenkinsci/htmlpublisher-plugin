/*
 * The MIT License
 *
 * Copyright (c) 2025, Michael Clarke
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

import java.util.Arrays;
import java.util.Map;
import java.util.Objects;
import java.util.logging.Logger;
import java.util.stream.Collectors;

import hudson.Extension;
import hudson.model.AbstractDescribableImpl;
import hudson.model.Descriptor;
import jenkins.model.Jenkins;
import org.apache.commons.lang.StringUtils;
import org.kohsuke.stapler.DataBoundConstructor;

public class ContentSecurity extends AbstractDescribableImpl<ContentSecurity> {

    private static final Logger LOGGER = Logger.getLogger(ContentSecurity.class.getName());

    private final Scripts scripts;
    private final Styles styles;
    private final Images images;
    private final boolean allowSameOrigin;

    private transient boolean headerWarningLogged = false;
    private transient boolean iframeWarningLogged = false;

    @DataBoundConstructor
    public ContentSecurity(Scripts scripts, Styles styles, Images images, boolean allowSameOrigin) {
        this.scripts = scripts;
        this.styles = styles;
        this.images = images;
        this.allowSameOrigin = allowSameOrigin;
    }

    public Scripts getScripts() {
        return scripts;
    }

    public Styles getStyles() {
        return styles;
    }

    public Images getImages() {
        return images;
    }

    public boolean isAllowSameOrigin() {
        return allowSameOrigin;
    }

    public String createAlteredContentSecurityPolicy(String contentSecurityPolicy) {
        if (isContentSecurityAlterationDisabled()) {
            if (areAnyNotNull(scripts, images, styles) || allowSameOrigin) {
                LOGGER.log(headerWarningLogged ? java.util.logging.Level.FINE : java.util.logging.Level.WARNING,
                        "Content security policy override has been disabled. Content-Security-Policy header will not be modified.");
                headerWarningLogged = true;
            }
            return contentSecurityPolicy;
        }
        if (StringUtils.trimToNull(contentSecurityPolicy) == null) {
            return null;
        }
        Map<String, String> directives = Arrays.stream(contentSecurityPolicy.split(";")).collect(
                Collectors.toMap(
                        directive -> directive.trim().split(" ")[0],
                        directive -> directive,
                        (directive1, directive2) -> directive1
                )
        );

        StringBuilder modifiedCsp = new StringBuilder();
        modifiedCsp.append(createSandboxContentSecurityDirective(directives.remove("sandbox")));
        if (scripts != null) {
            modifiedCsp.append(createScriptsContentSecurityDirective(directives.remove("script-src"), getScripts()));
        }
        if (styles != null) {
            modifiedCsp.append(createStylesContentSecurityDirective(directives.remove("style-src"), getStyles()));
        }
        if (images != null) {
            modifiedCsp.append(createImagesContentSecurityDirective(directives.remove("img-src"), getImages()));
        }


        directives.forEach((k, v) -> modifiedCsp.append(v).append("; "));
        return modifiedCsp.toString().trim();
    }

    private static boolean isContentSecurityAlterationDisabled() {
        return !((HtmlPublisher.DescriptorImpl) Jenkins.get().getDescriptorOrDie(HtmlPublisher.class)).isAllowContentSecurityOverride();
    }

    private static boolean areAnyNotNull(Object... objects) {
        for (Object o : objects) {
            if (Objects.isNull(o)) {
                return true;
            }
        }
        return false;
    }

    private String createSandboxContentSecurityDirective(String sandboxDirective) {
        if (sandboxDirective == null) {
            sandboxDirective = "sandbox ";
        }
        if (scripts != null && !sandboxDirective.contains("allow-scripts")) {
            sandboxDirective = sandboxDirective.trim() + " allow-scripts ";
        }
        if (allowSameOrigin && !sandboxDirective.contains("allow-same-origin")) {
            sandboxDirective = sandboxDirective.trim() + " allow-same-origin ";
        }
        return sandboxDirective.trim() + "; ";
    }

    private static String createScriptsContentSecurityDirective(String scriptSrcDirective, Scripts scripts) {
        if (scriptSrcDirective == null) {
            scriptSrcDirective = "script-src 'self'";
        } else {
            scriptSrcDirective = scriptSrcDirective.trim();
        }
        if (scripts.isAllowUnsafeInline() && !scriptSrcDirective.contains("'unsafe-inline'")) {
            scriptSrcDirective += " 'unsafe-inline'";
        }
        if (scripts.isAllowUnsafeEval() && !scriptSrcDirective.contains("'unsafe-eval'")) {
            scriptSrcDirective += " 'unsafe-eval'";
        }
        return scriptSrcDirective + "; ";
    }

    private static String createStylesContentSecurityDirective(String styleSrcDirective, Styles styles) {
        if (styleSrcDirective == null) {
            styleSrcDirective = "style-src 'self'";
        } else {
            styleSrcDirective = styleSrcDirective.trim();
        }
        if (styles.isAllowUnsafeInline() && !styleSrcDirective.contains("'unsafe-inline'")) {
            styleSrcDirective += " 'unsafe-inline'";
        }
        return styleSrcDirective + "; ";
    }

    private static String createImagesContentSecurityDirective(String imageSrcDirective, Images images) {
        if (imageSrcDirective == null) {
            imageSrcDirective = "img-src 'self'";
        } else {
            imageSrcDirective = imageSrcDirective.trim();
        }
        if (images.isAllowData() && !imageSrcDirective.contains("data:")) {
            imageSrcDirective += " data:";
        }
        return imageSrcDirective + "; ";
    }

    public String createIframeSandboxAttributes() {
        if (isContentSecurityAlterationDisabled()) {
            if (areAnyNotNull(scripts, images, styles) || allowSameOrigin) {
                LOGGER.log(iframeWarningLogged ? java.util.logging.Level.FINE : java.util.logging.Level.WARNING,
                        "Content security policy override has been disabled. Iframe sandbox will not be modified.");
                iframeWarningLogged = true;
            }
            return "";
        }
        String sandbox = "";
        if (getScripts() != null) {
            sandbox += " allow-scripts";
        }
        if (isAllowSameOrigin()) {
            sandbox += " allow-same-origin";
        }
        return sandbox.trim();
    }

    @Extension
    public static class DescriptorImpl extends Descriptor<ContentSecurity> {

        @Override
        public String getDisplayName() {
            return "Content Security Policy";
        }

    }

    public static class Scripts extends AbstractDescribableImpl<Scripts> {

        private final boolean allowUnsafeInline;
        private final boolean allowUnsafeEval;

        @DataBoundConstructor
        public Scripts(boolean allowUnsafeInline, boolean allowUnsafeEval) {
            this.allowUnsafeInline = allowUnsafeInline;
            this.allowUnsafeEval = allowUnsafeEval;
        }

        public boolean isAllowUnsafeInline() {
            return allowUnsafeInline;
        }

        public boolean isAllowUnsafeEval() {
            return allowUnsafeEval;
        }

        @Extension
        public static class DescriptorImpl extends Descriptor<Scripts> {
            @Override
            public String getDisplayName() {
                return "Scripts";
            }
        }

    }

    public static class Styles extends AbstractDescribableImpl<Styles> {

        private final boolean allowUnsafeInline;

        @DataBoundConstructor
        public Styles(boolean allowUnsafeInline) {
            this.allowUnsafeInline = allowUnsafeInline;
        }

        public boolean isAllowUnsafeInline() {
            return allowUnsafeInline;
        }

        @Extension
        public static class DescriptorImpl extends Descriptor<Styles> {
            @Override
            public String getDisplayName() {
                return "Styles";
            }
        }

    }

    public static class Images extends AbstractDescribableImpl<Images> {

        private final boolean allowData;

        @DataBoundConstructor
        public Images(boolean allowData) {
            this.allowData = allowData;
        }

        public boolean isAllowData() {
            return allowData;
        }

        @Extension
        public static class DescriptorImpl extends Descriptor<Images> {
            @Override
            public String getDisplayName() {
                return "Images";
            }
        }

    }
}
