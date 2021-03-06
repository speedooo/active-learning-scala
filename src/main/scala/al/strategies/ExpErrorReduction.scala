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
import util.{ALDatasets, Datasets, Tempo}

import scala.util.Random

/**
 * Defaults to Uncertainty if it misses the label.
 * Resuming is not exact, but who cares?
 * When resuming queries it is impossible to know if the last label estimate was right. It is assumed it hit.
 * @param learner
 * @param pool
 * @param criterion
 * @param sample
 * @param debug
 */
case class ExpErrorReduction(learner: Learner, pool: Seq[Pattern], criterion: String, sample: Int, debug: Boolean = false)
   extends StrategyWithLearner with Sample with EntropyMeasure {
   lazy val criterionInt = criterion match {
      case "entropy" => Ventropy
      case "accuracy" => Vaccuracy
      //    case "gmeans" => Vgmeans
      case "gmeans+residual" => VgmeansResidual
   }
   override val toString = "Expected Error Reduction s" + sample + " (" + criterion + ")"
   val abr = "EERnoM" + criterion.take(3)
   val id = -350
   //Strategy with empty pool exists only to provide its name.
   val Ventropy = 0
   val Vaccuracy = 1
   val Vgmeans = 2
   val VgmeansResidual = 3

   protected def next(current_model: Model, unlabeled: Seq[Pattern], labeled: Seq[Pattern]) = {
      val res = if (labeled.last.missed) {
         Uncertainty(learner, distinct_pool).next(current_model, unlabeled, labeled) //todo: for multiclass, margin is better than unc. but the original paper don't do it this way.
      } else {
         val unlabeledSize = unlabeled.size
         val rnd = new Random(unlabeledSize)
         val unlabeledSamp = if (unlabeledSize > sample_internal) rnd.shuffle(unlabeled).take(sample_internal) else unlabeled
         lazy val optimistic_patterns = unlabeledSamp.map { p =>
            p.relabeled_reweighted(current_model.predict(p), 1, new_missed = false)
         }.toVector

         val (selected, label_estimate, _) = (
            for (pattern <- unlabeledSamp; c <- 0 until nclasses) yield {
               lazy val artificially_labeled_pattern = pattern.relabeled_reweighted(c, 1, new_missed = false)
               lazy val art_model = learner.update(current_model)(artificially_labeled_pattern)
               criterionInt match {
                  case Ventropy => (pattern, c, criterion_entropy(art_model, unlabeledSamp))
                  case Vgmeans => (pattern, c, 1 - criterion_gmeans(art_model, optimistic_patterns))
                  case VgmeansResidual => (pattern, c, 1 - criterion_gmeansResidual(art_model, optimistic_patterns))
                  case Vaccuracy => (pattern, c, 1 - criterion_accuracy(art_model, optimistic_patterns))
               }
            }).minBy(_._3)
         selected.relabeled_reweighted(selected.label, selected.weight, label_estimate != selected.label)

      }
      res
   }

   private def criterion_accuracy(m: Model, label_estimated_patterns: Vector[Pattern]) =
      m.hits(label_estimated_patterns) / label_estimated_patterns.length.toDouble

   private def criterion_gmeans(m: Model, label_estimated_patterns: Vector[Pattern]) = {
      val pseudo_accuracies_per_class = (0 until nclasses) map {
         c =>
            val only_this_class = label_estimated_patterns.filter(_.label == c)
            val hits = only_this_class count m.hit
            hits.toDouble / only_this_class.length
      }
      //    math.sqrt(pseudo_accuracies_per_class.product)
      pseudo_accuracies_per_class.product //faster
   }

   private def criterion_gmeansResidual(m: Model, label_estimated_patterns: Vector[Pattern]) = {
      val pseudo_accuracies_per_class = (0 until nclasses) map {
         c =>
            val only_this_class = label_estimated_patterns.filter(_.label == c)
            val hits = only_this_class count m.hit
            hits.toDouble / only_this_class.length
      }
      pseudo_accuracies_per_class.map(_ + 0.00001).product
   }

   /**
    * O exemplo que causa a menor entropia é o desejado,
    * pois isso indica que ele e o rótulo vão conferir mais certeza com relação ao pool.
    * @param m
    * @param unlabeled
    * @return
    */
   private def criterion_entropy(m: Model, unlabeled: Seq[Pattern]) = unlabeled.map {
      u => val d = m.distribution(u)
         entropy(d)
   }.sum
}
