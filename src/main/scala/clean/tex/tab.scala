/*

active-learning-scala: Active Learning library for Scala
Copyright (c) 2014 Davi Pereira dos Santos

   This program is free software: you can redistribute it and/or modify
   it under the terms of the GNU General Public License as published by
   the Free Software Foundation, either version 3 of the License, or
   (at your option) any later version.

   This program is distributed in the hope that it will be useful,
   but WITHOUT ANY WARRANTY; without even the implied warranty of
   MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
   GNU General Public License for more details.

   You should have received a copy of the GNU General Public License
   along with this program.  If not, see <http://www.gnu.org/licenses/>.
*/

package clean.tex

import clean.{AppWithUsage, Ds, LearnerTrait, StratsTrait}
import ml.classifiers.SVMLib
import util.{Stat, StatTests}

import scala.collection.mutable

object tab extends AppWithUsage with LearnerTrait with StratsTrait {
  lazy val arguments = superArguments ++ List("learners:nb,5nn,c45,vfdt,ci,...|eci|i|ei|in|svm", "medida:alca|alcg")
  val context = "tabtex"
  val sl = mutable.LinkedHashSet[String]()
  run()

  override def run() = {
    super.run()
    val res = datasets map { dataset =>
      val ds = Ds(path, dataset)
      ds.open()
      val ms = for {
        s <- allStrats()
      } yield {
        if (s.id >= 17 && s.id <= 20) {
          val learner = SVMLib()
          sl += s"${s.abr} ${learner.toString.take(2)}"
          val vs = for {
            r <- 0 until runs
            f <- 0 until folds
          } yield {
            ds.getMeasure(measure, s, learner, r, f) match {
              case Some(v) => v
              case None => ds.quit(s"No measure for ${(measure, s, learner, r, f)}!")
            }
          }
          Seq(Stat.media_desvioPadrao(vs.toVector))
        } else {
          learners(learnersStr) map { l =>
            sl += s"${s.abr} ${l.toString.take(2)}"
            val vs = for {
              r <- 0 until runs
              f <- 0 until folds
            } yield {
              ds.getMeasure(measure, s, l, r, f) match {
                case Some(v) => v
                case None => ds.quit(s"No measure for ${(measure, s, l, r, f)}!")
              }
            }
            Stat.media_desvioPadrao(vs.toVector)
          }
        }
      }
      ds.close()
      ds.dataset -> ms.flatten
    }
    println(s"")
    println(s"")
    println(s"")
    StatTests.extensiveTable2(res.toSeq.map(x => x._1.take(3) + x._1.takeRight(3) -> x._2), sl.toVector.map(_.toString), "nomeTab", measure.toString)
    justQuit("Datasets prontos.")
  }
}
