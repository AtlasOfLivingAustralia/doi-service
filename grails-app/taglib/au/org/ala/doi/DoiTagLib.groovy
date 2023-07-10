/*
 * Copyright (C) 2016 Atlas of Living Australia
 * All Rights Reserved.
 * The contents of this file are subject to the Mozilla Public
 * License Version 1.1 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of
 * the License at http://www.mozilla.org/MPL/
 * Software distributed under the License is distributed on an "AS
 * IS" basis, WITHOUT WARRANTY OF ANY KIND, either express or
 * implied. See the License for the specific language governing
 * rights and limitations under the License.
 */

package au.org.ala.doi

import org.apache.http.NameValuePair
import org.apache.http.client.utils.URLEncodedUtils

import java.nio.charset.Charset

class DoiTagLib {
//    static defaultEncodeAs = [taglib:'html']
    //static encodeAsForTags = [tagName: [taglib:'html'], otherTagName: [taglib:'none']]

    static namespace = 'doi'
    static defaultEncodeAs = "raw"

    static final UTF8_CHARSET =  Charset.lookup("UTF-8")
    static final SEPARATORS = "?;&".toCharArray()

    def sanitiseRawContent = { attrs ->
        String content = attrs.content
        try {
            String sanitised = org.jsoup.Jsoup.clean(content, org.jsoup.safety.Whitelist.basic())
            out << sanitised
        } catch (Exception e) {
            log.error "sanitiseRawContent failed for '${content}'. " +
                    "This will be ignored in the output to allow the calling page to display.", e
        }
    }


    /**
     * Format search query
     *
     * @attr searchUrl REQUIRED
     * @attr queryTitle
     */
    def formatSearchQuery = { attrs, body ->
        def searchUrl = attrs.searchUrl
        def queryTitle = attrs.queryTitle
        def dqFilter = attrs.dqFilter
        def content = ""

        try {
            if(queryTitle) {
                queryTitle = org.jsoup.Jsoup.clean(queryTitle, org.jsoup.safety.Whitelist.basic())
            }

            log.debug "searchUrl = ${searchUrl} || queryTitle = ${queryTitle}"

            if (searchUrl) {
                List<NameValuePair> params = URLEncodedUtils.parse(searchUrl, UTF8_CHARSET, SEPARATORS)
                content += "<ul class='searchQueryParams'>"

                for (NameValuePair param : params) {
                    if (param.name && param.value) {
                        String paramValue = ((param.name == "q" && queryTitle) ? queryTitle : param.value)
                        boolean include = true
                        for (def fq : dqFilter) {
                            if (fq.filter == paramValue) {
                                include = false
                            }
                        }
                        if (include && param.name != 'disableAllQualityFilters') {
                            paramValue = paramValue.replaceAll(/ (AND|OR) /, " <span class=\"boolean-op\">\$1</span> ")
                            content += "<li><strong>${g.message code: "doi.param.name.${param.name}", default: "${param.name}"}:</strong>&nbsp;"
                            List fieldItems = paramValue.tokenize(':')
                            log.debug "fieldItems = ${fieldItems.size()}"

                            if (fieldItems.size() == 2 && paramValue != "*:*") {
                                // Attempt to substitute i18n labels where possible
                                content += "${g.message code: "facet.${fieldItems[0]}", default: "${fieldItems[0]}"}:"
                                log.debug "if: i18n: \"facet.${fieldItems[0]}\" || ${g.message(code: "facet.${fieldItems[0]}")}"
                                content += "${g.message code: "${fieldItems[0]}.${fieldItems[1]}", default: "${fieldItems[1]}"}</li>"
                            } else {
                                content += "${g.message code: "doi.param.value.${paramValue}", default: "${paramValue}"}</li>"
                                log.debug "else: i18n: \"doi.param.value.${paramValue}\" || ${g.message(code: "doi.param.value.${paramValue}")}"
                            }
                        }
                    }

                }

                content += "</ul>"
            }

            out << content
        } catch (Exception e) {
            log.error "formatSearchQuery failed for searchUrl = '${searchUrl}' and queryTitle = '${queryTitle}'. " +
                    "This will be ignored in the output to allow the calling page to display.", e
        }
    }

}
