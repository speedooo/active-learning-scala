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

package al.strategies

import ml.Pattern
import ml.classifiers.Learner
import util.Graphics.Plot

/**
 * Only distinct patterns are accepted into the pool.
 */
trait Strategy {
  val learner: Learner
  var stop = false
  val pool: Seq[Pattern]
  lazy val distinct_pool = if (pool.distinct != pool) {
    println("The pool cannot have repeated instances!")
    sys.exit(1)
  } else pool
  val debug: Boolean
  lazy val nclasses = if (distinct_pool.nonEmpty) distinct_pool.head.nclasses else throw new Error("Lazy val nclasses undiscoverable from an empty patterns!")
  val delay: Double = .005
  lazy val plot = new Plot
  lazy val (firstof_each_class, rest) = extract_one_per_class(distinct_pool)

  /**
   * Returns a stream of queries.
   * The first instances are the first from each class,
   * because in practice no one would risk the budget before having at least these initial labels.
   * (except in cluster-based approaches, but even in this case an initial sampling is reasonable,
   * specially because of the guarantee of encompassing all classes)
   * @return
   */
  def queries: Stream[Pattern] = firstof_each_class.toStream ++ resume_queries_impl(rest, firstof_each_class)

  /**
   * Se estourar o tempo limite,
   * espera a query atual terminar
   * e retorna aquelas feitas até esse ponto.
   * Logo, a atual é desperdiçada.
   * @param seconds
   */
  def timeLimitedQueries(seconds: Double) = {
    val ti = System.currentTimeMillis()
    var t = 0d
    val withinTimeLimit = queries.takeWhile { _ =>
      t = (System.currentTimeMillis() - ti) / 1000d
      t <= seconds
    }.toSeq
    withinTimeLimit
  }

  /**
   * Se estourar o tempo limite,
   * espera a query atual terminar
   * e retorna aquelas feitas até esse ponto.
   * Logo, a atual é desperdiçada.
   * @param seconds
   */
  def timeLimitedResumeQueries(labeled: Seq[Pattern], seconds: Double) = {
    val ti = System.currentTimeMillis()
    var t = 0d
    val withinTimeLimit = resume_queries(labeled).takeWhile { _ =>
      t = (System.currentTimeMillis() - ti) / 1000d
      t <= seconds
    }.toSeq
    withinTimeLimit
  }

  protected def resume_queries_impl(unlabeled: Seq[Pattern], labeled: Seq[Pattern]): Stream[Pattern]

  /**
   * Resume queries from the last performed queries.
   * Returns only the new queries.
   * Like in queries(),
   * the first instances from labeled are the first from each class (in the same order as generated by extract_one_per_class()).
   * Exception if they are not complete yet.
   */
  def resume_queries(labeled: Seq[Pattern]) = {
    //todo: I donk know if resuming queries is a perfectly working idea
    if (firstof_each_class != labeled.take(nclasses)) {
      println(s"In dataset '${labeled.head.dataset().relationName()}': queries cannot be resumed, there should be the exact one-instance-per-class subset at the beginning.")
      println("Expected: " + firstof_each_class)
      println("Found:" + labeled.take(nclasses).toList)
      sys.exit(1)
    }
    resume_queries_impl(distinct_pool.diff(labeled), labeled)
  }

  protected def extract_one_per_class(patterns: Seq[Pattern]) = {
    val firstof_each_class = ((0 until nclasses) map {
      c => patterns find (_.label == c) match {
        case Some(pattern) => pattern
        case _ => println("Dataset should have at least one instance from each class per fold! Label index " + c + " not found in dataset " + patterns.head.dataset().relationName() + " !")
          sys.exit(1)
      }
    }).toList
    (firstof_each_class, patterns.diff(firstof_each_class))
  }

  protected def visual_test(selected: Pattern, unlabeled: Seq[Pattern], labeled: Seq[Pattern])
}