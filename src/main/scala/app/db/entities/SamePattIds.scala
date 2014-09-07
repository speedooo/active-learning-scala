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

package app.db.entities

import java.util.Calendar

import al.strategies.{RandomSampling, Strategy}
import ml.Pattern
import ml.classifiers._
import util.{Lazy, ALDatasets, Datasets, Tempo}
import weka.filters.unsupervised.attribute.Standardize

import scala.collection.mutable
import scala.util.Random


/**
 * Verifica se a sequencia de ids dos patterns se mantém
 * entre carregar direto do ARFF ou do SQLite.
 */
object SamePattIds extends App {
  val l = "appendicitis,blogger,glioma16,fertility-diagnosis,planning-relax,qualitative-bankruptcy,lenses,acute-inflammations-urinary,lung-cancer,post-operative-patient,dbworld-subjects-stemmed,iris,robot-failure-lp3,zoo,leukemia-haslinger,dbworld-bodies-stemmed,volcanoes-d1,hepatitis,movement-libras-1,robot-failure-lp2,heart-disease-processed-switzerland,habermans-survival,robot-failure-lp4,robot-failure-lp1,hayes-roth,volcanoes-d3,teaching-assistant-evaluation,wine,lsvt-voice-rehabilitation,breast-tissue-6class,seeds,led7digit,heart-disease-processed-hungarian,ozone-eighthr,volcanoes-d4,molecular-promotor-gene,voting,breast-tissue-4class,statlog-heart,thyroid-newthyroid,monks3,breast-cancer-wisconsin,spectf-heart,volcanoes-d2,heart-disease-processed-cleveland,heart-disease-processed-va,steel-plates-faults,meta-data,lymphography,monks1,cardiotocography-10class,flare,robot-failure-lp5,spect-heart,flags,parkinsons,vertebra-column-2c,vertebra-column-3c,arcene,systhetic-control,ionosphere,dresses-sales,horse-colic-surgical,connectionist-mines-vs-rocks,glass,bupa,heart-disease-reprocessed-hungarian,dermatology,indian-liver-patient,mammographic-mass,ecoli,blood-transfusion-service,wholesale-channel,movement-libras-10,ozone-onehr,climate-simulation-craches,wdbc,user-knowledge,arrhythmia,volcanoes-e2,micro-mass-mixed-spectra,saheart,credit-approval,movement-libras,statlog-australian-credit,waveform-v1,pima-indians-diabetes,leaf,volcanoes-e4,volcanoes-e1,balance-scale,autoUniv-au6-cd1-400,volcanoes-a1,banknote-authentication,monks2,autoUniv-au7-cpd1-500,volcanoes-e5,connectionist-vowel-reduced,wine-quality-red,autoUniv-au7-700,volcanoes-a4,waveform-v2,micro-mass-pure-spectra,autoUniv-au6-250-drift-au6-cd1-500,annealing,statlog-german-credit-numeric,autoUniv-au1-1000,tic-tac-toe,statlog-vehicle-silhouettes,autoUniv-au6-1000,volcanoes-a2,ringnorm,statlog-german-credit,yeast-4class,qsar-biodegradation,volcanoes-e3,volcanoes-a3,autoUniv-au7-300-drift-au7-cpd1-800,connectionist-vowel,cnae-9,yeast,thyroid-hypothyroid,cmc,hill-valley-with-noise,mushroom-expanded,mushroom,hill-valley-without-noise,digits2,thyroid-sick-euthyroid,twonorm,cardiotocography-3class,robot-nav-sensor-readings-2,semeion,multiple-features,car-evaluation,statlog-image-segmentation,mfeat-fourier,robot-nav-sensor-readings-4,thyroid-allrep,thyroid-dis,thyroid-allhyper,thyroid-allhypo,thyroid-allbp,kr-vs-kp,thyroid-ann,wine-quality-white-5class,thyroid-sick,abalone-11class,volcanoes-b3,statlog-landsat-satellite,turkiye-student,molecular-splice-junction,wilt,abalone-3class,volcanoes-b5,volcanoes-b4,spambase,volcanoes-b1,wine-quality-5class,page-blocks,artificial-characters,banana,volcanoes-b6,robot-nav-sensor-readings-24,optdigits,texture,phoneme,first-order-theorem,volcanoes-b2,musk,pendigits,gas-drift-different-concentrations,eeg-eye-state,gas-drift,nursery,nursery-4class,magic".split(",")
  val res = l forall { d =>
    val da = Dataset("/home/davi/wcs/ucipp/uci/", false, true)(d)
    val db = Dataset("/home/davi/wcs/ucipp/uci/", false, true)(d)
    da.open()
    db.open()
    val a = Datasets.patternsFromSQLite(da.path)(da.database).right.get
    val b = Datasets.arff(true)(db.path + db.database + ".arff", false, false).right.get
    val sa = a.sameElements(b)
    //  a.toList take 10 foreach (x=>println(x.toStringCerto))
    //  println("-----")
    //  b.toList take 10 foreach (x=>println(x.toStringCerto))
    println(sa)
    println("")
    db.close()
    sa
  }
  println("tudo igual : " + res)
}