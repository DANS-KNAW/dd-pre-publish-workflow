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
import java.nio.charset.StandardCharsets

import nl.knaw.dans.dd.prepub.dataverse.DataverseInstance
import nl.knaw.dans.dd.prepub.dataverse.json.{ MetadataBlock, MetadataFieldSerializer, PrimitiveFieldSingleValue }
import nl.knaw.dans.lib.logging.DebugEnhancedLogging
import org.json4s.jackson.{ JsonMethods, Serialization }
import org.json4s.{ DefaultFormats, Formats, JObject }
import scalaj.http.Http

import scala.util.Try

class PrePublishWorkflowApp(configuration: Configuration) extends DebugEnhancedLogging {
  implicit val jsonFormats: Formats = DefaultFormats + MetadataFieldSerializer

  // TODO: output should not go to stdout

  private implicit val resultOutput: PrintStream = Console.out
  private val dataverse = new DataverseInstance(configuration.dataverse)
  private val mapper = new DansDataVaultMetadataBlockMapper

  def handleWorkflow(workFlowVariables: WorkFlowVariables): Try[Unit] = {
    trace(workFlowVariables)
    for {
      response <- dataverse.dataset(workFlowVariables.pid, isPersistentId = true).view(Some(":draft"))
      metadata <- Try { new String(response.body, StandardCharsets.UTF_8) }
      _ = debug(s"Found vault metadata $metadata")
      vaultBlockOpt <- getVaultBlockOpt(metadata)
      vaultFields <- Try {
        val bagId = getVaultFieldValue(vaultBlockOpt, "dansBagId")
        val urn = getVaultFieldValue(vaultBlockOpt, "dansNbn")
        val otherId = getVaultFieldValue(vaultBlockOpt, "dansOtherId")
        val otherIdVersion = getVaultFieldValue(vaultBlockOpt, "dansOtherIdVersion")
        val swordToken = getVaultFieldValue(vaultBlockOpt, "dansSwordToken")
        mapper.createDataVaultFields(workFlowVariables, bagId, urn, otherId, otherIdVersion, swordToken)
      }
      _ <- dataverse.dataset(workFlowVariables.pid, isPersistentId = true).editMetadata(Serialization.writePretty(vaultFields), replace = true)
    } yield ()
  }

  private def getVaultFieldValue(vaultBlockOpt: Option[MetadataBlock], fieldId: String): Option[String] = {
    vaultBlockOpt.flatMap(_.fields.map(_.asInstanceOf[PrimitiveFieldSingleValue]).find(_.typeName == fieldId)).map(_.value)
  }

  private def getVaultBlockOpt(metadata: String): Try[Option[MetadataBlock]] = Try {
    trace(metadata)
    val vaultBlockJson = (JsonMethods.parse(metadata) \\ "dansDataVaultMetadata")
    if (logger.underlying.isDebugEnabled) debug(Serialization.writePretty(vaultBlockJson))
    vaultBlockJson match {
      case JObject(List()) => None
      case v => Option(v.extract[MetadataBlock])
    }
  }
}
