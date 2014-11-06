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
import ml.models.Model

case class DensityWeightedLabelUtility(learner: Learner, pool: Seq[Pattern], distance_name: String, alpha: Double = 1, beta: Double = 1, debug: Boolean = false)
  extends StrategyWithLearnerAndMapsLU with MarginMeasure {
  override val toString = "Density Weighted LU a" + alpha + " b" + beta + " (" + distance_name + ")"
  val abr = "DWLU" + distance_name.take(3)
  val id = if (alpha == 1 && beta == 1) distance_name match {
    case "eucl" => 36
    case "cheb" => 38
    case "maha" => 39
    case "manh" => 37
  } else throw new Error("Parametros inesperados para DWLU.")

  protected def next(mapU: => Map[Pattern, Double], mapsL: => Seq[Map[Pattern, Double]], current_model: Model, unlabeled: Seq[Pattern], labeled: Seq[Pattern]) = {
    val selected = unlabeled.head
    //      maxBy {
    //      x => ???
    ////        val similarityU = mapU(x) / mapU.size.toDouble
    ////        val similaritiesL = mapsL(x) / mapL.size.toDouble
    ////        (1 - margin(current_model)(x)) * math.pow(similarityU, beta) / math.pow(similarityL, alpha)
    //    }
    selected
  }

  def simL(mapsL: => Seq[Map[Pattern, Double]], patt: Pattern) = {
    val tot = (mapsL map (_.size)).sum.toDouble
    mapsL map { m =>
      val p = m.size / tot
      math.pow(m(patt), p)
    }
    //      0 to nclasses map mapsL s
  }
}
