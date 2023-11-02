package au.org.ala.doi.storage

import au.org.ala.doi.Doi
import com.amazonaws.services.s3.AmazonS3
import com.amazonaws.services.s3.Headers
import com.amazonaws.services.s3.model.CannedAccessControlList
import com.amazonaws.services.s3.model.GeneratePresignedUrlRequest
import com.amazonaws.services.s3.model.ObjectMetadata
import com.google.common.hash.Hashing
import com.google.common.hash.HashingInputStream
import com.google.common.io.ByteSource
import com.google.common.io.ByteStreams
import grails.plugin.awssdk.s3.AmazonS3Service
import org.apache.commons.lang.time.DateUtils
import org.springframework.web.multipart.MultipartFile

import static au.org.ala.doi.util.StateAssertions.checkArgument

class S3Storage extends BaseStorage {

    AmazonS3Service amazonS3Service
    private CannedAccessControlList acl

    S3Storage(AmazonS3Service amazonS3Service, CannedAccessControlList acl) {
        this.amazonS3Service = amazonS3Service
        this.acl = acl
    }

    @Override
    ByteSource getFileForDoi(Doi doi) {

        def key = keyForDoi(doi)
        def exists = amazonS3Service.exists(key)
        if (exists) {
            def client = amazonS3Service.getClient()
            return new S3ByteSource(bucket: amazonS3Service.defaultBucketName, key: key, client: client)
        }
        return null
    }

    @Override
    void storeFileForDoi(Doi doi, MultipartFile incoming) {
        storeMultipartFile(doi, incoming) {
            def key = keyForDoi(doi)
            def url = amazonS3Service.storeMultipartFile(key, incoming, acl)
            if (!url) {
                throw new IOException("Couldn't store $incoming.originalFilename in S3")
            }
        }
    }

    @Override
    String getDescription() {
        return "Amazon S3 storage at ${amazonS3Service.client.endpoint ?: amazonS3Service.client.regionName ?: 'default S3'}, bucket: ${amazonS3Service.defaultBucketName}"
    }

    @Override
    void transferInputStream(Doi doi, InputStream input) {
        String key = "${doi.uuid}/${doi.filename}"
        ObjectMetadata om = new ObjectMetadata()
        om.contentType = doi.contentType
        if (doi.fileSize) {
            om.contentLength = doi.fileSize
        }
        om.setHeader(Headers.S3_CANNED_ACL, acl.toString())

        def s3Url = amazonS3Service.storeInputStream(key, input, om)
        if (!s3Url) {
            throw new IOException("Couldn't store ${doi.uuid} in S3")
        }
    }

    static String keyForDoi(Doi doi) {
        "${doi.uuid}/${doi.filename}"
    }

    String generatePresignedURL(Doi doi, Integer duration) {
        def key = keyForDoi(doi)
        def exists = amazonS3Service.exists(key)
        if (exists) {
            amazonS3Service.generatePresignedUrl(amazonS3Service.defaultBucketName, key, DateUtils.addHours(new Date(), duration))
        } else {
            null
        }
    }
}

