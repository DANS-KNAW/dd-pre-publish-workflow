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

import nl.knaw.dans.dd.wf.queue.ActiveTaskQueue
import org.json4s.JsonAST.JString
import org.json4s.native.{ JsonMethods, Serialization }
import scalaj.http.Http

class DdEasyWorkflowsPocApp(configuration: Configuration) {

  private val resumeTasks = new ActiveTaskQueue()

  def doWorkFlow(invocationId: String, datasetIdentifier: String): Unit = {
    val metadata = getDatasetJson(datasetIdentifier)
    val updatedMetadata = populateDataVaultMetadataBlock(metadata)
    updateMetadata(datasetIdentifier, invocationId, updatedMetadata)

    //resume request to be executed in a different thread
    resumeTasks.add(ResumeTask(invocationId))
    resumeTasks.start()
  }

  def getDatasetJson(datasetIdentifier: String): String = {

    val datasetJson =
      Http(s"${ configuration.baseUrl }/api/datasets/:persistentId/versions/:draft?persistentId=$datasetIdentifier")
        .header("content-type", "application/json")
        .header("accept", "application/json")
        .header("X-Dataverse-key", configuration.apiToken)
        .asString.body

    datasetJson
  }

  def populateDataVaultMetadataBlock(jsonString: String): String = {

    val urnNbn = mintUrnNbn(jsonString)
    var json = JsonMethods.parse(jsonString)

    json = json.replace("data" :: "metadataBlocks" :: "citation" :: "fields[0]" :: "value"
      :: Nil, JString(urnNbn))

    val metadataBlock = json.filterField {
      case ("metadataBlocks", _) => true
      case _ => false
    }.head

    Serialization.writePretty(metadataBlock)
  }

  def mintUrnNbn(jsonString: String): String = {
    "testURN:NBN"
  }

  def updateMetadata(datasetIdentifier: String, invocationId: String, metadata: String): String = {

    val result = Http(s"${ configuration.baseUrl }/api/datasets/:persistentId/versions/:draft?persistentId=$datasetIdentifier")
      .put(metadata)
      .header("content-type", "application/json")
      .header("accept", "application/json")
      .header("X-Dataverse-key", configuration.apiToken)
      .asString.body

    result
  }
}
