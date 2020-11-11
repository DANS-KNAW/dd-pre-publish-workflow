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

import nl.knaw.dans.dd.wf.json.{ DatasetVersion, MetadataBlock }
import nl.knaw.dans.dd.wf.queue.ActiveTaskQueue
import org.json4s.JsonAST.JValue
import org.json4s.jackson.{ JsonMethods, Serialization }

import scala.util.{ Success, Try }

class DdEasyWorkflowsPocApp(configuration: Configuration) extends Http {

  private val resumeTasks = new ActiveTaskQueue()
  val mapper = new DansDataVaultMetadataBlockMapper

  def doWorkFlow(workFlowVariables: WorkFlowVariables): Try[Unit] = {

    //todo use Try in for-comprehension

    // for {
    val metadata = getMetadata(workFlowVariables.pid)
    val metadata2Json = JsonMethods.parse(metadata)
    val datasetVersion = parseDatasetVersion(metadata2Json)
    val vaultBlock = mapper.populateDataVaultBlock(datasetVersion, workFlowVariables)
    val datasetUpdated = addBlockToMetadata(datasetVersion, vaultBlock)
    val updatedJsonString = Serialization.writePretty(datasetUpdated)
    updateMetadata(workFlowVariables.pid, updatedJsonString)
    //    //} yield ()
    //resume request to be executed in a different thread
    resumeTasks.add(ResumeTask(workFlowVariables.invocationId))
    resumeTasks.start()

    Success()
  }

  def parseDatasetVersion(metadataJson: JValue): DatasetVersion = {
    val metadataBlock = (metadataJson \\ "metadataBlocks").extract[Map[String, MetadataBlock]]
    DatasetVersion(metadataBlock)
  }

  def addBlockToMetadata(datasetVersion: DatasetVersion, dansVaultMetadata: MetadataBlock): DatasetVersion = {
    val updatedDatasetVersion = datasetVersion.copy(metadataBlocks = datasetVersion.metadataBlocks + ("dansDataVaultMetadata" -> dansVaultMetadata))
    updatedDatasetVersion
  }
}
