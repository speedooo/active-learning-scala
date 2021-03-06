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

package exp.query

import al.strategies._
import clean.lib.Ds
import ml.Pattern
import ml.classifiers._
import ml.models.Model
import util.{ALDatasets, Datasets, Tempo}

import scala.util.Random

object Queries_visualized extends App {
   //  val arff = "/home/davi/unversioned/experimentos/fourclusters.arff"
   val arff = "/home/davi/wcs/tese/toy.arff"
   val data = new Random().shuffle(Datasets.arff(arff).right.get)
   //  val data= new Random().shuffle(Ds("banana", readOnly = true).patterns)
   val train = data.take(2000)
   val test = data.drop(2000)

   println("train = " + train.length)

   //  println(trainp.groupBy(_.label).values.map(_.length))
   //  println(testp.groupBy(_.label).values.map(_.length))
   //  println(trainp.map(_.label))
   //  println(testp.map(_.label))
   val cc = c //OSELMdyn(compromise(), 0)

   //    VFDT()
   //NB()
   //    OSELMdyn(best(test.take(100), 0), 0)
   //    OSELMdyn(compromise(), 0)
   //    OSELMdyn(esPRESS(0), 0)
   //  c.build(train)
   //  sys.exit(1)

   //    EnsOSELMdyn(halfway(), 0, 1)
   //  EnsOSELMdyn(compromise(), 0, 1)
   //  EnsOSELMdyn(fullunderfitting(), 0, 1)
   //  EnsOSELMdyn(fulloverfitting(), 0, 1)
   //            C45()
   //    KNN(5, "eucl", data)

   val ts = test
   //    NBBatch()
   //  NB()
   //  RF(0, 5)
   val e =
   //    DensityWeightedLocalLabelUtility(c, train, "eucl", debug = true)
   //      DensityWeightedLabelUtility(c, train, "eucl", debug = true)
   //      DensityWeightedLocalUtility(c, train, "eucl", debug = true)
   //      DensityWeightedTrainingUtility(c, train, "eucl", debug = true)
   //           ClusterBased(train, debug = true) //143 10:178
   //          Margin(c, train, debug = true)
      Entropy(c, train, debug = true)
   //      Uncertainty(c, train, debug = true) //212 10:199
   //      RandomSampling(train, true)
   //        SGmulti(c, train, "consensus", debug = true) //143 10:146 ponto fraco: talvez seja mais sensível ao peso dos exemplos de fundo
   //        SGmulti(c, train, "majority", debug = true) //
   //      SGmultiJS(c, train, debug = true) //149 ponto fraco: não consulta quando concordam na dúvida
   //                       SGmultiMargin(c, train, debug = true) //168 ponto fraco: concentra demais as consultas na fronteira de decisão
   //                       DensityWeightedTrainingUtility(c, train, "cheb", debug = true)
   //    ExpErrorReductionMargin(c, train, "accuracy", 100, debug = true)
   //           QBCJS(c, train, debug = true) //2:168 10:143
   //           QBCJSMargin(c, train, debug = true) //2:168 10:168
   //        DensityWeighted(c, train, 1d, "eucl", debug = true)
   //      MahalaWeightedTrainingUtility(c, train, 1d, 1d, debug = true)
   //           MahalaWeightedRefreshedTrainingUtility(c, train, 1d, 1d, 200, debug = true)
   //               MahalaWeighted(c, train, 1d, debug = true)
   //           MahalaWeightedRefreshed(c, train, 1d, 100, debug = true)
   //                    DensityWeightedTrainingUtility(c, train, 1,1, "eucl", debug = true)
   //                     DensityWeightedTrainingUtility(c, train, 1,1, "manh", debug = true)
   //                      ExpErrorReduction(c, train, "entropy", 200, debug = true)
   //    ExpErrorReduction(c, train, "accuracy", 200, debug)
   //                 ExpErrorReduction(c, train, "gmeans", 100, debug = true)
   //           ExpModelChange(c, train, debug = true)
   //    RandomSampling(train, debug = true)
   var labeled = List[Pattern]()
   var model: Model = null

   def c = NinteraELM() //KNNBatch(75, "eucl", train, weighted = true) //CIELMBatch()


   //      MLP(20,0)
   //  CARTBag(0,20)
   //  C45Bag(0,10)
   //    RIPPER()

   Tempo.start
   for ((q, i) <- e.queries.zipWithIndex) {
      labeled = q :: labeled
      if (labeled.length <= labeled.head.nclasses) {
         if (labeled.length == labeled.head.nclasses) model = cc.build(labeled)
      } else {
         model = cc.update(model)(q)
         val acc = model.accuracy(test)
         println(" " + acc)
         if (acc > 7) {
            //0.82
            Tempo.print_stop
            Thread.sleep(1000)
            sys.exit(1)
         }
         Thread.sleep(100)
      }
   }
}
