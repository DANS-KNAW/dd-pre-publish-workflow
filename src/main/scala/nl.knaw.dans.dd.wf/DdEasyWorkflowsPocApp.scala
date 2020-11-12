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
  val mapper = new DansDataVaultMetadataBlockMapper
  private val resumeTasks = new ActiveTaskQueue()

  def start(): Unit = {
    resumeTasks.start()
  }

  def stop(): Unit = {
    resumeTasks.stop()
  }


  def doWorkFlow(workFlowVariables: WorkFlowVariables): Try[Unit] = {
    val vaultFields = mapper.populateDataVaultBlock()
    debug("Trying to update metadata...")
    val result = editMetadata(workFlowVariables.pid, Serialization.writePretty(vaultFields))
    debug(s"result = $result")
    resumeTasks.add(ResumeTask(workFlowVariables))
    debug("workflow finished")
    Success()
  }

  def parseDatasetVersion(metadataJson: JValue): DatasetVersion = {
    val metadataBlocks = (metadataJson \\ "metadataBlocks").extract[Map[String, MetadataBlock]]
//    val citation = metadataBlocks("citation")
//    val contact = citation \ "fields"
    DatasetVersion(metadataBlocks)
  }

  def addBlockToMetadata(datasetVersion: DatasetVersion, dansVaultMetadata: MetadataBlock): DatasetVersion = {
    val updatedDatasetVersion = datasetVersion.copy(metadataBlocks = datasetVersion.metadataBlocks + ("dansDataVaultMetadata" -> dansVaultMetadata))
    updatedDatasetVersion
  }
}
