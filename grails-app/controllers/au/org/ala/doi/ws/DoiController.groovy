package au.org.ala.doi.ws

import au.ala.org.ws.security.RequireApiKey
import au.ala.org.ws.security.SkipApiKeyCheck
import au.org.ala.doi.BasicWSController
import au.org.ala.doi.DoiSearchService
import au.org.ala.doi.MintResponse
import au.org.ala.doi.SearchDoisCommand
import au.org.ala.doi.exceptions.DoiNotFoundException
import au.org.ala.doi.exceptions.DoiUpdateException
import au.org.ala.doi.exceptions.DoiValidationException
import au.org.ala.doi.storage.S3Storage
import au.org.ala.doi.storage.Storage
import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.google.common.io.ByteSource
import grails.plugins.elasticsearch.ElasticSearchResult
import grails.web.http.HttpHeaders
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.enums.ParameterIn
import io.swagger.v3.oas.annotations.headers.Header
import io.swagger.v3.oas.annotations.links.Link
import io.swagger.v3.oas.annotations.links.LinkParameter
import io.swagger.v3.oas.annotations.media.ArraySchema
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.parameters.RequestBody
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import org.elasticsearch.search.aggregations.Aggregation
import org.elasticsearch.search.fetch.subphase.highlight.HighlightField

import org.springframework.web.multipart.MultipartFile
import org.springframework.web.multipart.MultipartHttpServletRequest

import javax.servlet.http.HttpServletRequest
import javax.validation.constraints.NotNull
import javax.ws.rs.Consumes
import javax.ws.rs.Path
import javax.ws.rs.Produces

import static au.org.ala.doi.util.Utils.isUuid

import au.org.ala.doi.Doi
import au.org.ala.doi.util.DoiProvider
import au.org.ala.doi.DoiService
import grails.converters.JSON

import static io.swagger.v3.oas.annotations.enums.ParameterIn.HEADER
import static io.swagger.v3.oas.annotations.enums.ParameterIn.PATH
import static io.swagger.v3.oas.annotations.enums.ParameterIn.QUERY
import static javax.servlet.http.HttpServletResponse.SC_CREATED
import static javax.servlet.http.HttpServletResponse.SC_OK

@RequireApiKey(scopes=["doi/write"])
class DoiController extends BasicWSController {

    static responseFormats = ['json']

    static namespace = "v1"

    DoiService doiService
    Storage storage
    DoiSearchService doiSearchService

