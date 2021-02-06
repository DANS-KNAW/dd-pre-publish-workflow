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

import nl.knaw.dans.lib.dataverse.model.dataset.{ MetadataBlock, PrimitiveSingleValueField }
import nl.knaw.dans.lib.dataverse.{ DataverseInstance, Version }
import nl.knaw.dans.lib.logging.DebugEnhancedLogging
import nl.knaw.dans.lib.taskqueue.Task
import org.json4s.JsonAST.JObject
import org.json4s.jackson.{ JsonMethods, Serialization }

import scala.util.Try
import scala.util.control.NonFatal

class SetVaultMetadataTask(workFlowVariables: WorkFlowVariables, dataverse: DataverseInstance, mapper: DansDataVaultMetadataBlockMapper) extends Task[WorkFlowVariables] with DebugEnhancedLogging {
  private val maxAttempts = 10 // TODO: make configurable
  private val dataset = dataverse.dataset(workFlowVariables.globalId, Option(workFlowVariables.invocationId))

  override def run(): Try[Unit] = {
    (for {
      _ <- waitUntilWorkflowLockIsSet()
      _ <- editVaultMetadata()
      _ <- dataverse.workflows().resume(workFlowVariables.invocationId)
      _ = logger.info(s"Vault metadata set for dataset ${workFlowVariables.globalId}. Dataset resume called.")
    } yield ())
      .recover {
        case NonFatal(e) =>
          logger.error(s"SetVaultMetadataTask for dataset ${workFlowVariables.globalId} failed. Resuming dataset with 'fail=true'", e)
          dataverse.workflows().resume(workFlowVariables.invocationId, fail = true)
      }
  }

  private def waitUntilWorkflowLockIsSet(): Try [Unit] = {
    trace(())
    var tryIsLockSet = isWorkflowLockSet
    debug(s"tryIsLockSet = $tryIsLockSet")
    var attempts = 0

    while(tryIsLockSet.isSuccess && !tryIsLockSet.get && attempts < maxAttempts) {
      logger.info(s"Workflow not yet set for dataset ${workFlowVariables.datasetId}")
      attempts += 1
      Thread.sleep(1000) // TODO: make configurable
      tryIsLockSet = isWorkflowLockSet
    }
    tryIsLockSet.map(_ => ())
  }

  private def isWorkflowLockSet: Try[Boolean] = {
    trace(())
    for {
      r <- dataset.getLocks
      locks <- r.data
    } yield locks.exists(_.lockType == "Workflow")
  }

  private def editVaultMetadata(): Try[Unit] = {
    trace(())
    for {
      response <- dataset.view(Version.DRAFT)
      metadata <- response.string
      _ = if (logger.underlying.isDebugEnabled) debug(s"Found metadata ${ response.string }")
      vaultBlockOpt <- getVaultBlockOpt(metadata)
      _ = if (logger.underlying.isDebugEnabled) debug(s"vaultBlockOpt = $vaultBlockOpt")
      vaultFields <- {
        val bagId = getVaultFieldValue(vaultBlockOpt, "dansBagId")
        val urn = getVaultFieldValue(vaultBlockOpt, "dansNbn")
        val otherId = getVaultFieldValue(vaultBlockOpt, "dansOtherId")
        val otherIdVersion = getVaultFieldValue(vaultBlockOpt, "dansOtherIdVersion")
        val swordToken = getVaultFieldValue(vaultBlockOpt, "dansSwordToken")
        mapper.createDataVaultFields(workFlowVariables, bagId, urn, otherId, otherIdVersion, swordToken)
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
