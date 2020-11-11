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

import nl.knaw.dans.dd.wf.queue.Task
import nl.knaw.dans.lib.logging.DebugEnhancedLogging
import org.json4s.Formats

import scala.util.Try

case class ResumeTask(invocationId: String)(implicit jsonFormats: Formats) extends Task with DebugEnhancedLogging with Http {

  override def run(): Try[Unit] = {
    trace()
    debug(s"Resume workflow with invocationId: $invocationId")

    for {
      _ <- resume(invocationId)
    } yield ()
  }
}