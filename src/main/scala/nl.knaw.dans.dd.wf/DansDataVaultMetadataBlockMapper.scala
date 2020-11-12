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
package nl.knaw.dans.dd.wf

import nl.knaw.dans.dd.wf.json._
import org.json4s.JObject
import org.json4s.JsonAST.JArray
import org.json4s.native.{ JsonMethods, Serialization }

import scala.collection.mutable.ListBuffer

class DansDataVaultMetadataBlockMapper {

  case class EditField(typeName: String, value: String)
  case class EditFields(fields: List[EditField])

  val vaultFields = new ListBuffer[PrimitiveFieldSingleValue]

  // TODO: Base on info from workflow
  def createDataVaultFields(): EditFields = {
    val dansPid = EditField("dansDataversePid", "NBN12345")
    val pidVersion = EditField("dansDataversePidVersion", "2,2")
    EditFields(List(dansPid, pidVersion))
  }

  def mintUrnNbn(): String = {
    "testURN:NBN"
  }

  def isDoi(pid: String): Boolean = {
    pid.contains("doi")
  }
}