    /**
     * Mint a new DOI. POST only. Must have an ALA API Key.
     *
     * This endpoint accepts 2 formats:
     * <ol>
     *     <li>A Multipart Request, where the metadata is in a parameter called 'json' and the file associated with the DOI is provided in the request; or
     *     <li>A standard post with a JSON body, with a mandatory 'fileUrl' property containing a URL where the file for the DOI can be downloaded from.
     * </ol>
     *
     * The request must have JSON object with the following structure:
     * <pre>
     * {
     *     provider: "ANDS", // the doi provider to use (see {@link DoiProvider} for a list of supported providers)
     *     applicationUrl: "http://....", // the url to the relevant page on the source application. This is NOT the landing page: it is used to provide a link ON the landing page back to the original source of the publication/data/etc for the DOI.
     *     providerMetadata: { // the provider-specific metadata to be sent with the DOI minting request
     *         ...
     *     },
     *     title: "...", // title to be displayed on the landing page
     *     authors: "...", // author(s) to be displayed on the landing page
     *     description: "...", // description to be displayed on the landing page
     *
     *
     *     // the following are optional
     *     fileUrl: "http://....", // the url to use to download the file for the DOI (use this, or send the file as a multipart request)
     *     customLandingPageUrl: "http://...", // an application-specific landing page that you want the DOI to resolve to. If not provided, the default ALA-DOI landing page will be used.
     *     applicationMetadata: { // any application-specific metadata you want to display on the landing page in ALA-DOI
     *         ...
     *     }
     * }
     * </pre>
     *
     * If "fileUrl" is not provided, then you must send the file in a multipart request with the metadata as a JSON string in a form part called 'json'.
     *
     * @return JSON response containing the DOI and the landing page on success, HTTP 500 on failure
     */
    @Operation(
            summary = "Mint / Register / Reserve a DOI",
            description = "Mint / Register / Reserve a DOI. Required scopes: 'doi/write'.",
            method = "POST",
            parameters = [
                    @Parameter(name = "Authorization", in = HEADER, required = true)
            ],
            requestBody = @RequestBody(
                    description = """
JSON request body. The metadata for the mint request, may include a fileUrl that this service will fetch and use as the file for the DOI. Provider metadata is provider specific.
<pre>
{
    provider: "ANDS", // the doi provider to use; DATACITE or ANDS
    applicationUrl: "http://....", // the url to the relevant page on the source application. This is NOT the landing page: it is used to provide a link ON the landing page back to the original source of the publication/data/etc for the DOI.
    providerMetadata: { // the provider-specific metadata to be sent with the DOI minting request
        ...
    },
    title: "...", // title to be displayed on the landing page
    authors: "...", // author(s) to be displayed on the landing page
    description: "...", // description to be displayed on the landing page
    // the following are optional
    fileUrl: "http://....", // the url to use to download the file for the DOI (use this, or send the file as a multipart request)
    customLandingPageUrl: "http://...", // an application-specific landing page that you want the DOI to resolve to. If not provided, the default ALA-DOI landing page will be used.
    applicationMetadata: { // any application-specific metadata you want to display on the landing page in ALA-DOI
        ...
    }
}
</pre>

You can use the template below to populate the ANDS providerMetadata:
<pre>
{
    "authors" : [
        "{Author}"
    ],
    "contributors" : [{
            "name" : "{Contributor}",
            "type" : "{Editor|etc}"
        }
    ],
    "title" : "{Title}",
    "subjects" : [
        "{Subjects}"
    ],
    "subtitle" : "{Subtitle}",
    "publicationYear" : {Year},
    "createdDate" : "YYYY-MM-ddThh:mm:ssZ",
    "descriptions" : [{
            "text" : "{Description}",
            "type" : "{Other|etc}"
        }
    ],
    "resourceText" : "{Species information|etc}",
    "resourceType" : "{Text|etc}",
    "publisher" : "{Publisher}"
}
</pre>
""",
                    required = true,
                    content = [
                            @Content(
                                    mediaType = 'application/json',
                                    schema = @Schema(implementation = MintRequest)
                            ),
                            @Content(
                                    mediaType = 'multipart/form-data',
                                    schema = @Schema(type = 'object') // TODO Complete this
                            )
                    ]
            ),
            responses = [
                    @ApiResponse(responseCode = "201",
                            description = "Success",
                            links = [
                                    @Link(
                                            name = 'Location',
                                            description = 'URL for minted / registered / reserved DOI',
                                            operationId = 'GetDoi',
                                            parameters = [
                                                    @LinkParameter(name = 'uuid', expression = '$response.header.X-DOI-ID')
                                            ]
                                    )],
                            headers = [
                                    @Header(name = 'Access-Control-Allow-Headers', description = "CORS header", schema = @Schema(type = "string")),
                                    @Header(name = 'Access-Control-Allow-Methods', description = "CORS header", schema = @Schema(type = "string")),
                                    @Header(name = 'Access-Control-Allow-Origin', description = "CORS header", schema = @Schema(type = "string"))
                            ]
                    )
            ],
            security = [
                    @SecurityRequirement(name = "openIdConnect", scopes = "doi/write")
            ],
            tags = ['DOI']
    )
    @Path("/api/doi")
    @Produces("application/json")
    @Consumes("application/json")
    def save() {
        Map json = getJson(request)

        if (validateMintRequest(json)) {
            MultipartFile file = null
            if (request instanceof MultipartHttpServletRequest) {
                file = request.getFile(request.fileNames[0])
            }

            MintResponse result = doiService.mintDoi(DoiProvider.byName(json.provider as String), json.providerMetadata as Map, json.title as String,
                    json.authors as String, json.description as String, json.licence as List<String>, json.applicationUrl as String, json.fileUrl as String, file, json.applicationMetadata as Map,
                    json.customLandingPageUrl as String, null, json.userId as String, json.active as Boolean, json.authorisedRoles as List<String>, json.displayTemplate as String)


            if (result?.uuid) {
                response.addHeader(HttpHeaders.LOCATION,
                        grailsLinkGenerator.link( method: 'GET', resource: this.controllerName, action: 'show',id: result.uuid, absolute: true,
                                namespace: hasProperty('namespace') ? this.namespace : null ))
                response.addHeader('X-DOI-ID', result.uuid)
            }
            render result as JSON, status: SC_CREATED
        }
    }

