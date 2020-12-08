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

import nl.knaw.dans.lib.error._
import nl.knaw.dans.lib.logging.DebugEnhancedLogging
import org.json4s.jackson.JsonMethods
import org.json4s.{ DefaultFormats, Formats }
import org.scalatra._

import scala.util.{ Failure, Success }

class PrePublishWorkflowServlet(app: PrePublishWorkflowApp,
                                version: String) extends ScalatraServlet with DebugEnhancedLogging {
  private implicit val jsonFormats: Formats = DefaultFormats

  get("/") {
    contentType = "text/plain"
    Ok(s"Pre-publish Workflow Service running ($version)")
  }

  post("/workflow") {
    contentType = "application/json"
    trace(request.body)
    val requestBodyJson = JsonMethods.parse(request.body)
    //TODO: extract directly from request body -> JsonMethods.parse(request.body).extract[WorkFlowVariables]
    val invocationId = (requestBodyJson \ "invocationId").extract[String]
    val datasetIdentifier = (requestBodyJson \ "globalId").extract[String]
    val datasetId = (requestBodyJson \ "datasetId").extract[String]
    val majorVersion = (requestBodyJson \ "majorVersion").extract[String]
    val minorVersion = (requestBodyJson \ "minorVersion").extract[String]
    val workflowVariables = WorkFlowVariables(invocationId, datasetIdentifier, datasetId, majorVersion, minorVersion)

    app.handleWorkflow(workflowVariables)
      .doIfSuccess { _ => logger.info("Workflow finished successfully") }
      .doIfFailure { case e => logger.error("Workflow failed", e) }
    match {
      case Success(_) => ServiceUnavailable()
      /*
       * The Dataverse code only check if the result is successful (200 or 201) or not, so there is no point in returning a
       * sophisticated response here.
       */
      case Failure(RequestFailedException(_, _, _)) => ServiceUnavailable()
      case _ => InternalServerError()
    }
  }

  post("/rollback") {
    contentType = "application/json"
    trace(request.body)
    Ok()
  }
}
