package au.org.ala.doi

import grails.util.Holders
import groovyx.net.http.ContentType

import java.util.concurrent.ConcurrentHashMap

class DataQualityService {
    RestService restService

    // Details of specific biocache filters by label.
    ConcurrentHashMap<String, Map> filterDescriptions = new ConcurrentHashMap()
    long age = 0L
    long maxAge = 1000*60*60

    ConcurrentHashMap getFilterDescriptions() {
        // update by maxAge in ms
        if (System.currentTimeMillis() - age > maxAge) {
            String url = Holders.config.dqservice.url + '/api/v1/data-profiles?max=100&offset=0&sort=id&order=desc&enabled=true'
            Map response = restService.get(url, ContentType.JSON, ContentType.JSON)
            def profiles = response.data

            for (def profile : profiles) {
                for (def category : profile.categories) {
                    filterDescriptions.put(category.label, category)
                }
            }
        }

        return filterDescriptions
    }



}
