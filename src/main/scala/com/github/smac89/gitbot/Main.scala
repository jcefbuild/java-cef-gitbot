package com.github.smac89.gitbot

import java.time.LocalDateTime
import java.time.format.DateTimeFormatter.{ISO_LOCAL_DATE_TIME, ISO_OFFSET_DATE_TIME}

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
   Logger.setDefaultFormatter(LogFormatter.AppLogFormatter)

   val watchedRepoOwner: String = "ChromiumEmbedded".toLowerCase
   val watchedRepoName: String = "java-cef"

   val buildRepoOwner: String = "jcefbuild"
   val buildRepoName: String = "java-cef-build"

   val RELEASE_DATE_KEY = "com.github.smac89.gitbot.lastReleaseDate"

   // From https://bitbucket.org/chromiumembedded/cef/issues/2596/improve-cef-version-number-format#comment-50679036
   val cefVersionPattern: Regex = """(?i)((?:\d+\.?){3}\+g[a-z0-9]+\+chromium-(?:\d+\.?){4})""".r.unanchored
   val repoReleasePattern: Regex = """^(v(?:\d+[.-]){3})""".r.unanchored

   val latestBuildRelease: Action[Release] =
      Github.Repo.releases(buildRepoOwner, buildRepoName, "latest")

   val findFileWithCefVersion: Action[Blob] =
      Github.trees(watchedRepoOwner, watchedRepoName, percentEncode.encode("HEAD:", "utf-8"))
         .map(_.tree.find(_.path == "CMakeLists.txt").map(_.sha))
         .flatMap {
            case Some(sha) => Github.blob(watchedRepoOwner, watchedRepoName, sha)
            case None => Action(httpz.RequestsMonad.pure(-\/(httpz.Error.http(new RuntimeException("Missing `Sha` for CMakeLists.txt")))))
         }

   def parseCefVersion(fileContent: String): Option[SimpleSemver] = {
      val cefVersion = """set\s*\(CEF_VERSION\s+"(.+?)"\s*\)""".r.unanchored
      fileContent match {
         case cefVersion(version) => version
         case _ => None
      }
   }

   def triggerNewRelease(name: String, tagName: String, messageBody: String): Action[Release] = {
      info(s"Creating a new release with tag: $tagName")
      Github.Repo.createRelease(buildRepoOwner, buildRepoName,
         CreateRelease(name, tagName, draft = false, body = Some(messageBody)))
   }

   def commitsSummary(startDate: LocalDateTime): Action[(String, LocalDateTime)] =
      Github.Commits.list(watchedRepoOwner,
         watchedRepoName,
         filterParams = Map("since" -> startDate.toString.format(ISO_OFFSET_DATE_TIME))).map { commits =>
         commits.map(_.commit)
            .sortBy(commit => LocalDateTime.parse(commit.author.date, ISO_OFFSET_DATE_TIME))
            .map(commit =>
               s"${commit.tree.sha.substring(0, 7)} - ${commit.message} <${commit.author.name}>")
            .mkString("## Changes summary:\n```\n", "\n", "\n```") ->
            LocalDateTime.parse(commits.head.commit.author.date, ISO_OFFSET_DATE_TIME)
      }

   def saveReleaseLastCommitDate(time: LocalDateTime): Unit = redis.set(RELEASE_DATE_KEY, time.format(ISO_LOCAL_DATE_TIME))

   def readCurrentReleaseCommitDate: LocalDateTime = LocalDateTime.parse(redis.get[String](RELEASE_DATE_KEY).get, ISO_OFFSET_DATE_TIME)

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
               info("Checking for new release...")
               (cef > tag).tap { updateFound =>
                  if (updateFound) {
                     info(s"🚀 New release found: $cef")
                  } else {
                     info(cef)
                     info(tag)
                     info("Nothing new ☹️")
                  }
               }
            case _ => false
         })
         lastReleaseDate = readCurrentReleaseCommitDate
         (releaseMessage, newLastReleaseDate) <- commitsSummary(lastReleaseDate).nel
         tagName = s"$repoVersion${cefVersion.get.original}"
         _ <- triggerNewRelease(tagName, tagName, releaseMessage).nel
         _ = saveReleaseLastCommitDate(newLastReleaseDate)

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
      info(s"🤖 Started! ")
      program(sys.env.get("GITHUB_TOKEN")).unsafePerformSync
      info(s"🤖 Finished!")
   }
}