    private static Map getJson(HttpServletRequest request) {
        Map json = request.getJSON()

        if (!json && request instanceof MultipartHttpServletRequest) {
            json = JSON.parse(request.getParameter("json")) as Map
        }

        json
    }

    private boolean validateMintRequest(Map json) {
        boolean valid = true

        if (areMandatoryMetadataFieldsMissing(json)) {
            log.debug("Rejecting request with missing mandatory parameters. Provided parameters: ${json}")
            badRequest "provider, title, authors, description, applicationUrl and providerMetadata must be provided " +
                    "in the request's JSON body"

            valid = false
        } else if (!DoiProvider.byName(json.provider)) {
            log.debug("Rejecting request with invalid provider ${json.provider}")
            badRequest "invalid provider: must be one of ${DoiProvider.values()*.name().join(", ")}"

            valid = false
        }

        valid
    }

    private static boolean areMandatoryMetadataFieldsMissing(json) {
        !json.provider || !json.applicationUrl || !json.providerMetadata || !json.title || !json.authors || !json.description
    }

    private static boolean areFileAndUrlMissing(json, request) {
        !json.fileUrl && (!(request instanceof MultipartHttpServletRequest) || !request.fileNames)
    }

    @Operation(
            summary = "List DOIs",
            description = "List DOIs",
            method = "GET",
            parameters = [
                    @Parameter(name = "max", in = QUERY, description = 'max number of dois to return', schema = @Schema(implementation = Integer, defaultValue = '10')),
                    @Parameter(name = "offset", in = QUERY, description = 'index of the first record to return', schema = @Schema(implementation = Integer, defaultValue = '0')),
                    @Parameter(name = "sort", in = QUERY, description = 'the field to sort the results by', schema = @Schema(implementation = String, defaultValue = 'dateMinted', allowableValues = ['dateMinted','dateCreated','lastUpdated','title'])),
                    @Parameter(name = "order", in = QUERY, description = 'the direction to sort the results by', schema = @Schema(implementation = String, defaultValue = 'asc', allowableValues = ['asc','desc'])),
                    @Parameter(name = "userId", in = QUERY, description = 'Add a userid filter, userid should be the user\'s numeric user id', schema = @Schema(implementation = String)),
                    @Parameter(name = "activeStatus", in = QUERY, description = 'Filters DOIs returned based on active flag. Valid values are \'all\', \'active\' or \'inactive\'. If omitted it defaults to \'active\'', schema = @Schema(implementation = String)),
            ],
            responses = [
                    @ApiResponse( responseCode = "200",
                            description = "Success",
                            content = [
                                    @Content(
                                            mediaType = 'application/json',
                                            array = @ArraySchema(schema = @Schema(implementation = Doi))
                                    )
                            ],
                            headers = [
                                    @Header(name = 'Access-Control-Allow-Headers', description = "CORS header", schema = @Schema(type = "string")),
                                    @Header(name = 'Access-Control-Allow-Methods', description = "CORS header", schema = @Schema(type = "string")),
                                    @Header(name = 'Access-Control-Allow-Origin', description = "CORS header", schema = @Schema(type = "string")),
                                    @Header(name = 'Link', description = 'Pagination links', schema = @Schema(type = "string")),
                                    @Header(name = 'X-Total-Count', description = 'Total count of search results available', schema = @Schema(type = "string"))
                            ]
                    )
            ],
            tags = ['DOI']
    )
    @Path("/api/doi")
    @Produces("application/json")
    @SkipApiKeyCheck
    def index() {

        Integer max = Math.min(params.max as Integer ?: 10, 100)
        int offset = params.int('offset', 0)
        String sort = params.get('sort', 'dateMinted')
        String order = params.get('order', 'desc')

        String userId = params.get('userId')
        String title = params.get('title')
        String authors = params.get('authors')
        String licence = params.get('licence')
        String activeStatus = params.boolean("activeStatus")


        def eqParams = [:]
        if (userId) eqParams << [ userId : userId ]
        if (title) eqParams << [ title : title ]
        if (authors) eqParams << [ authors : authors ]
        if (licence) eqParams << [ licence : licence ]


        if (activeStatus != null) {
            if(activeStatus == 'inactive') {
                eqParams << [ active : false ]
            } else if(activeStatus == 'all') {
                // just skip the filter completely
            } else {
                // Any other value will force default filter
                eqParams << [ active : true ]
            }
        } else {
            eqParams << [ active : true ]
        }

        def list = doiService.listDois(max, offset, sort, order, eqParams)
        def totalCount = list.totalCount

        addPaginationHeaders(eqParams, totalCount, offset, max, sort, order)

        respond list
    }

