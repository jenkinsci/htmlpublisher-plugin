# Change Log

Releases since version 1.19 are now documented as [GitHub releases](https://github.com/jenkinsci/htmlpublisher-plugin/releases).

## Older Releases

### Version 1.18 (January 17th, 2019)

-   Small fix to prevent overlapping of tabs when there are too many for
    one line
    ([PR-39](https://github.com/jenkinsci/htmlpublisher-plugin/pull/39))
-   Change to the code to allow reportTitle to truly be optional
    ([PR-40](https://github.com/jenkinsci/htmlpublisher-plugin/pull/40))

### Version 1.17 (October 3rd, 2018)

-   With the security fix, underscores in the Report Title were escaped
    in the URL and directory name as part of the process. The default
    behaviour is still to do this but a new option "Escape Underscores"
    can now be set to "False" to not do this
    ([PR-36](https://github.com/jenkinsci/htmlpublisher-plugin/pull/36))
-   The "Back To" link at the top left of the HTML reports viewer will
    now take you back to the project or build depending if it was
    selected from the project or build respectively
    ([PR-38](https://github.com/jenkinsci/htmlpublisher-plugin/pull/38))

### Version 1.16 (April 16th, 2018)

-   [Fix](https://jenkins.io/security/advisory/2018-04-16/) [security
    vulnerability](https://jenkins.io/security/advisory/2018-04-16/)[  
    **Note:** As a side effect of this change, the URLs and directory
    names of archived reports will change. If you have external links
    pointing to reports in Jenkins, they may need to be
    adapted.](https://jenkins.io/security/advisory/2018-04-16/)

### Version 1.15 (March 28th, 2018)

-   White space in report name, directory, files, and titles
    configuration settings is now all trimmed.
    ([JENKINS-47034](https://issues.jenkins-ci.org/browse/JENKINS-47034))
-   Index page titles can now be passed in as a parameter for Freestyle
    builds (Part of
    [JENKINS-44786](https://issues.jenkins-ci.org/browse/JENKINS-44786))
-   **Note**: Currently the HTML Publisher plugin will resolve
    parameters passed in with pipeline builds. This is not considered
    best practice and therefore should be considered deprecated -
    resolving the parameter should be done via Groovy. The current
    behaviour will be removed in a future release but will only create a
    warning in this release. 

### Version 1.14 (July 7th, 2017)

-   Allow user to set file includes pattern
    ([PR-25](https://github.com/jenkinsci/htmlpublisher-plugin/pull/25))

### Version 1.13 (April 18th, 2017)

-   Allow specifying tab titles for report files
    ([PR-27](https://github.com/jenkinsci/htmlpublisher-plugin/pull/27))

### Version 1.12 (January 6th, 2017)

-   Fix "PublishHTMLStepExecution.run can block CPS thread"
    ([JENKINS-40447](https://issues.jenkins-ci.org/browse/JENKINS-40447))

#### Version 1.11 (February 4th, 2016)

-   Fix HTML report shows "Checksum mismatch"
    ([JENKINS-32281](https://issues.jenkins-ci.org/browse/JENKINS-32281))

#### Version 1.10 (December 13th, 2015)

-   [Content-Security-Policy (Jenkins Security Advisory 2015-12-09)
    compatibility](https://wiki.jenkins-ci.org/display/SECURITY/Jenkins+Security+Advisory+2015-12-09).
-   Fixed "Back link doesn't work after job renaming"
    ([JENKINS-29679](https://issues.jenkins-ci.org/browse/JENKINS-29679))

#### Version 1.9 (November 4th, 2015)

-   added wrapperName field to maintain serialization compatibility.
    ([JENKINS-31366](https://issues.jenkins-ci.org/browse/JENKINS-31366))

#### Version 1.8 (October 18th, 2015)

-   revert "Support FileSet includes" due to
    [JENKINS-31018](https://issues.jenkins-ci.org/browse/JENKINS-31018)

#### Version 1.7 (October 16th, 2015)

-   Support FileSet includes (ant patterns) for report files
    ([JENKINS-7139](https://issues.jenkins-ci.org/browse/JENKINS-7139))
    (reverted in 1.8)

#### Version 1.6 (August 23rd, 2015)

-   Workflow plugin integration
    ([JENKINS-26343](https://issues.jenkins-ci.org/browse/JENKINS-26343))

#### Version 1.5 (July 26th, 2015)

-   Clean up / improve the configuration UI
-   Restore removed constructor in 1.4 to fix binary compatibility
    ([JENKINS-29626](https://issues.jenkins-ci.org/browse/JENKINS-29626))

#### Version 1.4 (May 24th, 2015)

-   Add an option to publish HTML reports even if the build fails.
    (JENKINS-11689, JENKINS-24057)

#### Version 1.3 (Nov 13th, 2013)

-   Add an option to allow a build not to fail if a report is not
    present
-   fix "html publisher plugin overrides report encoding with
    iso-8859-1" JENKINS-19268

#### Version 1.2 (Dec 10th, 2012)

-   revert "support Ant patterns in archive directory" to fix
    JENKINS-16083

#### Version 1.1 (Dec 7th, 2012)

-   support Ant patterns in archive directory (reverted in 1.2)
-   fix viewing HTML report for specific builds (12967@issue)
-   fix NPE (14491@issue)

#### Version 1.0 (May 10th, 2012)

-   Support environment variables when configuring the report directory
    and index pages
    ([10273@issue](https://issues.jenkins-ci.org/browse/JENKINS-10273))

#### Version 0.8 (Apr 26th, 2012)

-   Add empty descriptor to HtmlPublisherTarget
    ([JENKINS-12258](https://issues.jenkins-ci.org/browse/JENKINS-12258))
-   Scrollbar in HTML publisher due to 100% height on div/iframe
    ([JENKINS-13070](https://issues.jenkins-ci.org/browse/JENKINS-13070))
-   HTML Publisher does not work for multi-configuration projects
    ([JENKINS-8832](https://issues.jenkins-ci.org/browse/JENKINS-8832))

#### Version 0.7 (Aug 2nd, 2011)

-   Update to work with Jenkins 1.418+

#### Version 0.6 (Jan 20th, 2011)

-   Added Zip option to HTML Report View - this will provide a Zip file
    of the contents of the particular report
    ([JENKINS-8163](https://issues.jenkins-ci.org/browse/JENKINS-8163))

#### Version 0.5 (Jan 20th, 2011)

-   This version unintentionally left blank
    ![(smile)](https://wiki.jenkins-ci.org/s/en_GB/8100/5084f018d64a97dc638ca9a178856f851ea353ff/_/images/icons/emoticons/smile.svg)

#### Version 0.4 (May 24th, 2010)

-   The "Back to Jenkins" link is now "Back to JOBNAME" and goes back to
    the job instead of the Jenkins root
    ([JENKINS-6521](https://issues.jenkins-ci.org/browse/JENKINS-6521))

#### Version 0.3 (May 10th, 2010)

-   display per-build report links after a restart (only works for
    builds after the upgrade, alas)
    ([JENKINS-5775](https://issues.jenkins-ci.org/browse/JENKINS-5775))
-   don't display report links if there aren't reports yet
    ([JENKINS-5683](https://issues.jenkins-ci.org/browse/JENKINS-5683))
-   "Back to Jenkins" link triggers a back action in the browser if the
    user hasn't configured the Jenkins URL
    ([JENKINS-6434](https://issues.jenkins-ci.org/browse/JENKINS-6434))

#### Version 0.2.2 (Feb 17th, 2010)

-   Show all project-level reports on the project page, not just the
    first
    ([JENKINS-5069](https://issues.jenkins-ci.org/browse/JENKINS-5069))

#### Version 0.1.0

-   Initial release from abstraction of NCover plugin, allowing for
    archiving and displaying of HTML report directories
