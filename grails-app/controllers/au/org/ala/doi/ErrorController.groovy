package au.org.ala.doi


import grails.converters.JSON
import org.apache.http.entity.ContentType

class ErrorController {
    def error() {
        withFormat {
            html {
                switch(response.status) {
                    case 404:
                        render view: '/notfound'
                        break;
                    case 403:
                    case 401:
                        render view: '/unauthorised'
                        break;
                    case 500:
                    case 400:
                    default:
                        render view: '/error'
                        break;
                }
            }
            json {
                response.contentType = ContentType.APPLICATION_JSON

                def result = [message: request.'javax.servlet.error.message']
                render result as JSON
            }
        }
    }
}
