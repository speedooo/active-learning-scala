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

package clean.meta

import java.io.FileWriter

import clean.lib._
import ml.Pattern
import ml.classifiers._
import org.apache.commons.math3.stat.correlation.SpearmansCorrelation
import util.{Datasets, Stat, StatTests}

import scala.collection.mutable
import scala.util.Random

object arffMeta extends AppWithUsage with StratsTrait with LearnerTrait with RangeGenerator with FilterTrait with Lock {
   /*
   "Rank" => prediz ranking das strats
   "Ties" => prediz vencedores empatados (via multirrótulo no ARFF) com ELM
             se a predição estiver entre os empatados, então acertou
   "TiesDup" => prediz vencedores empatados (via repetição de exemplos no ARFF)
                com learners que façam contagem (NB, C45, KNN, VFDT)
                se a predição estiver entre os empatados, então acertou
   "Winner" => prediz apenas o melhor
   "Acc" => prediz acc em cada strat com ELM

   Escolher mais abaixo se sorteia learner, budget ou nada.
   */
   //      val modo = "TiesDup"
   val modo = "Winner"
   val arq = s"/home/davi/wcs/ucipp/uci/metaAcc$modo.arff"
   val context = "metaAttsAccApp"
   val arguments = superArguments ++ List("learners:nb,5nn,c45,vfdt,ci,...|eci|i|ei|in|svm")
   val measure = ALCKappa
   //   val measure = ALCBalancedAcc
   val redux = true
   run()

   def ff(x: Double) = (x * 100).round / 100d

