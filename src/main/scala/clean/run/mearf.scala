///*
//
//active-learning-scala: Active Learning library for Scala
//Copyright (c) 2014 Davi Pereira dos Santos
//
//   This program is free software: you can redistribute it and/or modify
//   it under the terms of the GNU General Public License as published by
//   the Free Software Foundation, either version 3 of the License, or
//   (at your option) any later version.
//
//   This program is distributed in the hope that it will be useful,
//   but WITHOUT ANY WARRANTY; without even the implied warranty of
//   MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
//   GNU General Public License for more details.
//
//   You should have received a copy of the GNU General Public License
//   along with this program.  If not, see <http://www.gnu.org/licenses/>.
//*/
//
//package clean.run
//
//import al.strategies.{Majoritary, Passive, SVMmultiLinear}
//import clean.lib._
//import ml.Pattern
//import ml.classifiers._
//import weka.filters.Filter
//
//import scala.collection.mutable
//
//object mearf extends Exp with LearnerTrait with StratsTrait with Lock with CM with RangeGenerator {
//   val context = "mearfApp"
//   val arguments = superArguments ++ Seq("p:passivas")
//   val ignoreNotDone = false
//   var acabou = true
//   val strats = stratsSemLearnerExterno_FilterFree(Seq()) ++ stratsSemLearnerExterno_FilterDependent(Seq()).dropRight(3) ++ stratsComLearnerExterno_FilterFree(Seq(), NoLearner()) ++ stratsComLearnerExterno_FilterDependent(Seq(), NoLearner())
//   run()
//
//   def poeNaFila(fila: mutable.Set[String], f: => String): Unit =
//      try {
//         fila += f
//      } catch {
//         case e: Throwable => acabou = false
//      }
//
//   def op(ds: Ds, pool: Seq[Pattern], testSet: Seq[Pattern], fpool: Seq[Pattern], ftestSet: Seq[Pattern], learnerSeed: Int, run: Int, fold: Int, binaf: Filter, zscof: Filter) {
//      val fila = mutable.Set[String]()
//      //      //passiva
//      if (passivas) {
//         val poolt = pool.take(3000)
//         for (learner <- Seq(RF(learnerSeed))) {
//            val k = Kappa(ds, Passive(poolt), learner, run, fold)(-1)
//            val b = BalancedAcc(ds, Passive(poolt), learner, run, fold)(-1)
//            if (!k.existia || !b.existia) {
//               val model = learner.build(poolt)
//               val CM = model.confusion(testSet)
//               poeNaFila(fila, k.sqlToWrite(ds, CM))
//               poeNaFila(fila, b.sqlToWrite(ds, CM))
//               if (fila.exists(_.startsWith("insert"))) ds.batchWrite(fila.toList)
//               fila.clear()
//            }
//         }
//         if (fila.exists(_.startsWith("insert"))) ds.batchWrite(fila.toList)
//         fila.clear()
//      } else {
//         lazy val (tmin, thalf, tmax, tpass) = ranges(ds)
//         for (strat <- strats; learner <- Seq(RF(learnerSeed)); (ti, tf) <- Seq((tmin, thalf), (thalf, tmax), (tmin, tmax), (tmin, 49))) {
//            strat match {
//               case Majoritary(Seq(), false) => //| SVMmulti(Seq(), "KFFw", false) | SVMmulti(Seq(), "BALANCED_EEw", false) => //jah foi acima
//               case s =>
//                  if (!Global.gnosticasComLearnerInterno.contains(strat.id) || (strat.id == 3292212 && learner.id == 773)) {
//                     poeNaFila(fila, ALCKappa(ds, s, learner, run, fold)(ti, tf).sqlToWrite(ds))
//                     poeNaFila(fila, ALCBalancedAcc(ds, s, learner, run, fold)(ti, tf).sqlToWrite(ds))
//                  }
//            }
//         }
//         for (strat <- strats; learner <- Seq(RF(learnerSeed)); t <- tmin to tmax) {
//            strat match {
//               case Majoritary(Seq(), false) => //| SVMmulti(Seq(), "KFFw", false) | SVMmulti(Seq(), "BALANCED_EEw", false) => //jah foi acima
//               case s =>
//                  if (!Global.gnosticasComLearnerInterno.contains(strat.id) || (strat.id == 3292212 && learner.id == 773)) {
//                     poeNaFila(fila, Kappa(ds, s, learner, run, fold)(t).sqlToWrite(ds))
//                     poeNaFila(fila, BalancedAcc(ds, s, learner, run, fold)(t).sqlToWrite(ds))
//                  }
//            }
//         }
//         for (strat <- strats; learner <- Seq(RF(learnerSeed))) {
//            val t = tpass
//            strat match {
//               case Majoritary(Seq(), false) => // | SVMmulti(Seq(), "KFFw", false) | SVMmulti(Seq(), "BALANCED_EEw", false) => //jah foi acima
//               case s =>
//                  if (!Global.gnosticasComLearnerInterno.contains(strat.id) || (strat.id == 3292212 && learner.id == 773)) {
//                     poeNaFila(fila, Kappa(ds, s, learner, run, fold)(t).sqlToWrite(ds))
//                     poeNaFila(fila, BalancedAcc(ds, s, learner, run, fold)(t).sqlToWrite(ds))
//                  }
//            }
//         }
//         ds.log(fila.mkString("\n"), 10)
//         if (fila.exists(_.startsWith("insert"))) {
//            val sqls = fila.toList.distinct.filter(_.startsWith("insert"))
//            try {
//               ds.batchWrite(sqls)
//            } catch {
//               case e: Throwable => sqls.sorted foreach println
//                  justQuit("batchWrite:" + e.getMessage)
//            }
//         }
//         fila.clear()
//      }
//   }
//
//   def datasetFinished(ds: Ds) {
//      if (acabou) {
//         ds.markAsFinishedMea("k" + passivas + strats.map(_.limpa).mkString + Seq(RF()).map(_.limpa).mkString)
//         ds.log("Dataset marcado como terminado !", 50)
//      }
//      acabou = true
//   }
//
//   def isAlreadyDone(ds: Ds) = {
//      ds.isFinishedMea("k" + passivas + strats.map(_.limpa).mkString + Seq(RF()).map(_.limpa).mkString)
//   }
//
//   def end(res: Map[String, Boolean]): Unit = {
//      println(s"prontos. \n${args.toList}")
//   }
//}
