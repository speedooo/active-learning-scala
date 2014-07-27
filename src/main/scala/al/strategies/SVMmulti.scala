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
import ml.classifiers.{KNN, NoLearner, Learner}
import ml.models.Model
import svmal.{SVMStrategymulti}

/**
 * One SVM per class; queries by round-robin.
 * @param pool
 * @param algorithm
 * @param debug
 */
case class SVMmulti(pool: Seq[Pattern], algorithm: String, debug: Boolean = false) extends Strategy {
  override val toString = s"SVMmulti ($algorithm)"
  val learner = NoLearner()

  protected def resume_queries_impl(unlabeled: Seq[Pattern], labeled: Seq[Pattern]) = {
    val labeledar = labeled.toArray
    val unlabeledar = unlabeled.toArray
    val svms = {
      for (c <- 0 until nclasses) yield {
        new SVMStrategymulti(algorithm, SVMStrategymulti.PatternsToInstances2(labeledar, c), SVMStrategymulti.PatternsToInstances2(unlabeledar, c))
      }
    }.toArray
    queries_rec(svms, unlabeled, labeled)
  }

  private def queries_rec(svm: Seq[SVMStrategymulti], unlabeled: Seq[Pattern], labeled: Seq[Pattern]): Stream[Pattern] = {
    if (unlabeled.isEmpty) Stream.Empty
    else {
      val n = labeled.size
      if (debug) visual_test(null, unlabeled, labeled)
      val id = svm(n % nclasses).nextQuery()
      val selected = pool.find(_.id == id) match {
        case Some(p) => p
        case None => println("Queried id not found!")
          sys.exit(0)
      }
      if (stop) Stream(selected)
      else {
        if (debug) visual_test(selected, unlabeled, labeled)
        selected #:: queries_rec(svm, unlabeled.diff(Seq(selected)), labeled :+ selected)
      }
    }
  }

  protected def visual_test(selected: Pattern, unlabeled: Seq[Pattern], labeled: Seq[Pattern]) {
    val current_model = KNN(5, "eucl", pool).build(labeled)
    plot.zera()
    for (p <- distinct_pool) plot.bola(p.x, p.y, current_model.predict(p), 9)
    for (p <- labeled) plot.bola(p.x, p.y, p.label.toInt + 5, 6)
    if (selected != null) plot.bola(selected.x, selected.y, -1, 25)
    plot.mostra()
    Thread.sleep((delay * 1000).round.toInt)
  }
}
