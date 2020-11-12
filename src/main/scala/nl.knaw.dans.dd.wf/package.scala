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

import java.net.URI
import java.text.DateFormat
import java.util.{ Calendar, TimeZone }

import better.files.File
import org.json4s.{ DefaultFormats, Formats }

import scala.concurrent.ExecutionContext

package object wf {

  implicit val jsonFormats: Formats = DefaultFormats + JsonUtils.MetadataFieldSerializer

  implicit val context = ExecutionContext.global

  case class WorkFlowVariables(invocationId: String, pid: String, datasetId: String, majorVersion: String, minorVersion: String)

  case class LockRecord(lockType: String, date: String, user: String)

  case class LockStatusMessage(status: String, data: List[LockRecord])

//  val configuration: Configuration = Configuration(File(System.getProperty("app.home")))
//  implicit protected val connectionTimeout: Int = configuration.connectionTimeout
//  implicit protected val readTimeout: Int = configuration.readTimeout
//  implicit protected val baseUrl: URI = configuration.baseUrl
//  implicit protected val apiToken: String = configuration.apiToken
//  implicit protected val apiVersion: String = configuration.version
}
