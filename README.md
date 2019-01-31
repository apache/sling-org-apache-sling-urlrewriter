[<img src="https://sling.apache.org/res/logos/sling.png"/>](https://sling.apache.org)

 [![Build Status](https://builds.apache.org/buildStatus/icon?job=Sling/sling-org-apache-sling-urlrewriter/master)](https://builds.apache.org/job/Sling/job/sling-org-apache-sling-urlrewriter/job/master) [![Maven Central](https://maven-badges.herokuapp.com/maven-central/org.apache.sling/org.apache.sling.urlrewriter/badge.svg)](https://search.maven.org/#search%7Cga%7C1%7Cg%3A%22org.apache.sling%22%20a%3A%22org.apache.sling.urlrewriter%22) [![JavaDocs](https://www.javadoc.io/badge/org.apache.sling/org.apache.sling.urlrewriter.svg)](https://www.javadoc.io/doc/org.apache.sling/org.apache.sling.urlrewriter) [![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](https://www.apache.org/licenses/LICENSE-2.0)

# Apache Sling URL Rewriter

This module is part of the [Apache Sling](https://sling.apache.org) project.

multi-purpose service for altering HTTP requests/responses based on Tuckey's UrlRewriteFilter

* http://tuckey.org/urlrewrite/manual/4.0/guide.html
* http://urlrewritefilter.googlecode.com/svn/trunk/src/doc/manual/4.0/index.html

example for setting a Cache-Control header:

    <?xml version="1.0" encoding="utf-8"?>
    <!DOCTYPE urlrewrite PUBLIC "-//tuckey.org//DTD UrlRewrite 4.0//EN" "http://www.tuckey.org/res/dtds/urlrewrite4.0.dtd">
    <urlrewrite>
      <rule>
        <from>.*</from>
        <set type="response-header" name="Cache-Control">max-age=600</set>
      </rule>
    </urlrewrite>

example for setting CORS headers:

    <?xml version="1.0" encoding="UTF-8"?>
    <!DOCTYPE urlrewrite PUBLIC "-//tuckey.org//DTD UrlRewrite 4.0//EN" "http://www.tuckey.org/res/dtds/urlrewrite4.0.dtd">
    <urlrewrite>
      <rule>
        <note>
          http://www.w3.org/TR/cors/
          https://developer.mozilla.org/en-US/docs/HTTP/Access_control_CORS
          http://fetch.spec.whatwg.org
          http://enable-cors.org
          http://www.html5rocks.com/en/tutorials/cors/
        </note>
        <condition type="header" name="Origin">.*</condition>
        <condition type="header" name="Access-Control-Request-Method">.*</condition>
        <condition type="header" name="Access-Control-Request-Headers">.*</condition>
        <set type="response-header" name="Access-Control-Allow-Origin">%{header:Origin}</set>
        <set type="response-header" name="Access-Control-Allow-Methods">%{header:Access-Control-Request-Method}</set>
        <set type="response-header" name="Access-Control-Allow-Headers">%{header:Access-Control-Request-Headers}</set>
        <set type="response-header" name="Access-Control-Allow-Credentials">true</set>
      </rule>
    </urlrewrite>
