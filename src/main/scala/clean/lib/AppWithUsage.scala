package clean.lib

import java.security.SecureRandom
import java.util.UUID

import util.XSRandom

import scala.util.Random

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

trait AppWithUsage extends App with Log with ArgParser {
   val language = "pt"
   val NA = -9d
   val superArguments = List("debug-verbosity:-1,0,1,2,...,30", "files-with-dataset-names-or-dataset-names:file1,file2|#d1,d2,d3", "paralleliz(runs folds):r|f|rf|d", "maxtimesteps")
   val arguments: List[String]
   lazy val runs = Global.runs
   lazy val folds = Global.folds
   lazy val debugIntensity = if (args.isEmpty) 20 else args(0).toInt
   lazy val maxQueries0 = args(3).toInt
   lazy val sql = args(4)
   //   lazy val pesadas = args(4).contains("p")
   //   lazy val todas = args(4).contains("t")
   lazy val passivas = args(4).contains("p")
   lazy val porRank = args(5).contains("r")
   lazy val comprimento = args(5)
   lazy val porRisco = args(6).contains("r")
   lazy val dist = args(7)
   lazy val normalizar = args(4).contains("y")
   lazy val path = args(4) + "/"
   lazy val trulyrnd = new SecureRandom()
   lazy val seed = trulyrnd.nextInt() + System.nanoTime() + System.currentTimeMillis() + UUID.randomUUID().toString.map(_.toByte).map(_.toInt).sum
   lazy val xsrnd = {
      val tmp = new XSRandom()
      tmp.setSeed(seed)
      tmp
   }
   lazy val rnd = new Random(xsrnd.nextInt())
   lazy val datasets = if (args(1).startsWith("@")) datasetsFromFiles(args(1).drop(1)).toList
   else rnd.shuffle(if (args(1).startsWith("#")) args(1).drop(1).split(',').toList else datasetsFromFiles(args(1)).toList)
   //  lazy val datasets = if (args(1).startsWith("#")) args(1).drop(1).split(',') else datasetsFromFiles(args(1))
   lazy val parallelRuns = args(2).contains("r")
   lazy val parallelFolds = args(2).contains("f")
   lazy val parallelDatasets = args(2).contains("d")
   lazy val learnerStr = if (args.size < 5) "learner-undefined" else args(4)
   lazy val learnersStr = if (args.size < 5) Array("learners-undefined") else args(4).split(",")
   //  lazy val measure = args.last match {
   //    case "alca" => ALCacc()
   //    case "alcg" => ALCgmeans()
   //    case "aatq" => accAtQ()
   //    case "gatq" => gmeansAtQ()
   //    case "pa" => passiveAcc()
   //    case "pg" => passiveGme()
   //  }
   lazy val memlimit = Global.memlimit
   lazy val attsFromRNames = Seq("AH-conect.-Y", "AH-Dunn-Y", "AH-silhueta-Y", "AH-conect.-1.5Y", "AH-Dunn-1.5Y", "AH-silhueta-1.5Y",
      "AH-conect.-2Y", "AH-Dunn-2Y", "AH-silhueta-2Y", "kM-conect.-Y", "kM-Dunn-Y", "kM-silhueta-Y", "kM-conect.-1.5Y", "kM-Dunn-1.5Y",
      "kM-silhueta-1.5Y", "kM-conect.-2Y", "kM-Dunn-2Y", "kM-silhueta-2Y").map(x => "\"" + x + "\"")
   val nonHumanNumAttsNames = "\"#classes\",\"#atributos\",\"#exemplos\"," +
      "\"#exemplos/#atributos\",\"%nominais\",\"log(#exs)\",\"log(#exs/#atrs)\"," +
      "skewnessesmin,skewavg,skewnessesmax,skewnessesminByskewnessesmax," +
      "kurtosesmin,kurtavg,kurtosesmax,kurtosesminBykurtosesmax," +
      "nominalValuesCountmin,nominalValuesCountAvg,nominalValuesCountmax,nominalValuesCountminBynominalValuesCountmax," +
      "mediasmin,mediasavg,mediasmax,mediasminBymediasmax," +
      "desviosmin,desviosavg,desviosmax,desviosminBydesviosmax," +
      "entropiasmin,entropiasavg,entropiasmax,entropiasminByentropiasmax," +
      "correlsmin,correlsavg,correlsmax,correlsminBycorrelsmax,correleucmah,correleucman,correlmanmah" + attsFromRNames.mkString(",")
   //      "majority,minority,majorityByminority,classEntropy," + attsFromRNames.mkString(",")
   // <- class dependent metaatts
   val humanNumAttsNames = "\"\\\\#classes\",\"\\\\#atributos\",\"\\\\#exemplos\",\"$\\\\frac{\\\\#exemplos}{\\\\#atrib.}$\",\"\\\\%nominais\",\"\\\\%major.\",\"\\\\%minor.\",\"$\\\\frac{\\\\%major.}{\\\\%minor.}$\",\"entropia da distr. de classes\""
   //   val descriptionNames = Seq("""\pbox{20cm}{\#exemplos\\($|\mathcal{U}|$)}""", """\pbox{20cm}{\#classes\\($|Y|$)}""", "\\#atributos", "\\#nominais", "\\%majoritária", "\\%minoritária", """\pbox{20cm}{entropia da \\distr. de classes}""")
   val descriptionNames = Seq( """$|\mathcal{U}|$""", """$|Y|$""", "atributos", "nominais", """\makecell{majoritária\\(\%)}""", """\makecell{minoritária\\(\%)}""", """\makecell{entropia da \\distr. de classes}""")

   def ff(precision: Double)(x: Double) = (x * precision).round / precision

   def maxQueries(ds: Ds) = math.max(ds.nclasses, math.min(ds.expectedPoolSizes(folds).min, maxQueries0))

   def memoryMonitor() = {
      Global.running = true
      new Thread(new Runnable() {
         def run() {
            while (Global.running) {
               //60s
               1 to 300 takeWhile { _ =>
                  Thread.sleep(200)
                  Global.running
               }
               log(s"Memory usage: ${Runtime.getRuntime.totalMemory() / 1000000d}MB.", 30)
               //          if (Runtime.getRuntime.totalMemory() / 1000000d > memlimit) {
               //            Global.running = false
               //            error(s"Limite de $memlimit MB de memoria atingido.")
               //          }
            }
            log("Saiu do monitoramento de memória.", 30)
         }
      }).start()
   }

   def run() {
      try {
         Global.debug = debugIntensity
         println(args.mkString(" "))
         if (args.size != arguments.size) {
            println(s"Usage: java -cp your-path/als-version.jar ${this.getClass.getCanonicalName.dropRight(1)} ${arguments.mkString(" ")}")
            sys.exit(1)
         }
      } catch {
         case ex: Throwable => Global.running = false
            ex.printStackTrace()
            justQuit("Erro: " + ex.getMessage)
      }
   }

   def renomeia(ds: Ds) = {
      val reticencias = if (ds.dataset.size > 18) "..." else ""
      val name = ds.dataset.take(5) match {
         case "heart" => ds.dataset.replace("processed-", "")
         case "conne" => ds.dataset.replace("connectionist", "connect.")
         case _ => ds.dataset
      }
      name.take(18).split("-").mkString(" ") + reticencias
   }
}
