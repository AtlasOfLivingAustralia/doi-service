package au.org.ala.doi.providers

import au.org.ala.doi.util.ServiceResponse

class MockService extends DoiProviderService {

    @Override
    def generateRequestPayload(Map metadata, String landingPageUrl, String doi) {
        return [:]
    }

    @Override
    ServiceResponse invokeCreateService(Object requestPayload, String landingPageUrl) {
        def response = new ServiceResponse(200, '', "ABC")
        response.doi = "10.1000/${UUID.randomUUID()}"
        return response
    }

    @Override
    ServiceResponse invokeUpdateService(String doi, Map requestPayload, String landingPageUrl) {
        def response = new ServiceResponse(200, '', "ABC")
        response.doi = "10.1000/${UUID.randomUUID()}"
        return response
    }
}
