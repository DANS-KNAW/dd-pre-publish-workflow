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
import nl.knaw.dans.lib.dataverse.model.dataset.{ DatasetVersion, FieldList, MetadataField, PrimitiveSingleValueField }
import nl.knaw.dans.lib.dataverse.{ DataverseException, DataverseInstance, DataverseResponse, Version }
import nl.knaw.dans.lib.error.TryExtensions
import nl.knaw.dans.lib.logging.DebugEnhancedLogging
import nl.knaw.dans.lib.taskqueue.Task

import java.lang.Thread._
import java.net.HttpURLConnection._
import java.util.UUID
import scala.collection.mutable.ListBuffer
import scala.util.control.NonFatal
import scala.util.{ Failure, Success, Try }

class SetVaultMetadataTask(workFlowVariables: WorkFlowVariables, dataverse: DataverseInstance, nbnPrefix: String, maxNumberOfRetries: Int, timeBetweenRetries: Int) extends Task[WorkFlowVariables] with DebugEnhancedLogging {
  require(nbnPrefix != null)
  private val dataset = dataverse.dataset(workFlowVariables.globalId, Option(workFlowVariables.invocationId))

  override def run(): Try[Unit] = {
    (for {
      _ <- dataset.awaitLock(lockType = "Workflow")
      _ <- editVaultMetadata()
      _ <- resumeWorkflow(workFlowVariables.invocationId)
      _ = logger.info(s"Vault metadata set for dataset ${ workFlowVariables.globalId }. Dataset resume called.")
    } yield ())
      .recover {
        case NonFatal(e) =>
          logger.error(s"SetVaultMetadataTask for dataset ${ workFlowVariables.globalId } failed. Resuming dataset with 'fail=true'", e)
          dataverse.workflows().resume(workFlowVariables.invocationId, ResumeMessage(Status = "Failure", Message = "Publication failed: pre-publication workflow returned an error", Reason = s"${ e.getMessage }"))
      }
  }

  override def getTarget: WorkFlowVariables = {
    workFlowVariables
  }

  private def editVaultMetadata(): Try[Unit] = {
    trace(())
    for {
      draftDsv <- getDatasetVersion(Version.DRAFT)
      optLatestPublishedDsv <- if (hasLatestPublishedVersion(workFlowVariables)) getDatasetVersion(Version.LATEST_PUBLISHED).map(Option(_))
                               else Success(None)
      bagId = getBagId(getVaultMetadataFieldValue(draftDsv, "dansBagId"), optLatestPublishedDsv, workFlowVariables)
      nbn = optLatestPublishedDsv
        .map(pdsv => getVaultMetadataFieldValue(pdsv, "dansNbn")
          .getOrElse(throw new IllegalStateException("Found published dataset-version without NBN")))
        .getOrElse(getVaultMetadataFieldValue(draftDsv, "dansNbn")
          .getOrElse(mintUrnNbn()))
      vaultFieldsToUpdate = createFieldList(workFlowVariables, bagId, nbn)
      _ <- dataset.editMetadata(vaultFieldsToUpdate, replace = true)
      _ = debug("editMetadata call returned success. Data Vault Metadata should be added to Dataverse now.")
    } yield ()
  }

  private def getDatasetVersion(version: Version): Try[DatasetVersion] = {
    for {
      response <- dataset.view(version)
      dsv <- response.data
    } yield dsv
  }

  private def getBagId(optFoundBagId: Option[String], optLatestPublishedDatasetVersion: Option[DatasetVersion], w: WorkFlowVariables): String = {
    trace(optFoundBagId, w)
    optLatestPublishedDatasetVersion.map {
      latestPublishedDsv => { // Draft of version > 1.0
        val latestPublishedBagId = getVaultMetadataFieldValue(latestPublishedDsv, "dansBagId")
          .getOrElse(throw new IllegalArgumentException("Dataset with a latest published version without bag ID found!"))
        if (optFoundBagId.isEmpty || latestPublishedBagId == optFoundBagId.get) {
          /*
           * This happens after publishing a new version via the UI. The bagId from the previous version is inherited by the new draft. However, we
           * want every version to have a unique bagId.
           */
          mintBagId()
        }
        else {
          /*
           * Provided by machine deposit.
           */
          optFoundBagId.get
        }
      }
    }.getOrElse { // Draft of version 1.0
      optFoundBagId.getOrElse(mintBagId())
    }
  }

  private def getVaultMetadataFieldValue(dsv: DatasetVersion, fieldId: String): Option[String] = {
    dsv.metadataBlocks.get("dansDataVaultMetadata")
      .flatMap(_.fields
        .map(_.asInstanceOf[PrimitiveSingleValueField])
        .find(_.typeName == fieldId))
      .map(_.value)
  }

  private def hasLatestPublishedVersion(w: WorkFlowVariables): Boolean = {
    s"${ w.majorVersion }.${ w.minorVersion }" != "1.0"
  }

  private def createFieldList(workFlowVariables: WorkFlowVariables,
                              bagId: String,
                              nbn: String,
                             ): FieldList = {
    trace(workFlowVariables, bagId, nbn)
    val fields = ListBuffer[MetadataField]()
    fields.append(PrimitiveSingleValueField("dansDataversePid", workFlowVariables.globalId))
    fields.append(PrimitiveSingleValueField("dansDataversePidVersion", s"${ workFlowVariables.majorVersion }.${ workFlowVariables.minorVersion }"))
    fields.append(PrimitiveSingleValueField("dansBagId", bagId))
    fields.append(PrimitiveSingleValueField("dansNbn", nbn))
    FieldList(fields.toList)
  }

  private def mintUrnNbn(): String = {
    trace(())
    "urn:nbn:" + nbnPrefix + UUID.randomUUID().toString
  }

  private def mintBagId(): String = {
    trace(())
    "urn:uuid:" + UUID.randomUUID().toString
  }

  private def resumeWorkflow(invocationId: String): Try[Unit] = {
    trace(maxNumberOfRetries, timeBetweenRetries)
    var numberOfTimesTried = 0
    var invocationIdNotFound = true

    do {
      val resumeResponse = dataverse.workflows().resume(invocationId, ResumeMessage(Status = "Success", Message = "", Reason = ""))
      invocationIdNotFound = checkForInvocationIdNotFoundError(resumeResponse, invocationId).unsafeGetOrThrow

      if (invocationIdNotFound) {
        debug(s"Sleeping $timeBetweenRetries ms before next try..")
        sleep(timeBetweenRetries)
        numberOfTimesTried += 1
      }
    } while (numberOfTimesTried <= maxNumberOfRetries && invocationIdNotFound)

    if (invocationIdNotFound) {
      logger.error(s"Workflow could not be resumed for dataset ${ workFlowVariables.globalId }. Number of retries: $maxNumberOfRetries. Time between retries: $timeBetweenRetries")
      Failure(InvocationIdNotFoundException(maxNumberOfRetries, timeBetweenRetries))
    }
    else Success(())
  }

  private def checkForInvocationIdNotFoundError(resumeResponse: Try[DataverseResponse[Nothing]], invocationId: String): Try[Boolean] = {
    resumeResponse.map(_.httpResponse.isError)
      .recover { case e: DataverseException if e.status == HTTP_NOT_FOUND => true }
      .recoverWith { case e: Throwable => Failure(ExternalSystemCallException(s"Resume could not be called for dataset: $invocationId ", e)) }
  }
}
