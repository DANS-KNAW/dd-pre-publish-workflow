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
package nl.knaw.dans.dd.prepub

import java.io.PrintStream

import nl.knaw.dans.dd.prepub.dataverse.DataverseInstance
import nl.knaw.dans.dd.prepub.json.{ DatasetVersion, MetadataBlock }
import nl.knaw.dans.dd.prepub.queue.ActiveTaskQueue
import nl.knaw.dans.lib.logging.DebugEnhancedLogging
import org.json4s.{ DefaultFormats, Formats }
import org.json4s.JsonAST.JValue
import org.json4s.jackson.{ JsonMethods, Serialization }

import scala.util.{ Success, Try }

class PrePublishWorkflowApp(configuration: Configuration) extends DebugEnhancedLogging {
  private implicit val jsonFormats: Formats = DefaultFormats

  // TODO: output should not go to stdout

  private implicit val resultOutput: PrintStream = Console.out
  private val dataverse = new DataverseInstance(configuration.dataverse)

  val mapper = new DansDataVaultMetadataBlockMapper

  def handleWorkflow(workFlowVariables: WorkFlowVariables): Try[Unit] = {
    val vaultFields = mapper.createDataVaultFields()
    debug("Trying to update metadata...")
    dataverse.dataset(workFlowVariables.pid, isPersistentId = true).editMetadata(Serialization.writePretty(vaultFields), replace = true).map(_ => ())
  }
}
