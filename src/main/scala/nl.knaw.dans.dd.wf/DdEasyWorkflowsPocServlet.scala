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

import nl.knaw.dans.lib.logging.DebugEnhancedLogging
import org.json4s.native.JsonMethods
import org.scalatra._

class DdEasyWorkflowsPocServlet(app: DdEasyWorkflowsPocApp,
                                version: String) extends ScalatraServlet with DebugEnhancedLogging {

  get("/") {
    contentType = "text/plain"
    Ok(s"DD Easy Worflows Poc Service running ($version)")
  }

  synchronized {
    post("/workflow") {
      {
        contentType = "application/json"
        val requestBodyJson = JsonMethods.parse(request.body)
        val invocationId = (requestBodyJson \ "invocationId").extract[String]
        val datasetIdentifier = (requestBodyJson \ "globalId").extract[String]
        app.doWorkFlow(invocationId, datasetIdentifier)
      }
    }

  }
}
