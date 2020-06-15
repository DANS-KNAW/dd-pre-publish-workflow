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

import org.json4s.JsonAST.{ JField, JString }
import org.json4s.native.JsonMethods
import org.json4s.native.Serialization.write
import scalaj.http.Http

class DdEasyWorkflowsPocApp(configuration: Configuration) {
  def doWorkflow(invocationId: String, datasetIdentifier: String): Unit = {
    val json = getDatasetJson(datasetIdentifier)
    val updatedJson = updateLicense(json)
    updateMetadata(datasetIdentifier, invocationId, updatedJson)
  }

  def getDatasetJson(datasetIdentifier: String): String = {

    val datasetJson =
      Http(s"http://localhost:8080/api/datasets/:persistentId/versions/:draft?persistentId=$datasetIdentifier")
        .header("content-type", "application/json")
        .header("accept", "application/json")
        .header("X-Dataverse-key", "57ce6d09-6195-4621-a1ed-cd1e3e9f6160")
        .asString.body

    datasetJson
  }

  def updateLicense(jsonString: String): String = {

    var json = JsonMethods.parse(jsonString) \ "data"

    val fields = json \ "metadataBlocks" \ "access-and-license" \ "fields"
    val licenseFromMetadata = (fields(1) \ "value").extract[String]

    if (!licenseFromMetadata.equalsIgnoreCase("CC0-1.0")) {
      json = json.transformField {
        case JField("license", JString(_)) => ("license", JString("NONE"))
        case JField("termsOfUse", JString(_)) => ("termsOfUse", JString(licenseFromMetadata))
      }
    }
    write(json)
  }

  def updateMetadata(datasetIdentifier: String, invocationId: String, metadata: String): Unit = {

    Http(s"http://localhost:8080/api/datasets/:persistentId/versions/:draft?persistentId=$datasetIdentifier")
      .put(metadata)
      .header("content-type", "application/json")
      .header("accept", "application/json")
      .header("X-Dataverse-key", "57ce6d09-6195-4621-a1ed-cd1e3e9f6160")
      .asString.headers

    //    val ok = new Thread(new Runnable {
    //
    //      override def run(): Unit = {
    //        println("Thread OK sleep 2 seconds ")
    //        Thread.sleep(2000)
    //        Ok()
    //      }
    //    })

    val resume = new Thread(new Runnable {

      override def run(): Unit = {
        println("Thread resume sleep 4 seconds ")
        Thread.sleep(4000)
        Http(s"http://localhost:8080/api/workflows/$invocationId")
          .postData("")
          .header("content-type", "application/json")
          .header("accept", "application/json")
          .header("X-Dataverse-key", "57ce6d09-6195-4621-a1ed-cd1e3e9f6160")
          .asString.headers
      }
    })

    //ok.start()
    resume.start()
  }
}
