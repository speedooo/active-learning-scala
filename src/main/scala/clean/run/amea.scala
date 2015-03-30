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

import clean.lib._
import ml.Pattern
import ml.classifiers._
import weka.filters.Filter

import scala.collection.mutable

object amea extends Exp with LearnerTrait with StratsTrait with RangeGenerator {
   val context = "ameaApp"
   val arguments = superArguments
   val ignoreNotDone = false
   var outroProcessoVaiTerminarEsteDataset = false
   run()

   def poeNaFila(fila: mutable.Set[String], f: => String): Unit =
      try {
         fila += f
      } catch {
         case e: Throwable => justQuit(e.getMessage)
      }

   def op(ds: Ds, pool: Seq[Pattern], testSet: Seq[Pattern], fpool: Seq[Pattern], ftestSet: Seq[Pattern], learnerSeed: Int, run: Int, fold: Int, binaf: Filter, zscof: Filter) {
      val fila = mutable.Set[String]()
      if (ds.nclasses > maxQueries(ds)) ds.error(s"ds.nclasses ${ds.nclasses} > ${maxQueries(ds)} maxtimesteps!")
      else if (ds.isAliveByOtherJob(run, fold)) {
         outroProcessoVaiTerminarEsteDataset = true
         ds.log(s"Outro job está all-izando este pool ($run.$fold). Skipping all' for this pool...", 30)
      } else {
         ds.startbeat(run, fold)
         ds.log(s"Iniciando trabalho para pool $run.$fold ...", 30)
         for {
            learner <- learnersPool(pool, learnerSeed) ++ learnersFpool(learnerSeed)
            s <- stratsPool(learner, pool, pool) ++ stratsFpool(learner, pool, fpool)
            classif <- Seq(BestLearner(ds, learnerSeed, pool), BestLearnerCVPerPool(ds, run, fold, s), learner)
         } yield {
            lazy val (tmin, thalf, tmax, tpass) = ranges(ds)
            for ((ti, tf) <- Seq((tmin, thalf), (thalf, tmax), (tmin, tmax), (tmin, 49))) {
               poeNaFila(fila, ALCKappa(ds, s, classif, run, fold)(ti, tf).sqlToWrite(ds))
               poeNaFila(fila, ALCBalancedAcc(ds, s, classif, run, fold)(ti, tf).sqlToWrite(ds))
            }
            for (t <- tmin to tmax) {
               poeNaFila(fila, Kappa(ds, s, classif, run, fold)(t).sqlToWrite(ds))
            }
            val t = tpass
            poeNaFila(fila, BalancedAcc(ds, s, classif, run, fold)(t).sqlToWrite(ds))
         }
      }

      ds.log(fila.mkString("\n"), 10)
      if (fila.exists(_.startsWith("insert"))) ds.batchWrite(fila.toList)
      fila.clear()
   }

   def datasetFinished(ds: Ds) = {
      if (!outroProcessoVaiTerminarEsteDataset) {
         ds.markAsFinishedRun("amea2" + (stratsFpool(NoLearner()) ++ stratsPool(NoLearner()) ++ allLearners()).map(x => x.limpa).mkString)
         ds.log("Dataset marcado como terminado !", 50)
      }
      outroProcessoVaiTerminarEsteDataset = false
   }

   def isAlreadyDone(ds: Ds) = ds.isFinishedRun("amea2" + (stratsFpool(NoLearner()) ++ stratsPool(NoLearner()) ++ allLearners()).map(x => x.limpa).mkString)

   def end(res: Map[String, Boolean]): Unit = {
   }
}