   override def run() = {
      super.run()
      val mapaAtts = mutable.Map[Ds, List[Double]]()
      val mapaSuav = mutable.Map[(Ds, Learner), Double]()
      val strats = if (redux) stratsForTreeUltraRedux().dropRight(4) else stratsForTree()
      val ss = strats.map(_.abr).toVector
      val metadata0 = for {
         name <- datasets.toList.take(500).par
         (ti, tf, budix) <- {
            val ds = Ds(name, readOnly = true)
            ds.open()

            //escolher se sorteia budget
            //            val tmp = maxRange(ds, 2, 200) // <-verificar trocar p/ 200?
            //            ds.close()
            //            Seq((tmp._1, tmp._2, 0))

            //            val tmp = ranges(ds, 2, 200) // <- verificar!!! verificar tb argumentos do programa!!!
            //            ds.close()
            //            Seq(tmp.zipWithIndex.map(x => (x._1._1, x._1._2, x._2)).apply(rnd.nextInt(2)))

            //varia budget(cuidado: válido apenas para TiesDup, Winner ou outro modo que faça o LOO por patterns agrupados)
            val (tmin, thalf, tmax, tpass) = ranges(ds)
            ds.close()
            Seq((tmin, thalf, "\"$\\cent\\leq 50$\""), (tmin, thalf, "baixo"), (thalf, tmax, "alto"))
         }

         //varia learner(cuidado: válido apenas para TiesDup, Winner ou outro modo que faça o LOO por patterns agrupados)
         l <- learners(learnersStr).par
      } yield {
         val ds = Ds(name, readOnly = true)
         println(s"${l.abr} $ds")
         ds.open()
         acquire()
         val metaAtts = mapaAtts.getOrElseUpdate(ds, ds.metaAtts)
         val suav = mapaSuav.getOrElseUpdate((ds, l), ds.suavidade(l))
         release()
         //escolher se sorteia, fixa ou varia learner (pra variar, comentar abaixo e descomentar mais acima)
         //         val l = allLearners()(rnd.nextInt(allLearners().size)) //warning: estrats de learner único permanecem sempre com seus learners (basicamente SVMmulti e Majoritary)
         //         val l = KNNBatch(5, "eucl", Seq(), weighted = true)

         val seqratts = (for (r <- 0 until Global.runs; f <- 0 until Global.folds) yield ds.attsFromR(r, f)).transpose.map(_.toVector)
         val rattsmd = seqratts map Stat.media_desvioPadrao
         val (rattsm, _) = rattsmd.unzip

         val res = modo match {
            case "Acc" => //prediz acc em cada strat
               println(s"filtrar sVMmulti com learner errado");
               ???
               val vs = for {
                  s <- strats
               } yield {
                  val le = if (s.id >= 17 && s.id <= 21 || s.id == 968 || s.id == 969) s.learner else l
                  val ms = for {
                     r <- 0 until Global.runs
                     f <- 0 until Global.folds
                  } yield measure(ds, s, le, r, f)(ti, tf).read(ds).getOrElse {
                        ds.log(s" base incompleta para intervalo [$ti;$tf] e pool ${(s, le, r, f)}.", 40)
                        NA
                     }
                  s.abr -> Stat.media_desvioPadrao(ms.toVector)
               }
               if (vs.exists(x => x._2._1 == NA)) Seq()
               else Seq((ds.metaAtts ++ rattsm, l.abr, "\"multilabel" + vs.map(_._2._1).mkString(",") + "\"", budix, l.attPref, l.boundaryType, suav))
            case "TiesDup" => //qualquer metalearner que conte prediz vencedores empatados
               val vs = for {
                  r <- 0 until runs
                  f <- 0 until folds
               //                  duplicadorDeAmostra <- 0 to 1
               } yield {
                  val poolStr = (100 * r + f).toString
                  val medidas = for {
                     s <- strats
                  } yield if (l.id != 5 && (s.id >= 17 && s.id <= 21 || s.id == 968 || s.id == 969)) None
                     else Some(measure(ds, s, l, r, f)(ti, tf).read(ds).getOrElse {
                        ds.log(s" base incompleta para intervalo [$ti;$tf] e pool ${(s, l, r, f)}.", 40)
                        NA
                     })
                  poolStr -> medidas.flatten
               }
               if (vs.exists(_._2.contains(NA))) ???
               val winners = StatTests.clearWinners(vs, ss)
               ss.map { x =>
                  if (winners.contains(x)) Option(metaAtts ++ rattsm, "na", x, budix, l.attPref, l.boundaryType, suav)
                  //if (winners.contains(x)) Option(metaAtts ++ rattsm, l.abr, x, budix, 0, 0, 0)
                  else None
               }.flatten
            case "Ties" => //prediz vencedores empatados
               println(s"filtrar sVMmulti com learner errado");
               ???
               val vs = for {
                  r <- 0 until runs
                  f <- 0 until folds
               //                  duplicadorDeAmostra <- 0 to 1
               } yield {
                  val poolStr = (100 * r + f).toString
                  val medidas = for {
                     s <- strats
                     le = if (s.id >= 17 && s.id <= 21 || s.id == 968 || s.id == 969) s.learner else l
                  } yield measure(ds, s, le, r, f)(ti, tf).read(ds).getOrElse {
                        ds.log(s" base incompleta para intervalo [$ti;$tf] e pool ${(s, le, r, f)}.", 40)
                        -2d
                     }
                  poolStr -> medidas
               }
               val winners = StatTests.clearWinners(vs, ss)
               val binario = ss.map(x => if (winners.contains(x)) 1 else 0)
               if (vs.exists(x => x._2.contains(-2d))) Seq()
               else Seq((ds.metaAtts ++ rattsm, l.abr, "\"multilabel" + binario.mkString(",") + "\"", budix, 0, 0, 0)) //l.attPref, l.boundaryType, suav))
            case "Rank" => //prediz ranking
               println(s"filtrar sVMmulti com learner errado");
               println(s"arrumar ranking, pois não está verificando empate de posições (ou nem arrumar caso não existam empates)")
               ???
               val vs = for {
                  s <- strats
               } yield {
                  val le = if (s.id >= 17 && s.id <= 21 || s.id == 968 || s.id == 969) s.learner else l
                  val ms = for {
                     r <- 0 until Global.runs
                     f <- 0 until Global.folds
                  } yield measure(ds, s, le, r, f)(ti, tf).read(ds).getOrElse {
                        ds.log(s" base incompleta para intervalo [$ti;$tf] e pool ${(s, le, r, f)}.", 40)
                        -2d
                     }
                  s.abr -> Stat.media_desvioPadrao(ms.toVector)
               }
               lazy val rank = vs.map(_._2._1).zipWithIndex.sortBy(_._1).map(_._2).zipWithIndex.sortBy(_._1).map(_._2)
               if (vs.exists(x => x._2._1 == -2d)) Seq()
               else Seq((ds.metaAtts ++ rattsm, l.abr, "\"multilabel" + rank.mkString(",") + "\"", budix, l.attPref, l.boundaryType, suav))
            case "Winner" => //qualquer learner prediz apenas o melhor
               val vs0 = for {
                  s <- strats
               } yield if (l.id != 5 && (s.id >= 17 && s.id <= 21 || s.id == 968 || s.id == 969)) None
                  else {
                     val ms = for {
                        r <- 0 until Global.runs
                        f <- 0 until Global.folds
                     } yield measure(ds, s, l, r, f)(ti, tf).read(ds).getOrElse {
                           ds.log(s" base incompleta para intervalo [$ti;$tf] e pool ${(s, l, r, f)}.", 40)
                           NA
                        }
                     Some(s.abr -> Stat.media_desvioPadrao(ms.toVector))
                  }
               val vs = vs0.flatten
               if (vs.exists(_._2._1 == NA)) ???
               //               else Seq((metaAtts ++ rattsm, "na", vs.maxBy(_._2._1)._1, budix, l.attPref, l.boundaryType, suav))
               else Seq((metaAtts ++ rattsm, "na", vs.maxBy(_._2._1)._1, budix, 0, 0, 0))
            //            else Seq("(metaAtts ++ rattsm, l.abr, vs.maxBy(_._2._1)._1, budix, 0, 0, 0))
            //else Seq((ds.metaAtts ++ rattsm, l.abr, vs.maxBy(_._2._1)._1, budix, l.attPref, l.boundaryType, suav))
         }

         ds.close()
         res
      }
      val metadata = metadata0.flatten.toList

      //cria ARFF
      val pred = metadata.map(_._3)
      val labels = pred.distinct.sorted
      val data = metadata.map { case (numericos, learner, vencedores, budget, attPref, boundaryType, suavidade) =>
         numericos.mkString(",") + s",$budget,$learner,$attPref,$boundaryType,$suavidade," + "\"" + vencedores + "\""
      }
      val header = List("@relation data") ++
         nonHumanNumAttsNames.split(",").map(i => s"@attribute $i numeric") ++
         List("@attribute \"orçamento\" {\"$\\cent\\leq 50$\",baixo,alto}", "@attribute aprendiz {" +
            learners(learnersStr).map(x => "\"" + x.abr + "\"").mkString(",") + "}", "@attribute \"atributo aceito\" {\"numérico\",\"nominal\",\"ambos\"}", "@attribute \"fronteira\" {\"rígida\",\"flexível\",\"nenhuma\"}", "@attribute suavidade numeric", "@attribute class {" + labels.map(x => "\"" + x + "\"").mkString(",") + "}", "@data")

      val pronto = header ++ data
      pronto foreach println

      val fw = new FileWriter(arq)
      pronto foreach (x => fw.write(s"$x\n"))
      fw.close()
      println(s"qtd de metaexemplos: ${data.size}")

      //processa arff
      if (modo == "Ties" || modo == "Acc") Datasets.arff(arq) match {
         case Left(str) => error("problemas abrindo arff")
         case Right(patterns) =>
            println(s"${patterns.size}")
            val nestedRes = (0 until runs).par map { run =>
               val shuffled = new Random(run).shuffle(patterns)
               Datasets.kfoldCV(shuffled, k = 94) { (tr, ts, fold, minSize) =>
                  //                  println(s"Pool $run.$fold (${tr.size} instances) ...")
                  val learnerSeed = run * 10000 + fold

                  val (fpool, binaf, zscof) = criaFiltro(tr, fold)
                  val ftestSet = aplicaFiltro(ts, fold, binaf, zscof)

                  val m = NinteraELM(learnerSeed).build(fpool)

                  val hitsELM = ftestSet map { p => p.nominalSplit(m.predict(p).toInt) == p.nominalSplit.max}
                  val accELM = hitsELM.count(_ == true) / ftestSet.size.toDouble

                  val hitsRnd = ftestSet.zipWithIndex map { case (p, idx) => p.nominalSplit(idx % p.nclasses) == p.nominalSplit.max}
                  val accRnd = hitsRnd.count(_ == true) / ftestSet.size.toDouble

                  val hitsMaj = ftestSet map { p => p.nominalSplit(2) == p.nominalSplit.max}
                  val accMaj = hitsMaj.count(_ == true) / ftestSet.size.toDouble

                  println(s"$accELM $accMaj")
               }
            }
         //            val res=nestedRes.flatten
         //            res foreach println
      }

      if (modo == "Rank") Datasets.arff(arq) match {
         case Left(str) => error("problemas abrindo arff")
         case Right(patterns) =>
            println(s"Falta comparar com ranking medio.")
            ???
            println(s"${patterns.size}")
            val nestedRes = (0 until runs).par map { run =>
               val shuffled = new Random(run).shuffle(patterns)
               Datasets.kfoldCV(shuffled, k = 94) { (tr, ts, fold, minSize) =>
                  //                  println(s"Pool $run.$fold (${tr.size} instances) ...")
                  val learnerSeed = run * 10000 + fold

                  val (fpool, binaf, zscof) = criaFiltro(tr, fold)
                  val ftestSet = aplicaFiltro(ts, fold, binaf, zscof)

                  val m = NinteraELM(learnerSeed).build(fpool)

                  val hitsELM = ftestSet map { p =>
                     val spear = new SpearmansCorrelation().correlation(m.output(p), p.nominalSplit)
                     val spearMaj = new SpearmansCorrelation().correlation(new Random(System.currentTimeMillis()).shuffle(Array(0d, 3, 2, 4, 1, 5, 6).toList).toArray, p.nominalSplit)
                     //                     println(m.output(p).toList)
                     //                     println(p.nominalSplit.toList)
                     println(s"$spear $spearMaj")
                     p.nominalSplit(m.predict(p).toInt) == p.nominalSplit.max
                  }
                  val accELM = hitsELM.count(_ == true) / ftestSet.size.toDouble

                  val hitsRnd = ftestSet.zipWithIndex map { case (p, idx) =>
                     p.nominalSplit(idx % p.nclasses) == p.nominalSplit.max
                  }
                  val accRnd = hitsRnd.count(_ == true) / ftestSet.size.toDouble

                  val hitsMaj = ftestSet map { p =>
                     p.nominalSplit(2) == p.nominalSplit.max
                  }
                  val accMaj = hitsMaj.count(_ == true) / ftestSet.size.toDouble

                  //                  println(s"$accELM $accMaj")
               }
            }
         //            val res=nestedRes.flatten
         //            res foreach println
      }

      //NB, C45, KNN, VFDT
      if (modo == "Winner" || modo == "TiesDup") Datasets.arff(arq, dedup = false) match {
         case Left(str) => error("problemas abrindo arff:" + str)
         case Right(patterns) =>
            val grupos = patterns.groupBy(x => x.vector.dropRight(5)).map(_._2).toArray
            println(s"qtd de grupos = ${grupos.size}")

            val accs = Datasets.LOO(grupos) { (tr: Seq[Vector[Pattern]], ts: Vector[Pattern]) =>
               val ls = Seq(C45(),
                  NB(),
                  KNNBatch(5, "eucl", tr.flatten, weighted = true),
                  KNNBatch(5, "eucl", tr.flatten, weighted = false),
                  //                  KNNBatch(50, "eucl", tr.flatten, weighted = true),
                  //                  SVMLib(), //não tira proveito de exemplos duplicados
                  //                  NinteraELM(), //não tira proveito de exemplos duplicados
                  Maj())
               val trios = ls map {
                  case l: NinteraELM =>
                     val (fpool, binaf, zscof) = criaFiltro(tr.flatten, 1)
                     val ftestSet = aplicaFiltro(ts, 1, binaf, zscof)
                     (l, fpool, ftestSet)
                  case l => (l, tr.flatten, ts)
               }
               modo match {
                  case "TiesDup" => trios map { case (lea, tre, tes) =>
                     val m = lea.build(tre)
                     val hitsByDupGroup = tes.groupBy(x => x) map { case (pat, pats) =>
                        if (pats.map(_.label).contains(m.predict(pat))) 1d else 0d
                     }
                     hitsByDupGroup.sum / hitsByDupGroup.size
                  }
                  case "Winner" => trios map { case (lea, tre, tes) =>
                     val m = lea.build(tre)
                     m.accuracy(tes)
                  }
               }
            }
            println(accs.transpose.map(x => x.sum / x.size).mkString(" "))
         //Winner:
         //                          c45                 nb                  5nnw                5nn                 maj
         //sem rf, com 3atts sem lea 0.24881796690307328 0.07860520094562644 0.24468085106382984 0.25827423167848684 0.18853427895981079
         //com rf, com 3atts sem lea 0.21428571428571405 0.06079027355623095 0.24113475177304950 0.25785207700101280 0.19199594731509614
         //sem rf, com 3atts com lea 0.24881796690307328 0.07860520094562644 0.24468085106382984 0.25827423167848684 0.18853427895981079
         //com rf, com 3atts com lea 0.21428571428571405 0.06079027355623095 0.24113475177304950 0.25785207700101280 0.19199594731509614
         //sem rf, sem 3atts sem lea
         //com rf, sem 3atts sem lea
         //sem rf, sem 3atts com lea
         //com rf, sem 3atts com lea

         //TiesDup:
         //                          c45                 nb                  5nnw                5nn                 maj
         //sem rf, sem 3atts com lea
         //com rf, sem 3atts com lea
         //sem rf, com 3atts sem lea
         //com rf, com 3atts sem lea

         //Winner (sem rf, só 2 buds, sem 3atts, com lea):
         //c45                 nb                  5nn                 50nn                svm                 elm                maj
         //0.19278033794162813 0.06298003072196622 0.18586789554531477 0.18279569892473113 0.08218125960061444 0.1804915514592934 0.1359447004608294
         //qtd de metaexemplos: 1302
         //qtd de grupos = 93

      }
   }
}