    private void addPaginationHeaders(Map nonPaginationParams, int totalCount, int offset, int max, String sort, String order) {
        response.addIntHeader('X-Total-Count', totalCount)
        if (offset + max < totalCount) {
            response.addHeader('Link', createLink(params: nonPaginationParams + [max: max, offset: offset + max, sort: sort, order: order]) + '; rel="next"')
        }
        if (offset > 0) {
            response.addHeader('Link', createLink(params: nonPaginationParams + [max: max, offset: Math.max(0, offset - max), sort: sort, order: order]) + '; rel="prev"')
        }
        response.addHeader('Link', createLink(params: nonPaginationParams + [max: max, offset: 0, sort: sort, order: order]) + '; rel="first"')
        response.addHeader('Link', createLink(params: nonPaginationParams + [max: max, offset: Math.max(0, totalCount - max), sort: sort, order: order]) + '; rel="last"')
    }

    @Operation(
            summary = "Search DOIs",
            description = "Search DOIs",
            method = "GET",
            parameters = [
                    @Parameter(name = "q", in = QUERY, description = 'An elasticsearch Simple Query String formatted string.', schema = @Schema(implementation = String)),
                    @Parameter(name = "max", in = QUERY, description = 'max number of dois to return', schema = @Schema(implementation = Integer, defaultValue = '10')),
                    @Parameter(name = "offset", in = QUERY, description = 'index of the first record to return', schema = @Schema(implementation = Integer, defaultValue = '0')),
                    @Parameter(name = "sort", in = QUERY, description = 'the field to sort the results by', schema = @Schema(implementation = String, defaultValue = 'dateMinted', allowableValues = ['dateMinted','dateCreated','lastUpdated','title'])),
                    @Parameter(name = "order", in = QUERY, description = 'the direction to sort the results by', schema = @Schema(implementation = String, defaultValue = 'asc', allowableValues = ['asc','desc'])),
                    @Parameter(name = "fq", in = QUERY, description = 'filters the search results by by supplied fields.  Each value must be a string of the form fieldName:filterTerm.  To filter on DOI applicationMetadata, use a fieldName of \'applicationMetadata.field\'', array = @ArraySchema(schema = @Schema(implementation = String))),
                    @Parameter(name = "activeStatus", in = QUERY, description = 'Filters DOIs returned based on active flag. Valid values are \'all\', \'active\' or \'inactive\'. If omitted it defaults to \'active\'', schema = @Schema(implementation = String)),
            ],
            responses = [
                    @ApiResponse(
                            responseCode = "200",
                            description = "Success",
                            content = [
                                    @Content(
                                            mediaType = 'application/json',
                                            array = @ArraySchema(schema = @Schema(implementation = DoiElasticSearchResult))
                                    )
                            ],
                            headers = [
                                    @Header(name = 'Access-Control-Allow-Headers', description = "CORS header", schema = @Schema(type = "string")),
                                    @Header(name = 'Access-Control-Allow-Methods', description = "CORS header", schema = @Schema(type = "string")),
                                    @Header(name = 'Access-Control-Allow-Origin', description = "CORS header", schema = @Schema(type = "string")),
                                    @Header(name = 'Link', description = 'Pagination links', schema = @Schema(type = "string")),
                                    @Header(name = 'X-Total-Count', description = 'Total count of search results available', schema = @Schema(type = "string"))
                            ]
                    )
            ],
            tags = ['DOI']
    )
    @Path("/api/doi/search")
    @Produces("application/json")
    @SkipApiKeyCheck
    def search(@Parameter(hidden = true) SearchDoisCommand command) {

        if (!command.validate()) {
            respond command
        }
        else {
            ElasticSearchResult result = doiSearchService.searchDois(command.max, command.offset, command.q, command.filters, command.sort, command.order)
            def totalCount = result.total.value as int

            Map queryParams = [q:command.q, fq:command.fq]
            addPaginationHeaders(queryParams, totalCount, command.offset, command.max, command.sort, command.order)

            // Use DoiElasticSearchResult to keep consistent response format
            DoiElasticSearchResult doiResult = new DoiElasticSearchResult()
            doiResult.total= result.total.value
            doiResult.totalRel = result.total.relation
            doiResult.searchResults = result.searchResults
            doiResult.highlight = result.highlight
            doiResult.scores = result.scores
            doiResult.sort = result.sort
            doiResult.aggregations = result.aggregations

            respond doiResult
        }
    }


