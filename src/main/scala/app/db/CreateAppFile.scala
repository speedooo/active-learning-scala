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

package app.db

import java.io.File

import al.strategies._
import app.ArgParser
import app.db.entities.AppFile
import exp.query.Heavy._
import ml.classifiers._

object CreateAppFile extends App {
  //commented code: aproveitar no futuro qnd for gerar tabela de meta-atributos
  //val (path, names) = ArgParser.testArgs(getClass.getSimpleName.dropRight(1), args, 2, "Version " + 0.2 + ". Creates the SQLite file for the whole als app.")
  //case class MetaAtt(dataset: Dataset, name: String, value: Float) extends Entity

  val sampleSize = 500
  val sampleSizePerfect = 1000
  val strats = List(
    RandomSampling(Seq()),
    ClusterBased(Seq()),
    Uncertainty(NoLearner(), Seq()),
    Entropy(NoLearner(), Seq()),
    Margin(NoLearner(), Seq()),
    new SGmulti(NoLearner(), Seq(), "consensus"),
    new SGmulti(NoLearner(), Seq(), "majority"),
//    new SGmultiJS(NoLearner(), Seq()),
    ExpErrorReduction(NoLearner(), Seq(), "entropy", sampleSize),
    ExpErrorReduction(NoLearner(), Seq(), "accuracy", sampleSize),
    DensityWeightedTrainingUtility(NoLearner(), Seq(), "cheb"),
    DensityWeightedTrainingUtility(NoLearner(), Seq(), "eucl"),
    DensityWeightedTrainingUtility(NoLearner(), Seq(), "maha"),
    DensityWeightedTrainingUtility(NoLearner(), Seq(), "manh"),
    MahalaWeighted(NoLearner(), Seq(), 1),
    MahalaWeightedRefreshed(NoLearner(), Seq(), 1, sampleSize),
    MahalaWeightedTrainingUtility(NoLearner(), Seq(), 1, 1),
    MahalaWeightedRefreshedTrainingUtility(NoLearner(), Seq(), 1, 1, sampleSize),
    Optimal(NoLearner(), Seq(), sampleSizePerfect),
    DensityWeighted(NoLearner(), Seq(), 1, "eucl"),
    ExpErrorReduction(NoLearner(), Seq(), "gmeans", sampleSize),
    ExpErrorReductionMargin(NoLearner(), Seq(), "entropy", sampleSize),
    SVMmultiLinear(Seq(), "SELF_CONF"),
    SVMmultiLinear(Seq(), "KFF"),
    SVMmultiLinear(Seq(), "BALANCED_EE"),
    SVMmultiLinear(Seq(), "SIMPLE"),
    ExpErrorReductionMargin(NoLearner(), Seq(), "gmeans+residual", samplingSize),
    ExpErrorReductionMargin(NoLearner(), Seq(), "accuracy", samplingSize)
  )

  //ensures there is no previous file
  val alsFileStr = ArgParser.appPath + "app.db"
  val dbOriginal = new File(alsFileStr)
  if (dbOriginal.exists) {
    println("File " + dbOriginal + " already exists! Delete it first.")
    sys.exit(1)
  }

  val learners = Seq(NoLearner(), KNNBatchb(5, "eucl", Seq(), weighted = true), KNNBatchb(5, "manh", Seq()),
    KNNBatchb(1, "eucl", Seq()),
    IELM(), IELMEnsemble(), EIELM(), CIELM(), ECIELM(),
    interaELM(),
    LASVM(),
    SVMLibDegree1(),
    NB(), C45(), VFDT() //"semzscore"
  )

  //opens (creates) app.db
  val af = AppFile(createOnAbsence = true)
  af.open()
  af.createOtherTables()
  af.createTableOfLearners(learners)
  af.createTableOfStrategies(strats)
  af.save()
  af.close()
}
