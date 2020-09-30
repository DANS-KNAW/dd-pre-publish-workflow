package nl.knaw.dans.dd.wf

import nl.knaw.dans.lib.logging.DebugEnhancedLogging
import scalaj.http.Http

import scala.util.Try

trait Http extends DebugEnhancedLogging {

  protected val connectionTimeout = configuration.connectionTimeout
  protected val readTimeout = configuration.readTimeout
  protected val baseUrl = configuration.baseUrl
  protected val apiToken = configuration.apiToken
  protected val apiVersion = configuration.version

  def getMetadata(datasetIdentifier: String): Try[String] = Try {
    Http(s"${ baseUrl }/api/datasets/:persistentId/versions/:draft?persistentId=$datasetIdentifier")
      .header("content-type", "application/json")
      .header("accept", "application/json")
      .header("X-Dataverse-key", apiToken)
      .asString.body
  }

  def updateMetadata(datasetIdentifier: String, metadata: String): Try[String] = Try {
    val result = Http(s"${ baseUrl }/api/datasets/:persistentId/versions/:draft?persistentId=$datasetIdentifier")
      .put(metadata)
      .header("content-type", "application/json")
      .header("accept", "application/json")
      .header("X-Dataverse-key", apiToken)
      .asString.body

    result
  }

  def resume(invocationId: String): Try[String] = Try {
    val result = Http(s"http://localhost:8080/api/workflows/$invocationId")
      .postData("")
      .header("content-type", "application/json")
      .header("accept", "application/json")
      .header("X-Dataverse-key", "c21f9076-1ede-44e1-b138-d5fd647119ae")
      .asString.body

    result
  }
}