    /**
     * Retrieve the metadata for a doi by either UUID or DOI
     *
     * @param id Either the local UUID or the DOI identifier
     * @return JSON response containing the metadata for the requested doi
     */
    @Operation(
            summary = "Get a stored DOI and its metadata",
            method = "GET",
            description = "Get a stored DOI and its metadata",
            parameters = [
                    @Parameter(name = "id", in = PATH, required = true, description = 'Either the DOI (encoded or unencoded) or the UUID', schema = @Schema(implementation = String)),
            ],
            responses = [
                    @ApiResponse( responseCode = "200",
                            description = "Success",
                            content = [
                                    @Content(
                                            mediaType = 'application/json',
                                            schema = @Schema(implementation = Doi)
                                    )
                            ],
                            headers = [
                                    @Header(name = 'Access-Control-Allow-Headers', description = "CORS header", schema = @Schema(type = "string")),
                                    @Header(name = 'Access-Control-Allow-Methods', description = "CORS header", schema = @Schema(type = "string")),
                                    @Header(name = 'Access-Control-Allow-Origin', description = "CORS header", schema = @Schema(type = "string"))
                            ]
                    ),
                    @ApiResponse(responseCode = '404', description = 'DOI or UUID not found in this system')
            ],
            tags = ['DOI']
    )
    @Path("/api/doi/{id}")
    @Produces("application/json")
    @SkipApiKeyCheck
    def show() {
        if(params.id == null) {
            badRequest "id is null"
        }
        String id = params.id
        Doi doi = queryForResource(id)

        if (!doi) {
            notFound "No doi was found for ${params.id}"
        } else {
            render doi as JSON
        }
    }

