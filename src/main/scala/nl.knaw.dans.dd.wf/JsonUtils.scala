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

import nl.knaw.dans.dd.wf.json.{ CompoundField, Field, PrimitiveFieldMultipleValues, PrimitiveFieldSingleValue }
import org.json4s.JsonAST.{ JNull, JObject }
import org.json4s.{ CustomSerializer, Extraction }

object JsonUtils {

  object MetadataFieldSerializer extends CustomSerializer[Field](format => ( {
    case jsonObj: JObject =>
      val multiple = (jsonObj \ "multiple").extract[Boolean]
      val typeClass = (jsonObj \ "typeClass").extract[String]

      typeClass match {
        case "primitive" if !multiple => Extraction.extract[PrimitiveFieldSingleValue](jsonObj)
        case "primitive" => Extraction.extract[PrimitiveFieldMultipleValues](jsonObj)
        case "controlledVocabulary" if !multiple => Extraction.extract[PrimitiveFieldSingleValue](jsonObj)
        case "controlledVocabulary" => Extraction.extract[PrimitiveFieldMultipleValues](jsonObj)
        case "compound" => Extraction.extract[CompoundField](jsonObj)
      }
  }, {
    case null => JNull
  }
  ))
}
