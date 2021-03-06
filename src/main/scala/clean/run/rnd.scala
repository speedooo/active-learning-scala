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

package clean.run

import al.strategies.RandomSampling
import clean.lib.{Ds, Exp}
import ml.Pattern
import ml.classifiers._
import weka.filters.Filter

object rnd extends Exp {
   val arguments = superArguments
   val context = "rndApp"
   val ignoreNotDone = false
   run()

   def strats(pool: Seq[Pattern], seed: Int) = List(RandomSampling(pool))

   def isAlreadyDone(ds: Ds) = false //ds.isQCalculated

   def op(ds: Ds, pool: Seq[Pattern], testSet: Seq[Pattern], fpool: Seq[Pattern], ftestSet: Seq[Pattern], learnerSeed: Int, run: Int, fold: Int, binaf: Filter, zscof: Filter) {
      val poolSize = pool.size
      //queries
      ds.log("queries")
      val queries = if (ds.areQueriesFinished(poolSize, RandomSampling(pool), run, fold, null, null, completeIt = false, poolSize)) {
         ds.log(s"Queries already done for rnd at pool $run.$fold. Retrieving from disk.")
         ds.queries(RandomSampling(pool), run, fold, null, null)
      } else ds.writeQueries(RandomSampling(pool), run, fold, Int.MaxValue) //pode dar conflito de insercao aqui se existir incompleto, pois queries agora retorna false, nao mais quit()

      //hits
      ds.log("hits")
      val hitspoolsize = poolSize - ds.nclasses + 1
      Seq(NB(), KNNBatchb(5, "eucl", pool, weighted = true), C45()) foreach { learner =>
         if (ds.areHitsFinished(poolSize, testSet, RandomSampling(pool), learner, run, fold, null, null, completeIt = false, hitspoolsize)) ds.log(s"Hits done for rnd/$learner at pool $run.$fold.")
         else ds.writeHits(poolSize, testSet, queries.toVector, RandomSampling(pool), run, fold, hitspoolsize)(learner) //pode dar conflito de insercao aqui se existir incompleto, pois queries agora retorna false, nao mais quit()
      }
   }

   def datasetFinished(ds: Ds) {
      //      //Q
      //      ds.calculaQ(runs, folds)
      //      ds.log(s"Q: ${ds.Q}\n", 20)
   }

   def end(res: Map[String, Boolean]): Unit = {
   }
}
