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
package nl.knaw.dans.dd

import nl.knaw.dans.lib.dataverse.model.dataset.{ CompoundField, ControlledSingleValueField, MetadataField, PrimitiveMultipleValueField, PrimitiveSingleValueField }
import org.json4s.{ CustomSerializer, DefaultFormats, Extraction, Formats, JNull, JObject }

import scala.collection.mutable

package object prepub {

  implicit val jsonFormats: Formats = DefaultFormats + MetadataFieldSerializer

  case class WorkFlowVariables(invocationId: String, globalId: String, datasetId: String, majorVersion: String, minorVersion: String)
  case class LockRecord(lockType: String, date: String, user: String)
  case class LockStatusMessage(status: String, data: List[LockRecord])
  case class RequestFailedException(status: Int, msg: String, body: String) extends Exception(s"Command could not be executed. Server returned: status line: '$msg', body: '$body'")
  case class ExternalSystemCallException(msg: String, cause: Throwable) extends RuntimeException(msg, cause)

  type JsonObject = Map[String, MetadataField]

  case class FieldMap() {
    private val fields = mutable.Map[String, MetadataField]()

    def addPrimitiveField(name: String, value: String): Unit = {
      fields.put(name, PrimitiveSingleValueField(name, value))
    }

    def addCvField(name: String, value: String): Unit = {
      fields.put(name, ControlledSingleValueField(name, value))
    }

    def toJsonObject: JsonObject = fields.toMap
  }

  object MetadataFieldSerializer extends CustomSerializer[MetadataField](format => ( {
    case jsonObj: JObject =>
      val multiple = (jsonObj \ "multiple").extract[Boolean]
      val typeClass = (jsonObj \ "typeClass").extract[String]

      typeClass match {
        case "primitive" if !multiple => Extraction.extract[PrimitiveSingleValueField](jsonObj)
        case "primitive" => Extraction.extract[PrimitiveMultipleValueField](jsonObj)
        case "controlledVocabulary" if !multiple => Extraction.extract[PrimitiveSingleValueField](jsonObj)
        case "controlledVocabulary" => Extraction.extract[PrimitiveMultipleValueField](jsonObj)
        case "compound" => Extraction.extract[CompoundField](jsonObj)
      }
  }, {
    case null => JNull
  }
  ))

  case class WorkflowNotPausedException(numberOfTimesTried: Int, waitTimeInMilliseconds: Int) extends RuntimeException(s"Workflow was not paused. Number of tries = $numberOfTimesTried, wait time between tries = $waitTimeInMilliseconds ms.")

}
