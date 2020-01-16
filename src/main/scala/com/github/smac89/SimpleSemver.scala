package com.github.smac89

import scala.language.implicitConversions
import scala.util.matching.Regex

// https://semver.org/
case class SimpleSemver(major: String, minor: String, patch: String)

object SimpleSemver {
   private [this] val pattern: Regex = """(\d+)\.(\d+).(\d+)""".r.unanchored

   implicit def unapply(arg: CharSequence): Option[SimpleSemver] = arg match {
      case pattern(major, minor, patch) => Some(SimpleSemver(major, minor, patch))
      case _ => None
   }

   implicit def unapply(arg: Option[CharSequence]): Option[SimpleSemver] = arg match {
      case Some(chr) => unapply(chr)
      case _ => None
   }
}
