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

import nl.knaw.dans.lib.dataverse.model.ResumeMessage
import nl.knaw.dans.lib.dataverse.model.dataset.{ MetadataBlock, PrimitiveSingleValueField }
import nl.knaw.dans.lib.dataverse.{ DataverseInstance, Version }
import nl.knaw.dans.lib.logging.DebugEnhancedLogging
import nl.knaw.dans.lib.taskqueue.Task
import org.json4s.JsonAST.JObject
import org.json4s.jackson.{ JsonMethods, Serialization }

import scala.util.Try
import scala.util.control.NonFatal

class SetVaultMetadataTask(workFlowVariables: WorkFlowVariables, dataverse: DataverseInstance, mapper: DansDataVaultMetadataBlockMapper) extends Task[WorkFlowVariables] with DebugEnhancedLogging {
  private val dataset = dataverse.dataset(workFlowVariables.globalId, Option(workFlowVariables.invocationId))

  override def run(): Try[Unit] = {
    (for {
      _ <- dataset.awaitLock(lockType = "Workflow")
      _ <- editVaultMetadata()
      _ = Thread.sleep(5000)
      _ <- dataverse.workflows().resume(workFlowVariables.invocationId, ResumeMessage(Status = "Success", Message = "", Reason = ""))
      _ = logger.info(s"Vault metadata set for dataset ${workFlowVariables.globalId}. Dataset resume called.")
    } yield ())
      .recover {
        case NonFatal(e) =>
          logger.error(s"SetVaultMetadataTask for dataset ${workFlowVariables.globalId} failed. Resuming dataset with 'fail=true'", e)
          dataverse.workflows().resume(workFlowVariables.invocationId, ResumeMessage(Status = "Failure", Message = "", Reason = s"${e.getMessage}"))
      }
  }

  private def editVaultMetadata(): Try[Unit] = {
    trace(())
    for {
      response <- dataset.view(Version.DRAFT)
      metadata <- response.string
      vaultBlockOpt <- getVaultBlockOpt(metadata)
      _ = if (logger.underlying.isDebugEnabled) debug(s"vaultBlockOpt = $vaultBlockOpt")
      vaultFields <- {
        val bagId = getVaultFieldValue(vaultBlockOpt, "dansBagId")
        val urn = getVaultFieldValue(vaultBlockOpt, "dansNbn")
        mapper.createDataVaultFields(workFlowVariables, bagId, urn)
      }
      _ <- dataset.editMetadata(vaultFields, replace = true)
      _ = debug("editMetadata call returned success. Data Vault Metadata should be added to Dataverse now.")
    } yield ()
  }

  private def getVaultFieldValue(vaultBlockOpt: Option[MetadataBlock], fieldId: String): Option[String] = {
    vaultBlockOpt.flatMap(_.fields.map(_.asInstanceOf[PrimitiveSingleValueField]).find(_.typeName == fieldId)).map(_.value)
  }

  private def getVaultBlockOpt(metadata: String): Try[Option[MetadataBlock]] = Try {
    trace(metadata)
    val vaultBlockJson = JsonMethods.parse(metadata) \\ "dansDataVaultMetadata"
    if (logger.underlying.isDebugEnabled) debug(Serialization.writePretty(vaultBlockJson))
    vaultBlockJson match {
      case JObject(List()) => None
      case v => Option(v.extract[MetadataBlock])
    }
  }

  override def getTarget: WorkFlowVariables = {
    workFlowVariables
  }
}
