package com.github.smac89.gitbot

import com.github.smac89.{CreateRelease, Release, SimpleSemver}
import ghscala.{Blob, Github}
import httpz.scalajhttp.ScalajInterpreter
import httpz.{Action, Request}
import io.lemonlabs.uri.encoding.percentEncode
import scalaz.concurrent.Task
import scalaz.{-\/, Monoid, NonEmptyList, \/-}

import scala.util.matching.Regex

object Main {

   val watchedRepoOwner: String = "ChromiumEmbedded".toLowerCase
   val watchedRepoName: String = "java-cef"

   val buildRepoOwner: String = "smac89"
   val buildRepoName: String = "java-cef-build"

   // From https://bitbucket.org/chromiumembedded/cef/issues/2596/improve-cef-version-number-format#comment-50679036
   val versionPattern: Regex = """(?i)((?:(\d+)\.?){3}\+g\w+\+chromium-(?:(\d+)\.?){4})""".r

   def parseCefVersion(fileContent: String): Option[SimpleSemver] = {
      val cefVersion = """set\s*\(CEF_VERSION\s+"(.+?)"\s*\)""".r.unanchored
      fileContent match {
         case cefVersion(version) => version
         case _ => None
      }
   }

   val latestRelease: Action[Option[SimpleSemver]] =
      Github.repoReleases(buildRepoOwner, buildRepoName, "latest")
         .map(_.tagName.replaceFirst("""(\d+\.?){3}""", ""))

   val findFileWithCefVersion: Action[Blob] =
      Github.trees(watchedRepoOwner, watchedRepoName, percentEncode.encode("HEAD:", "utf-8"))
         .map(_.tree.find(_.path == "CMakeLists.txt").map(_.sha))
         .flatMap {
            case Some(sha) => Github.blob(watchedRepoOwner, watchedRepoName, sha)
            case None => Action(httpz.RequestsMonad.pure(-\/(httpz.Error.http(new RuntimeException("Missing `Sha` for CMakeLists.txt")))))
         }

   def triggerNewRelease(name: String, tagName: String): Action[Release] =
      Github.createRepoRelease(buildRepoOwner, buildRepoName, CreateRelease(name, tagName))

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
         latestBuildTag <- latestRelease.nel
         fileContent <- findFileWithCefVersion.nel.map(_.decoded)
         cefVersion = parseCefVersion(fileContent)
         if ((cefVersion, latestBuildTag) match {
            case (Some(cef), Some(tag)) =>
               println(cef)
               println(tag)
               cef > tag
            case _ => false
         })
         _ <- triggerNewRelease("TESTING", "v1.1.100").nel
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
   def main(args: Array[String]): Unit =
      program(sys.env.get("GITHUB_TOKEN")).unsafePerformSync
}
