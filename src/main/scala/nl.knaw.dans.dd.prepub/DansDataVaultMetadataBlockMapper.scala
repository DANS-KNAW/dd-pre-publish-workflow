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

import java.net.URI
import java.util.UUID

import nl.knaw.dans.lib.dataverse.model.dataset.{ FieldList, MetadataField, PrimitiveSingleValueField }
import nl.knaw.dans.lib.dataverse.{ DataverseInstance, Version }
import nl.knaw.dans.lib.logging.DebugEnhancedLogging
import scalaj.http.Http

import scala.collection.mutable.ListBuffer
import scala.util.{ Failure, Success, Try }

class DansDataVaultMetadataBlockMapper(pidGeneratorBaseUrl: URI, dataverse: DataverseInstance) extends DebugEnhancedLogging {

  // TODO: Base on info from workflow
  def createDataVaultFields(workFlowVariables: WorkFlowVariables,
                            b: Option[String],
                            n: Option[String],
                            o: Option[String],
                            ov: Option[String],
                            st: Option[String]): Try[FieldList] = {
    for {
      bagId <- setBagId(b, workFlowVariables.pid)
      urn <- n.map(Success(_)).getOrElse(mintUrnNbn())
      fieldList = createFieldList(workFlowVariables, o, ov, st, bagId, urn)
    } yield fieldList
  }

  private def createFieldList(workFlowVariables: WorkFlowVariables,
                              o: Option[String],
                              ov: Option[String],
                              st: Option[String],
                              bagId: String,
                              urn: String,
                             ): FieldList = {

    val fields = ListBuffer[MetadataField]()
    fields.append(PrimitiveSingleValueField("dansDataversePid", workFlowVariables.pid))
    fields.append(PrimitiveSingleValueField("dansDataversePidVersion", s"${ workFlowVariables.majorVersion }.${ workFlowVariables.minorVersion }"))
    fields.append(PrimitiveSingleValueField("dansBagId", bagId))
    fields.append(PrimitiveSingleValueField("dansNbn", urn))
    o.foreach(b => fields.append(PrimitiveSingleValueField("dansOtherId", b)))
    ov.foreach(b => fields.append(PrimitiveSingleValueField("dansOtherIdVersion", b)))
    st.foreach(b => fields.append(PrimitiveSingleValueField("dansSwordToken", b)))
    FieldList(fields.toList)
  }

  private def setBagId(bagIdOpt: Option[String], pid: String): Try[String] = bagIdOpt match {
    case Some(bagId) => checkBagIdOrigin(bagId, pid)
    case None => Success(mintBagId())
  }

  /**
   *
   * @param bagId the bagId found in the Vault metadatablock
   * @param pid   the dataset pid
   * @return
   * if no previous version: filled in bagId means SWORD filled it in
   * if previous version: new bagId means SWORD filled it in, the same bagId as in previous version means UI created new draft.
   */
  private def checkBagIdOrigin(bagId: String, pid: String): Try[String] = {
    getBagIdOfPreviousVersion(pid)
      .map(bagIdOpt => {
        if (bagId.equals(bagIdOpt.getOrElse("")))
          mintBagId()
        else
          bagId
      })
  }.recoverWith { case e: Exception => Failure(ExternalSystemCallException(s"Problem with Dataverse api: $e")) }

  private def getBagIdOfPreviousVersion(pid: String): Try[Option[String]] = {
    for {
      response <- dataverse.dataset(pid).view(Version.LATEST_PUBLISHED)
      dsv <- response.data
      optBagId = dsv.metadataBlocks.get("dansDataVaultMetadata")
        .flatMap(_.fields
          .map(_.asInstanceOf[PrimitiveSingleValueField])
          .find(_.typeName == "dansBagId"))
        .map(_.value)
    } yield optBagId
  }

  def mintUrnNbn(): Try[String] = Try {
    Http(s"${ pidGeneratorBaseUrl resolve "create" }?type=urn")
      .method("POST")
      .header("Accept", "application/json")
      .asString.body
  }.recoverWith { case e: Exception => Failure(ExternalSystemCallException(s"Problem with pid-generator service: $e")) }

  def mintBagId(): String = {
    s"urn:uuid:${ UUID.randomUUID().toString }"
  }
}
