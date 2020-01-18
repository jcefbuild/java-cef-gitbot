package com.github.smac89.gitbot

import com.github.smac89.{CreateRelease, Release, SimpleSemver}
import ghscala.{Blob, Github}
import httpz.scalajhttp.ScalajInterpreter
import httpz.{Action, Request}
import io.lemonlabs.uri.encoding.percentEncode
import scalaz.concurrent.Task
import scalaz.{-\/, Monoid, NonEmptyList, \/-}
import wvlet.log.{LogFormatter, LogSupport, Logger}

import scala.util.chaining._
import scala.util.matching.Regex

object Main extends LogSupport {
   Logger.setDefaultFormatter(LogFormatter.SourceCodeLogFormatter)

   val watchedRepoOwner: String = "ChromiumEmbedded".toLowerCase
   val watchedRepoName: String = "java-cef"

   val buildRepoOwner: String = "smac89"
   val buildRepoName: String = "java-cef-build"

   // From https://bitbucket.org/chromiumembedded/cef/issues/2596/improve-cef-version-number-format#comment-50679036
   val cefVersionPattern: Regex = """(?i)((?:(\d+)\.?){3}\+g[a-z0-9]+\+chromium-(?:(\d+)\.?){4})""".r
   val repoReleasePattern: Regex = """^((?:v\d+[.-]){3})""".r

   def parseCefVersion(fileContent: String): Option[SimpleSemver] = {
      val cefVersion = """set\s*\(CEF_VERSION\s+"(.+?)"\s*\)""".r.unanchored
      fileContent match {
         case cefVersion(version) => version
         case _ => None
      }
   }

   val latestBuildRelease: Action[Release] =
      Github.repoReleases(buildRepoOwner, buildRepoName, "latest")

   val findFileWithCefVersion: Action[Blob] =
      Github.trees(watchedRepoOwner, watchedRepoName, percentEncode.encode("HEAD:", "utf-8"))
         .map(_.tree.find(_.path == "CMakeLists.txt").map(_.sha))
         .flatMap {
            case Some(sha) => Github.blob(watchedRepoOwner, watchedRepoName, sha)
            case None => Action(httpz.RequestsMonad.pure(-\/(httpz.Error.http(new RuntimeException("Missing `Sha` for CMakeLists.txt")))))
         }

   def triggerNewRelease(name: String, tagName: String): Action[Release] = {
      info(s"Creating a new release with tag: $tagName...")
      Github.createRepoRelease(buildRepoOwner, buildRepoName, CreateRelease(name, tagName))
   }

   /**
    * Steps:
    * 1. Authenticate
    * 2. Get the version of the latest release from the java-cef-build repo
    * 2. Find the file in the java-cef repo that contains the new version
    * 3. Parse the file to find out what the version is
    * 4. If the version is newer than latest release, create a new release with the version number
    *
    * @param ghToken The github token
    * @return A task
    */
   def program(ghToken: Option[String]): Task[Unit] = {
      import scalaz.Scalaz.ToOrderOps

      implicit val monoidNelError: Monoid[NonEmptyList[httpz.Error]] = Monoid.instance(_.append(_), null)

      val task = for {
         latestRelease <- latestBuildRelease.nel
         fileContent <- findFileWithCefVersion.nel.map(_.decoded)
         cefVersion = parseCefVersion(fileContent)
         repoReleasePattern(repoVersion) = latestRelease.tagName
         cefVersionPattern(latestBuiltCef) = repoReleasePattern.replaceFirstIn(latestRelease.tagName, "")

         if ((cefVersion, latestBuiltCef: Option[SimpleSemver]) match {
            case (Some(cef), Some(tag)) =>
               (cef > tag).tap { update =>
                  if (update) {
                     info(s"New release found: $cef")
                  }
               }
            case _ => false
         })
         tagName = s"$repoVersion${cefVersion.get.original}"
         _ <- triggerNewRelease(tagName, tagName).nel
      } yield ()

      task.run.foldMap(ghToken.map { token =>
         val conf = Request.header("Authorization", s"token $token")
         ScalajInterpreter.task(conf).interpreter
      }.getOrElse(ScalajInterpreter.task.empty.interpreter)).flatMap {
         case \/-(_) | -\/(null) => Task.point(())
         case -\/(NonEmptyList(f, _)) => Task.fail(f)
      }
   }

   /**
    * The main entry point for the bot
    */
   def main(args: Array[String]): Unit = {
      info("Checking for new release...")
      program(sys.env.get("GITHUB_TOKEN")).unsafePerformSync
      info("Finished!")
   }
}