    /**
     * Retrieve the file for a doi by either UUID or DOI
     *
     * @param id Either the local UUID or the DOI identifier
     * @return the file associated with the DOI
     */
    @Operation(
            summary = "Download the file associated with a DOI",
            method = "GET",
            description = "Download the file associated with a DOI",
            parameters = [
                    @Parameter(name = "id", in = PATH, required = true, description = 'Either the DOI (encoded or unencoded) or the UUID', schema = @Schema(implementation = String)),
            ],
            responses = [
                    @ApiResponse(responseCode = "200",
                            description = "Success",
                            content = [
                                    @Content(
                                            mediaType = 'application/octet-stream',
                                            schema = @Schema(type = 'string', format = 'binary')
                                    )
                            ],
                            headers = [
                                    @Header(name = 'Access-Control-Allow-Headers', description = "CORS header", schema = @Schema(type = "string")),
                                    @Header(name = 'Access-Control-Allow-Methods', description = "CORS header", schema = @Schema(type = "string")),
                                    @Header(name = 'Access-Control-Allow-Origin', description = "CORS header", schema = @Schema(type = "string"))
                            ]
                    ),
                    @ApiResponse(responseCode = '404', description = 'DOI or UUID not found in this system'),
                    @ApiResponse(responseCode = '302', description = 'Redirect to file')
            ],
            tags = ['DOI']
    )
    @Path("/api/doi/{id}/download")
    @Produces("application/octet-stream")
    @SkipApiKeyCheck
    def download() {
        if(params.id == null) {
            badRequest "id is null"
        }
        String id = params.id
        Doi doi = queryForResource(id)

        if (!doi) {
            notFound "No doi was found for ${id}"
        } else if(doi.authorisedRoles) {
            response.addHeader('Link', createLink(uri: "/doi/${doi.uuid}/download") + '; rel="alternate"')
            notAuthorised "Sensitive data files can only be downloaded via DOI Service GUI for authenticated users only"
        } else {
            if (storage instanceof S3Storage) {
                String url = storage.generatePresignedURL(doi, grailsApplication.config.getProperty('s3.temporaryurl.duration', Integer.class))
                if (url) {
                    response.status = 302
                    response.addHeader('Location', url)
                } else {
                    notFound "No file was found for DOI ${doi.doi} (uuid = ${doi.uuid})"
                }
            } else {
                ByteSource byteSource = storage.getFileForDoi(doi)
                if (byteSource) {
                    response.setContentType(doi.contentType)
                    response.setHeader("Content-disposition", "attachment;filename=${doi.filename}")
                    byteSource.openStream().withStream {
                        response.outputStream << it
                    }
                    response.outputStream.flush()
                } else {
                    notFound "No file was found for DOI ${doi.doi} (uuid = ${doi.uuid})"
                }
            }
        }
    }

