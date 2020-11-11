/**
 * Copyright (C) 2020 DANS - Data Archiving and Networked Services (info@dans.knaw.nl)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package nl.knaw.dans.dd.wf

import nl.knaw.dans.lib.logging.DebugEnhancedLogging
import scalaj.http.{ Http, HttpOptions }

import scala.util.Try

trait Http extends DebugEnhancedLogging {

  protected val connectionTimeout = configuration.connectionTimeout
  protected val readTimeout = configuration.readTimeout
  protected val baseUrl = configuration.baseUrl
  protected val apiToken = configuration.apiToken
  protected val apiVersion = configuration.version

  def getMetadata(datasetIdentifier: String): String = {
    val result = Http(s"${ baseUrl }/api/datasets/:persistentId/?persistentId=$datasetIdentifier")
      .header("content-type", "application/json")
      .header("accept", "application/json")
      .header("X-Dataverse-key", apiToken)
      //todo: fix unsafe ssl
      .option(HttpOptions.allowUnsafeSSL)
      .asString.body

    result
  }

  def updateMetadata(datasetIdentifier: String, metadata: String): Try[String] = Try {
    debug(s"Update metadata")
    val result = Http(s"${ baseUrl }/api/datasets/:persistentId/versions/:draft?persistentId=$datasetIdentifier")
      .put(metadata)
      .header("content-type", "application/json")
      .header("accept", "application/json")
      .header("X-Dataverse-key", apiToken)
      .option(HttpOptions.allowUnsafeSSL)
      .asString.headers.toSet.toString()

    result
  }

  def resume(invocationId: String): Try[String] = Try {
    val result = Http(s"${ baseUrl }/api/workflows/$invocationId")
      .postData("")
      .header("content-type", "application/json")
      .header("accept", "application/json")
      .header("X-Dataverse-key", apiToken)
      .option(HttpOptions.allowUnsafeSSL)
      .asString.headers.toString()

    result
  }
}