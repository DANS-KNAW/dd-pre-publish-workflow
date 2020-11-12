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

package object json {
  type MetadataBlockName = String
  type JsonObject = Map[String, Field]

  abstract class Field

  trait MetadataFormats
  case class MetadataBlock(displayName: String, fields: List[Field]) extends MetadataFormats
  case class DatasetVersion(metadataBlocks: Map[MetadataBlockName, MetadataBlock]) extends MetadataFormats
  case class DataverseDataset(datasetVersion: DatasetVersion) extends MetadataFormats

  case class PrimitiveFieldSingleValue(typeName: String,
                                       multiple: Boolean,
                                       typeClass: String,
                                       value: String
                                      ) extends Field with MetadataFormats

  case class PrimitiveFieldMultipleValues(typeName: String,
                                          multiple: Boolean,
                                          typeClass: String,
                                          value: List[String]
                                         ) extends Field with MetadataFormats

  case class CompoundField(typeName: String,
                           multiple: Boolean,
                           typeClass: String = "compound",
                           value: List[Map[String, Field]]) extends Field with MetadataFormats

  case class DataverseFile(description: Option[String] = None,
                           directoryLabel: Option[String] = None,
                           restrict: Option[String] = Some("true"),
                           categories: List[String] = List.empty[String])


}