    @Operation(
            summary = "Update the stored metadata or add a file to a DOI",
            method = "PATCH",
            description = "Update the stored metadata or add a file to a DOI. Required scopes: 'doi/write'.",
            parameters = [
                    @Parameter(name = "id", in = PATH, required = true, description = 'Either the DOI (encoded or unencoded) or the UUID', schema = @Schema(implementation = String)),
                    @Parameter(name = "Authorization", in = HEADER, required = true)
            ],
            requestBody = @RequestBody(
                    description = "The values to update the DOI with.  This will patch the existing DOI object with the provided values.  Only the following values are accepted: 'providerMetadata', 'customLandingPageUrl', 'title', 'authors', 'description', 'licence', 'applicationUrl','applicationMetadata'",
                    required = true,
                    content = [
                            @Content(mediaType = 'application/json', schema = @Schema(implementation = UpdateRequest))
                    ]
            ),

            responses = [
                    @ApiResponse(responseCode = "200",
                            description = "Success",
                            content = [@Content(mediaType = 'application/json', schema = @Schema(implementation = Doi))],
                            headers = [
                                    @Header(name = 'Access-Control-Allow-Headers', description = "CORS header", schema = @Schema(type = "string")),
                                    @Header(name = 'Access-Control-Allow-Methods', description = "CORS header", schema = @Schema(type = "string")),
                                    @Header(name = 'Access-Control-Allow-Origin', description = "CORS header", schema = @Schema(type = "string"))
                            ]
                    ),
                    @ApiResponse(responseCode = '400', description = 'Attempting to update the file when there is already an existing file'),
                    @ApiResponse(responseCode = '404', description = 'DOI or UUID not found in this system'),
                    @ApiResponse(responseCode = '422', description = 'If the request body creates an invalid DOI entry'),
                    @ApiResponse(responseCode = '500', description = 'There is an error while storing the file or contacting the DOI service'),
            ],
            security = [
                    @SecurityRequirement(name = "openIdConnect", scopes = "doi/write")
            ],
            tags = ['DOI']
    )
    @Path("/api/doi/{id}")
    @Produces("application/json")
    @Consumes("application/json")
    def patch(@NotNull String id) {
        update(id)
    }

    @Operation(
            summary = "Update the stored metadata or add a file to a DOI",
            method = "PUT",
            description = "Update the stored metadata or add a file to a DOI. Required scopes: 'doi/write'.",
            parameters = [
                    @Parameter(name = "id", in = PATH, required = true, description = 'Either the DOI (encoded or unencoded) or the UUID', schema = @Schema(implementation = String)),
                    @Parameter(name = "Authorization", in = HEADER, required = true)
            ],
            requestBody = @RequestBody(
                    description = "The values to update the DOI with.  This will patch the existing DOI object with the provided values.  Only the following values are accepted: 'providerMetadata', 'customLandingPageUrl', 'title', 'authors', 'description', 'licence', 'applicationUrl','applicationMetadata'",
                    required = true,
                    content = [
                            @Content(mediaType = 'application/json', schema = @Schema(implementation = UpdateRequest))
                    ]
            ),
            responses = [
                    @ApiResponse(responseCode = "200",
                            description = "Success",
                            content = [@Content(mediaType = 'application/json', schema = @Schema(implementation = Doi))],
                            headers = [
                                    @Header(name = 'Access-Control-Allow-Headers', description = "CORS header", schema = @Schema(type = "string")),
                                    @Header(name = 'Access-Control-Allow-Methods', description = "CORS header", schema = @Schema(type = "string")),
                                    @Header(name = 'Access-Control-Allow-Origin', description = "CORS header", schema = @Schema(type = "string"))
                            ]
                    ),
                    @ApiResponse(responseCode = '400', description = 'Attempting to update the file when there is already an existing file'),
                    @ApiResponse(responseCode = '404', description = 'DOI or UUID not found in this system'),
                    @ApiResponse(responseCode = '422', description = 'If the request body creates an invalid DOI entry'),
                    @ApiResponse(responseCode = '500', description = 'There is an error while storing the file or contacting the DOI service'),
            ],
            security = [
                    @SecurityRequirement(name = "openIdConnect", scopes = "doi/write")
            ],
            tags = ['DOI']
    )
    @Path("/api/doi/{id}")
    @Produces("application/json")
    @Consumes("application/json")
    def update(@NotNull String id) {

        def objectToBind = getJson(request)
        MultipartFile file = null
        if (request instanceof MultipartHttpServletRequest) {
            file = request.getFile(request.fileNames[0])
        }

        Doi instance
        try {
            instance = doiService.updateDoi(id, objectToBind, file)
        } catch (DoiNotFoundException e) {
            notFound()
            return
        } catch (DoiUpdateException e) {
            badRequest(e.message)
            return
        } catch (DoiValidationException e) {
            unprocessableEntity()
            return
        }

        response.addHeader(HttpHeaders.LOCATION,
                grailsLinkGenerator.link( resource: this.controllerName, action: 'show',id: id, absolute: true,
                        namespace: hasProperty('namespace') ? this.namespace : null ))
        respond instance, [status: SC_OK]
    }

