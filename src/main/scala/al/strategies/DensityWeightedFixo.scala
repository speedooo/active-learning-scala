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
import ml.classifiers._
import ml.models.Model

case class DensityWeightedFixo(poolForLearner: Seq[Pattern], learner: Learner, pool: Seq[Pattern], beta: Double = 1, distance_name: String = "eucl", debug: Boolean = false)
   extends StrategyWithLearnerAndMaps with MarginMeasure {
   override lazy val toString = "Density Weighted b" + learner.limpa + beta + " (" + distance_name + ")"
   lazy val abr = "DW" + distance_name.take(3)
   //+ beta
   lazy val id = distance_name match {
      case "eucl" => 81000000 + convlid(learner.id)
      case "cheb" => 78000000 + convlid(learner.id)
      case "maha" => 79000000 + convlid(learner.id)
      case "manh" => 80000000 + convlid(learner.id)
   }


   protected def next(mapU: => Map[Pattern, Double], mapL: => Map[Pattern, Double], current_model: Model, unlabeled: Seq[Pattern], labeled: Seq[Pattern]) = {
      val us = unlabeled.size
      val selected = unlabeled maxBy {
         x =>
            val similarityU = mapU(x) / us
            (1 - margin(current_model)(m(x.id))) * math.pow(similarityU, beta)
      }
      selected
   }
}
