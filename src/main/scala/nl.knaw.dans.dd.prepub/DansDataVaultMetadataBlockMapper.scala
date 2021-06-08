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

import nl.knaw.dans.lib.dataverse.model.dataset.{ FieldList, MetadataField, PrimitiveSingleValueField }
import nl.knaw.dans.lib.dataverse.{ DataverseInstance, Version }
import nl.knaw.dans.lib.logging.DebugEnhancedLogging

import java.util.UUID
import scala.collection.mutable.ListBuffer
import scala.util.{ Success, Try }

class DansDataVaultMetadataBlockMapper(nbnPrefix: String, dataverse: DataverseInstance) extends DebugEnhancedLogging {
  require(nbnPrefix != null)

  def createDataVaultFields(workFlowVariables: WorkFlowVariables,
                            optBagId: Option[String],
                            optNbn: Option[String]): Try[FieldList] = {
    trace(workFlowVariables, optBagId, optNbn)
    for {
      bagId <- getBagId(optBagId, workFlowVariables)
      urn = optNbn.getOrElse(mintUrnNbn())
      fieldList = createFieldList(workFlowVariables, bagId, urn)
    } yield fieldList
  }

  private def getBagId(optFoundBagId: Option[String], w: WorkFlowVariables): Try[String] = {
    trace(optFoundBagId, w)
    getLatestPublishedBagId(w).map {
      optLatestPublishedBagId =>
        if (optLatestPublishedBagId == optFoundBagId) mintBagId()
        /* If the latest published bag ID is found, it *should* also be in the draft deposit (i.e. in optFoundBagId), because
         * metadata fields get pre-filled with the latest versions. This means it *should* be safe to assume that optFoundBagId is
         * only None if optLatestPublishedBagId is also None, that is if there is no previous version.
         */
        else {
          if (optFoundBagId.isEmpty) throw new IllegalArgumentException("Dataset with a latest published version without bag ID found!")
          optFoundBagId.get
        }
    }
  }

  private def getLatestPublishedBagId(w: WorkFlowVariables): Try[Option[String]] = {
    if (hasLatestPublishedVersion(w)) {
      for {
        response <- dataverse.dataset(w.globalId).view(Version.LATEST_PUBLISHED)
        _ = if (logger.underlying.isDebugEnabled) debug(s"Successfully retrieved latest published metadata: ${ response.string }")
        dsv <- response.data
        optBagId = dsv.metadataBlocks.get("dansDataVaultMetadata")
          .flatMap(_.fields
            .map(_.asInstanceOf[PrimitiveSingleValueField])
            .find(_.typeName == "dansBagId"))
          .map(_.value)
        _ = debug(s"optBagId = $optBagId")
      } yield optBagId
    }
    else Success(None)
  }

  private def hasLatestPublishedVersion(w: WorkFlowVariables): Boolean = {
    s"${ w.majorVersion }.${ w.minorVersion }" != "1.0"
  }

  private def createFieldList(workFlowVariables: WorkFlowVariables,
                              bagId: String,
                              urn: String,
                             ): FieldList = {
    trace(workFlowVariables, bagId, urn)
    val fields = ListBuffer[MetadataField]()
    fields.append(PrimitiveSingleValueField("dansDataversePid", workFlowVariables.globalId))
    fields.append(PrimitiveSingleValueField("dansDataversePidVersion", s"${ workFlowVariables.majorVersion }.${ workFlowVariables.minorVersion }"))
    fields.append(PrimitiveSingleValueField("dansBagId", bagId))
    fields.append(PrimitiveSingleValueField("dansNbn", urn))
    FieldList(fields.toList)
  }

  def mintUrnNbn(): String = {
    trace(())
    "urn:nbn:" + nbnPrefix + UUID.randomUUID().toString
  }

  def mintBagId(): String = {
    trace(())
    "urn:uuid:" + UUID.randomUUID().toString
  }
}