    /** Dummy method for file upload update for Swagger  */
    @Operation(
            summary = "Update the stored metadata or add a file to a DOI",
            method = "POST",
            description = "Update the stored metadata or add a file to a DOI. Required scopes: 'doi/write'.",
            parameters = [
                    @Parameter(name = "id", in = PATH, required = true, description = 'Either the DOI (encoded or unencoded) or the UUID', schema = @Schema(implementation = String)),
                    @Parameter(name = "Authorization", in = HEADER, required = true)
            ],
            requestBody = @RequestBody(
                    description = "The values to update the DOI with.  This will patch the existing DOI object with the provided values.  Only the following values are accepted: 'providerMetadata', 'customLandingPageUrl', 'title', 'authors', 'description', 'licence', 'applicationUrl','applicationMetadata'",
                    required = true,
                    content = [
                            @Content(mediaType = 'application/json', schema = @Schema(implementation = UpdateRequest)),
                            @Content(mediaType = 'application/octet-stream', schema = @Schema(name='file', title='The file to upload', type='string', format='binary'))
                    ]
            ),
            responses = [
                    @ApiResponse(responseCode = "200",
                            description = "Success",
                            content = [@Content(mediaType = 'application/json', schema = @Schema(implementation = Doi))],
                            headers = [
                                    @Header(name = 'Access-Control-Allow-Headers', description = "CORS header", schema = @Schema(type = "string")),
                                    @Header(name = 'Access-Control-Allow-Methods', description = "CORS header", schema = @Schema(type = "string")),
                                    @Header(name = 'Access-Control-Allow-Origin', description = "CORS header", schema = @Schema(type = "string"))
                            ]
                    ),
                    @ApiResponse(responseCode = '400', description = 'Attempting to update the file when there is already an existing file'),
                    @ApiResponse(responseCode = '404', description = 'DOI or UUID not found in this system'),
                    @ApiResponse(responseCode = '422', description = 'If the request body creates an invalid DOI entry'),
                    @ApiResponse(responseCode = '500', description = 'There is an error while storing the file or contacting the DOI service'),
            ],
            security = [
                    @SecurityRequirement(name = "openIdConnect", scopes = "doi/write")
            ],
            tags = ['DOI']
    )
    @Path("/api/doi/{id}")
    @Produces("application/json")
    @Consumes("multipart/form-data")
    def updateUpload() {

    }

    protected Doi queryForResource(Serializable id) {
        String idString = id instanceof String ? id : id.toString()
        isUuid(idString) ? doiService.findByUuid(idString) : doiService.findByDoi(idString)
    }

    // classes used for the OpenAPI definition generator
    @JsonIgnoreProperties('metaClass')
    static class UpdateRequest {
        Map providerMetadata
        String customLandingPageUrl
        String title
        String authors
        String description
        String applicationUrl
        Map applicationMetadata
        String fileUrl
    }

    @JsonIgnoreProperties('metaClass')
    static class DoiElasticSearchResult {
        Long total
        String totalRel
        List searchResults = []
        List<Map<String, HighlightField>> highlight = []
        Map<String, Float> scores = [:]
        Map<String, Object[]> sort = [:]
        Map<String, Aggregation> aggregations = [:]
    }

    @JsonIgnoreProperties('metaClass')
    static class MintRequest {
        DoiProvider provider
        Map providerMetadata
        String title
        String authors
        String description
        String licence
        String applicationUrl
        String fileUrl
        Map applicationMetadata
        String customLandingPageUrl
        String userId

    }

}
