/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.sling.urlrewriter.internal;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;

import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import org.apache.commons.io.IOUtils;
import org.apache.sling.engine.EngineConstants;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.ConfigurationPolicy;
import org.osgi.service.component.annotations.Deactivate;
import org.osgi.service.component.annotations.Modified;
import org.osgi.service.component.propertytypes.ServiceDescription;
import org.osgi.service.component.propertytypes.ServiceRanking;
import org.osgi.service.component.propertytypes.ServiceVendor;
import org.osgi.service.metatype.annotations.AttributeDefinition;
import org.osgi.service.metatype.annotations.Designate;
import org.osgi.service.metatype.annotations.ObjectClassDefinition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.tuckey.web.filters.urlrewrite.Conf;
import org.tuckey.web.filters.urlrewrite.UrlRewriter;
import org.tuckey.web.filters.urlrewrite.utils.Log;
import org.tuckey.web.filters.urlrewrite.utils.StringUtils;
import org.w3c.dom.Document;
import org.xml.sax.helpers.DefaultHandler;

@Component(
        service = {SlingUrlRewriteFilter.class},
        immediate = true,
        configurationPolicy = ConfigurationPolicy.REQUIRE,
        property = {
                EngineConstants.SLING_FILTER_SCOPE + "=" + EngineConstants.FILTER_SCOPE_REQUEST,
                EngineConstants.SLING_FILTER_SCOPE + "=" + EngineConstants.FILTER_SCOPE_FORWARD
        })
@ServiceRanking(0)
@ServiceVendor("The Apache Software Foundation")
@ServiceDescription("multi-purpose service for altering HTTP requests/responses based on Tuckey's UrlRewriteFilter")
@Designate(ocd = SlingUrlRewriteFilter.Config.class)
public final class SlingUrlRewriteFilter implements Filter {

    private UrlRewriter rewriter;

    public static final String DEFAULT_REWRITE_RULES = "<?xml version=\"1.0\" encoding=\"utf-8\"?><!DOCTYPE urlrewrite PUBLIC \"-//tuckey.org//DTD UrlRewrite 4.0//EN\" \"http://www.tuckey.org/res/dtds/urlrewrite4.0.dtd\"><urlrewrite/>";

    @ObjectClassDefinition( localization = "OSGI-INF/l10n/metatype",
            name = "Apache Sling URL Rewriter",
            description = "multi-purpose service for altering HTTP requests/responses based on Tuckey's UrlRewriteFilter")
    public @interface Config {

        @AttributeDefinition
        String org_apache_sling_urlrewriter_rewrite_rules() default DEFAULT_REWRITE_RULES;
    }

    private final Logger logger = LoggerFactory.getLogger(SlingUrlRewriteFilter.class);

    public SlingUrlRewriteFilter() {
        Log.setLevel("SLF4J");
    }

    @Activate
    private void activate(final Config config) {
        logger.debug("activate");
        configure(config);
    }

    @Modified
    private void modified(final Config config) {
        logger.debug("modified");
        configure(config);
    }

    @Deactivate
    private void deactivate() {
        logger.debug("deactivate");
        clearRewriter();
    }

    private void configure(final Config config) {
        logger.info("configuring URL rewriter");
        final String rules = config.org_apache_sling_urlrewriter_rewrite_rules();

        final Document document = createDocument(rules);
        if (document == null) {
            logger.error("creating rules document failed");
            return;
        }

        final Conf conf = new DocumentConf(document);
        conf.initialise();
        clearRewriter();

        if (conf.isOk()) {
            logger.info("rewrite configuration is ok");
        } else {
            logger.error("rewrite configuration is NOT ok");
            return;
        }

        rewriter = new UrlRewriter(conf);
        logger.info("rewrite engine is enabled: {}", conf.isEngineEnabled());
        if (conf.getRules() != null) {
            logger.info("number of rewrite rules: {}", conf.getRules().size());
        } else {
            logger.info("no rewrite rules");
        }
    }

    @Override
    public void destroy() {
        logger.debug("destroy()");
    }

    @Override
    public void init(final FilterConfig filterConfig) throws ServletException {
        logger.debug("init({})", filterConfig);
    }

    @Override
    public void doFilter(final ServletRequest servletRequest, final ServletResponse servletResponse, final FilterChain filterChain) throws IOException, ServletException {
        logger.debug("do filter");
        if (rewriter != null && servletRequest instanceof HttpServletRequest && servletResponse instanceof HttpServletResponse) {
            final HttpServletRequest httpServletRequest = (HttpServletRequest) servletRequest;
            final HttpServletResponse httpServletResponse = (HttpServletResponse) servletResponse;
            final boolean handled = rewriter.processRequest(httpServletRequest, httpServletResponse, filterChain);
            if (handled) {
                logger.debug("request handled by rewriter");
                return;
            }
        }
        logger.debug("request NOT handled by rewriter");
        filterChain.doFilter(servletRequest, servletResponse);
    }

    private Document createDocument(final String rules) {
        if (StringUtils.isBlank(rules)) {
            logger.warn("given rules are blank");
            return null;
        }

        final DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setValidating(true);
        factory.setNamespaceAware(true);
        factory.setIgnoringComments(true);
        factory.setIgnoringElementContentWhitespace(true);

        try {
            final String systemId = "";
            final ConfHandler confHandler = new ConfHandler(systemId);
            final DocumentBuilder documentBuilder = factory.newDocumentBuilder();
            documentBuilder.setErrorHandler(confHandler);
            documentBuilder.setEntityResolver(confHandler);

            final InputStream inputStream = new ByteArrayInputStream(rules.getBytes("UTF-8"));
            final Document document = documentBuilder.parse(inputStream); // , systemId);
            IOUtils.closeQuietly(inputStream);
            return document;
        } catch (Exception e) {
            logger.error("error creating document from rules property", e);
            return null;
        }
    }

    private synchronized void clearRewriter() {
        if (rewriter != null) {
            rewriter.destroy();
            rewriter = null;
        }
    }

    public class DocumentConf extends Conf {

        public DocumentConf(final Document rules) {
            processConfDoc(rules);
            // TODO check docProcessed
            initialise();
            // TODO set loadedDate
        }

    }

    // TODO
    public class ConfHandler extends DefaultHandler {

        protected final String systemId;

        public ConfHandler(final String systemId) {
            this.systemId = systemId;
        }

    }

}
