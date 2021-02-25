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
import nl.knaw.dans.lib.error.TryExtensions
import nl.knaw.dans.lib.logging.DebugEnhancedLogging
import nl.knaw.dans.lib.taskqueue.Task
import org.json4s.JsonAST.JObject
import org.json4s.jackson.{ JsonMethods, Serialization }

import java.lang.Thread._
import java.net.HttpURLConnection._
import scala.util.control.NonFatal
import scala.util.{ Failure, Success, Try }

class SetVaultMetadataTask(workFlowVariables: WorkFlowVariables, dataverse: DataverseInstance, mapper: DansDataVaultMetadataBlockMapper, maxNumberOfRetries: Int, timeBetweenRetries: Int) extends Task[WorkFlowVariables] with DebugEnhancedLogging {
  private val dataset = dataverse.dataset(workFlowVariables.globalId, Option(workFlowVariables.invocationId))

  override def run(): Try[Unit] = {
    (for {
      _ <- dataset.awaitLock(lockType = "Workflow")
      _ <- editVaultMetadata()
      _ <- resumeWorkflow(dataverse, workFlowVariables.invocationId, maxNumberOfRetries, timeBetweenRetries)
      _ = logger.info(s"Vault metadata set for dataset ${ workFlowVariables.globalId }. Dataset resume called.")
    } yield ())
      .recover {
        case NonFatal(e) =>
          logger.error(s"SetVaultMetadataTask for dataset ${workFlowVariables.globalId} failed. Resuming dataset with 'fail=true'", e)
          dataverse.workflows().resume(workFlowVariables.invocationId, fail = true)
      }
  }

  private def resumeWorkflow(dataverse: DataverseInstance, invocationId: String, maxNumberOfRetries: Int, timeBetweenRetries: Int): Try[Unit] = {
    trace(maxNumberOfRetries, timeBetweenRetries)
    var numberOfTimesTried = 0
    var isError = true

    do {
      isError = getResumeResponse(dataverse: DataverseInstance, invocationId: String).unsafeGetOrThrow
      if (isError) {
        debug(s"Sleeping ${ timeBetweenRetries } ms before next try..")
        sleep(timeBetweenRetries)
        numberOfTimesTried += 1
      }
    } while (numberOfTimesTried <= maxNumberOfRetries && isError)

    if (isError) {
      logger.error(s"Workflow could not be resumed. Number of retries: $maxNumberOfRetries. Time between retries: $timeBetweenRetries")
      Failure(WorkflowNotPausedException(maxNumberOfRetries, timeBetweenRetries))
    }
    else {
      Success(())
    }
  }

  private def getResumeResponse(dataverse: DataverseInstance, invocationId: String): Try[Boolean] = {
    dataverse.workflows()
      .resume(invocationId)
      .map(_.httpResponse.code == HTTP_NOT_FOUND)
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
