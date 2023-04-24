package au.org.ala.doi

import grails.util.Holders

class DownloadService {

    def webService // via ala-ws-plugin

    def getStatuses(Boolean isAdmin) {
        String url
        if (isAdmin) {
            url = Holders.config.biocache.wsUrl + '/occurrences/offline/status/all'
        } else {
            url = Holders.config.biocache.wsUrl + '/occurrences/offline/status'
        }

       def  response = webService.get(url)
        return  response
    }

    void cancel(String cancelId) {
        String url = Holders.config.biocache.wsUrl + '/occurrences/offline/cancel/' + cancelId
        webService.get(url)
    }
}
