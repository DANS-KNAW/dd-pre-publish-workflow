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

import java.nio.charset.StandardCharsets

import nl.knaw.dans.dd.prepub.dataverse.DataverseInstance
import nl.knaw.dans.dd.prepub.queue.Task
import nl.knaw.dans.lib.logging.DebugEnhancedLogging
import org.json4s.Formats
import org.json4s.native.JsonMethods

import scala.util.Try

case class ResumeTask(workFlowVariables: WorkFlowVariables, dataverse: DataverseInstance)(implicit jsonFormats: Formats) extends Task with DebugEnhancedLogging {


  override def run(): Try[Unit] = Try {
    trace(())
    var isLocked = false

    while (!isLocked) {
      debug("Requesting lock-status...")
      val result = for {
        response <- dataverse.dataset(workFlowVariables.datasetId, isPersistentId = true).getLocks(Some("Workflow"))
        lockStatus <- Try { JsonMethods.parse(new String(response.body, StandardCharsets.UTF_8)).extract[LockStatusMessage] }
      } yield lockStatus.data.nonEmpty
      debug(s"result = $result")
      isLocked = result.getOrElse(false)
      Thread.sleep(1000)
    }
    debug("Trying to unlock...")
    dataverse.workflows().resume(workFlowVariables.invocationId)
  }
}
