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

import scala.collection.mutable.ListBuffer

class DansDataVaultMetadataBlockMapper {

  val vaultFields = new ListBuffer[PrimitiveFieldSingleValue]

  //Todo implement mapping logic
  def populateDataVaultBlock(datasetVersion: DatasetVersion, workFlowVariables: WorkFlowVariables): MetadataBlock = {
    val dansPid = PrimitiveFieldSingleValue("dansDataversePid", false, "primitive", "NBN12345")
    val pidVersion = PrimitiveFieldSingleValue("dansDataversePidVersion", false, "primitive", "2,2")
    MetadataBlock("Data Vault Metadata", List(dansPid, pidVersion))
  }

  def mintUrnNbn(): String = {
    "testURN:NBN"
  }

  def isDoi(pid: String): Boolean = {
    pid.contains("doi")
  }
}
