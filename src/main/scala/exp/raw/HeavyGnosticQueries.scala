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

package exp.raw

import al.strategies._
import app.ArgParser
import app.db.Dataset
import ml.Pattern
import ml.classifiers.NB
import util.Datasets
import weka.filters.unsupervised.attribute.Standardize

object HeavyGnosticQueries extends CrossValidation with App {
  val args1 = args
  val desc = "Version " + ArgParser.version + "\n Generates queries for the given list of datasets according to provided hardcoded heavy GNOSTIC " +
    "strategies (EER entr, acc and gmeans) mostly due to the fact that they are slow and are stopped by time limit of 8000s;\n"
  val (path, datasetNames0, learner) = ArgParser.testArgsWithLearner(className, args, desc)
  val datasetNames = datasetNames0.filter { d =>
    val db = Dataset(path, createOnAbsence = false, readOnly = true)(d)
    val res = completeForQCalculation(db) && !hitsComplete(learner(-1, -1, Seq()))(db)
    db.close()
    res
  }
  run(ff)

  def strats0(run: Int, pool: Seq[Pattern]) = List(
    ExpErrorReduction(learner(pool.length / 2, run, pool), pool, "entropy", samplingSize),
    ExpErrorReductionMargin(learner(pool.length / 2, run, pool), pool, "entropy", samplingSize),
    ExpErrorReduction(learner(pool.length / 2, run, pool), pool, "accuracy", samplingSize),
    ExpErrorReduction(learner(pool.length / 2, run, pool), pool, "gmeans", samplingSize)
  )

  def ff(db: Dataset, run: Int, fold: Int, pool: => Seq[Pattern], testSet: => Seq[Pattern], f: => Standardize) {
    val Q = q_notCheckedIfHasAllRndQueries(db)
    strats(run, pool) foreach (strat => db.saveQueries(strat, run, fold, f, timeLimitSeconds, Q))
  }
}
