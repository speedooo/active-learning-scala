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

import java.util.Calendar

import al.strategies.{RandomSampling, Strategy}
import ml.Pattern
import ml.classifiers.{NoLearner, Learner}
import util.{ALDatasets, Datasets, Tempo}
import weka.filters.unsupervised.attribute.Standardize

import scala.collection.mutable

/**
 * Cada instancia desta classe representa uma conexao a
 * um arquivo db que é um dataset.
 */
case class Dataset(path: String, createOnAbsence: Boolean = false, readOnly: Boolean = false)(dataset: String) extends Database {
  lazy val nclasses = exec(s"select max(Class)+1 from inst").get.head.head.toInt
  lazy val n = exec(s"select count(*) from inst").get.head.head.toInt
  lazy val countRndStartedPools = exec(s"select * from query where strategyid=1 group by run,fold").get.length
  lazy val countRndPerformedQueries = exec(s"select count(*) from query ${where(RandomSampling(Seq()), NoLearner())}").get.head.head.toInt
  val runs = 5
  val folds = 5
  val database = dataset
  val sidmap = mutable.Map[String, Int]()
  val lidmap = mutable.Map[String, Int]()

  /**
   * Returns only the recorded number of tuples.
   * You should add runs*folds*|Y|²*|Y| manually.
   */
  def rndCompleteHits(learner: Learner) = exec(s"select count(*) from hit ${where(RandomSampling(Seq()), learner)}").get.head.head.toInt

  def where(strategy: Strategy, learner: Learner) = s" where strategyid=${fetchsid(strategy)} and learnerid=${fetchlid(learner)}"

  def fetchlid(learner: Learner) = {
    //Fetch LearnerId by name.
    lazy val lid = try {
      val statement = connection.createStatement()
      val resultSet = statement.executeQuery("select rowid from app.learner where name='" + learner + "'")
      resultSet.next()
      resultSet.getInt("rowid")
    } catch {
      case e: Throwable => e.printStackTrace
        safeQuit("\nProblems consulting learner to insert queries into: " + dbCopy + ".")
    }
    lidmap.getOrElseUpdate(learner.toString, lid)
  }

  def fetchsid(strat: Strategy) = {
    //Fetch StrategyId by name.
    lazy val sid = try {
      val statement = connection.createStatement()
      val resultSet = statement.executeQuery("select rowid from app.strategy where name='" + strat + "'")
      resultSet.next()
      resultSet.getInt("rowid")
    } catch {
      case e: Throwable => e.printStackTrace
        safeQuit("\nProblems consulting strategy to insert queries into: " + dbCopy + " with query \"" + "select rowid from app.strategy where name='" + strat + "'" + "\".")
    }
    sidmap.getOrElseUpdate(strat.toString, sid)
  }

  def saveHits(strat: Strategy, learner: Learner, run: Int, fold: Int, nc: Int, f: Standardize, testSet: Seq[Pattern], seconds: Double, Q: Int = Int.MaxValue) {
    if (exiting()) return //se estava saindo, nem começa novo lote
    if (readOnly) {
      safeQuit("Cannot save queries on a readOnly database!")
    }
    if (!isOpen) {
      println(s"Impossible to get connection to write queries at the run $run and fold $fold for strategy $strat and learner ${strat.learner}. Isso acontece após uma chamada a close() ou na falta de uma chamada a open().")
      sys.exit(1)
    }
    if (!fileLocked) {
      println(s"This thread has is not in charge of locking $database . Impossible to get connection to write queries at the run $run and fold $fold for strategy $strat and learner ${strat.learner}.")
      sys.exit(1)
    }

    val lid = fetchlid(learner)
    val sid = fetchsid(strat)

    //descobre em que ponto das queries retomar os hits
    val nextPos = nextHitPosition(strat, learner, run, fold)
    val timeStep = math.max(nc, nextPos)
    //    if (timeStep==3) println(s"next=3: $strat $learner $run $fold")
    val queries = fetchQueries(strat, run, fold, f)

    //retoma hits
    val initial = queries.take(timeStep)
    val rest = queries.drop(timeStep)
    if (rest.nonEmpty) {
      var model = learner.build(initial)

      //train
      val ti = System.currentTimeMillis()
      val results = mutable.Queue[String]()
      rest.zipWithIndex.take(Q - timeStep).toStream.takeWhile(_ => (System.currentTimeMillis() - ti) / 1000.0 < seconds && !exiting()).foreach {
        case (trainingPattern, idx) =>
          model = learner.update(model, fast_mutable = true)(trainingPattern)
          val confusion = model.confusion(testSet)
          val position = timeStep + idx
          var i = 0
          var j = 0
          while (i < nc) {
            j = 0
            while (j < nc) {
              val sql = s"insert into hit values ($sid, $lid, $run, $fold, $position, $i, $j, ${confusion(i)(j)})"
              results.enqueue(sql)
              j += 1
            }
            i += 1
          }
      }

      //save
      batchWrite(results.toArray)
      println(s"${results.size} hits inserted into $database!")
      if (exiting()) releaseOp() //libera eventual fechamento somente após batchWrite
    }
  }

  def nextHitPosition(strategy: Strategy, learner: Learner, run: Int, fold: Int) = countEvenWhenEmpty(" from hit " + where(strategy, learner) + s" and run=$run and fold=$fold").head.head.toInt

  def fetchQueries(strat: Strategy, run: Int, fold: Int, f: Standardize = null) = {
    acquire()
    val queries = ALDatasets.queriesFromSQLite(this)(strat, run, fold) match {
      case Right(x) => x
      case Left(str) => safeQuit(s"Problem loading queries for Rnd: $str")
    }
    release()
    if (f != null) Datasets.applyFilter(queries, f) else queries
  }

  /**
   * Including first |Y| imaginary matrices.
   * Also checks consistency position-count for the pool.
   */
  def countPerformedConfMatricesForPool(strategy: Strategy, learner: Learner, run: Int, fold: Int) = {
    val c0 = exec(s"select count(*) from hit ${where(strategy, learner)} and run=$run and fold=$fold").get.head.head.toInt / (nclasses * nclasses).toDouble
    val c = if (c0 == 0) 0 else c0 + nclasses
    val m = countEvenWhenEmpty(s" from hit ${where(strategy, learner)} and run=$run and fold=$fold").head.head.toInt
    if (c != m) safeQuit(s"Inconsistency at $strategy / $learner: max position +1 $m at run $run and fold $fold for $dataset differs from number of conf. matrices $c .")
    c
  }

  /**
   * Including first |Y| imaginary matrices.
   * Also checks consistency position-count of each pool.
   */
  def countPerformedConfMatrices(strategy: Strategy, learner: Learner) = {
    val mx_cn = countEvenWhenEmpty(s", count(*)/${nclasses * nclasses}*1.0+$nclasses from hit ${where(strategy, learner)} group by run,fold")
    val m = mx_cn.map(_.head)
    val c = mx_cn.map(_.tail.head)

    if (c != m) safeQuit(s"Inconsistency at $strategy / $learner: sum of max positions +1 \n$m\n for $dataset differs from total number of conf. matrices \n$c\n .")
    c.sum.toInt
  }

  /**
   * Number of started pools for the given strat and its learner.
   * Not necessarily complete ones.
   * @param strategy
   * @return
   */
  def startedPools(strategy: Strategy) =
    exec(s"select * from query ${where(strategy, strategy.learner)} group by run,fold").get.length

  /**
   * Also checks consistency position-count for the pool.
   * @param strategy
   * @param learner
   * @param run
   * @param fold
   * @return
   */
  def countPerformedQueriesForPool(strategy: Strategy, run: Int, fold: Int) = {
    val learner = strategy.learner
    val c = exec(s"select count(*) from query ${where(strategy, learner)} and run=$run and fold=$fold").get.head.head.toInt
    val m = countEvenWhenEmpty(s" from query ${where(strategy, learner)} and run=$run and fold=$fold").head.head.toInt
    if (c != m) safeQuit(s"Inconsistency at $strategy / $learner: max position +1 $m at run $run and fold $fold for $dataset differs from number of queries $c .")
    c
  }

  /**
   * Also checks consistency position-count of each pool.
   * @param strategy
   * @param learner
   * @return
   */
  def countPerformedQueries(strategy: Strategy) = {
    val learner = strategy.learner
    val mx_cn = countEvenWhenEmpty(s", count(*) from query ${where(strategy, learner)} group by run,fold")
    val m = mx_cn.map(_.head)
    val c = mx_cn.map(_.tail.head)

    if (c != m) safeQuit(s"Inconsistency at $strategy / $learner: seq of max positions +1 \n$m\n for $dataset differs from seq of number of queries \n$c\n .")
    c.sum.toInt
  }

  /**
   * Only for specific pool and fold (with where clause or sumarizing with group by).
   * @param s
   * @param offset
   * @return
   */
  def countEvenWhenEmpty(s: String, offset: Int = 0) = {
    //    println(s"select (max(position)+1+$offset) " + s)
    val n = exec("select count(*) " + s).get.map(_.head.toInt).sum
    if (n == 0) mutable.Queue(Seq.fill(5)(0d))
    else exec(s"select (max(position)+1+$offset) " + s).get
  }

  /**
   * Inserts query-tuples (run, fold, position, instid) into database.
   * All queries for a given pair run-fold should be written at once or with the limits below.
   * The original file is updated at the end.
   * If the given pair run/fold already exists, queries are resumed from the last one recorded.
   *
   * Generates queries until Q from the provided strategy,
   * unless Q is not given (in this case all possible queries (the entire pool) will be generated)
   * or a time restriction is specified.
   *
   * filter f is needed to resume queries
   *
   * @param strat
   * @param run
   * @param fold
   * @param seconds time limit, if exceeded, waits for the current query to end and finish querying
   * @param Q
   * @return the total number of queries generated by the strategy along all jobs in this pool
   */
  def saveQueries(strat: Strategy, run: Int, fold: Int, f: Standardize, seconds: Double, Q: Int = Int.MaxValue) =
    if (exiting()) None //se estava saindo, nem começa novo lote
    else {
      if (readOnly) {
        safeQuit("Cannot save queries on a readOnly database!")
      }
      if (!isOpen) {
        println(s"Impossible to get connection to write queries at the run $run and fold $fold for strategy $strat and learner ${strat.learner}. Isso acontece após uma chamada a close() ou na falta de uma chamada a open().")
        sys.exit(1)
      }
      if (!fileLocked) {
        println(s"This thread has is not in charge of locking $database . Impossible to get connection to write queries at the run $run and fold $fold for strategy $strat and learner ${strat.learner}.")
        sys.exit(1)
      }
      val stratId = fetchsid(strat)
      val learnerId = fetchlid(strat.learner)

      //Check if there are more queries than the size of the pool (or greater than Q).
      var q = -1
      try {
        val statement = connection.createStatement()
        val resultSet = statement.executeQuery(s"select count(rowid) as q from query where strategyid=$stratId and learnerid=$learnerId and run=$run and fold=$fold")
        resultSet.next()
        q = resultSet.getInt("q")
        if (q > Q || q > strat.pool.size) {
          //        print("Excess of queries (" + q + ") fetched from dataset " + dbCopy + " for run=" + run + " and fold=" + fold + s" for $strat / ${strat.learner}. They are greater than Q " + Q + s" or pool size ${strat.pool.size}. ")
          //        println("This will be assumed as ok.")
          //        sys.exit(1)
        }
      } catch {
        case e: Throwable => e.printStackTrace
          safeQuit("\nProblems looking for preexistence of queries in: " + dbCopy + ".")
      }

      //Get queries from past jobs, if any.
      val queries = fetchQueries(strat, run, fold, f)
      val nextPosition = queries.size
      val r = if (nextPosition < Q && nextPosition < strat.pool.size) {
        println(s"Gerando queries para $dataset pool: $run / $fold ...")
        val (nextIds, t) = if (nextPosition == 0) Tempo.timev(strat.timeLimitedQueries(seconds, exiting).take(Q).map(_.id).toVector)
        else Tempo.timev(strat.timeLimitedResumeQueries(queries, seconds, exiting).take(Q - nextPosition).map(_.id).toVector)
        q = nextIds.length
        acquire()
        println(s"Gravando queries para $dataset pool: $run / $fold ...")
        var str = ""
        try {
          val statement = connection.createStatement()
          statement.executeUpdate("begin")
          nextIds.zipWithIndex.foreach { case (pattId, idx) =>
            val position = nextPosition + idx
            str = s"insert into query values ($stratId,$learnerId,$run,$fold,$position,$pattId)"
            statement.executeUpdate(str)
          }
          str = s"insert or ignore into time values ($stratId,$learnerId,$run,$fold,0)"
          statement.executeUpdate(str)
          str = s"update time set value = value + $t where strategyid=$stratId and learnerid=$learnerId and run=$run and fold=$fold"
          statement.executeUpdate(str)
          statement.executeUpdate("end")
        } catch {
          case e: Throwable => e.printStackTrace
            println(s"\nProblems inserting queries for $strat / ${strat.learner} into: $dbCopy: [ $str ]:")
            println(e.getMessage)
            safeQuit(s"\nProblems inserting queries for $strat / ${strat.learner} into: $dbCopy: [ $str ].")
        }
        println(s"$q $strat queries written to " + dbCopy + ". Backing up tmpFile...")
        save()
        release()
        if (exiting()) releaseOp() //libera eventual fechamento somente após save()
        nextPosition + q
      } else nextPosition
      Some(r)
    }
}

object DatasetTest extends App {
  //load patterns
  //  val patts = Datasets.patternsFromSQLite("/home/davi/wcs/ucipp/uci/")("iris").right.get

  //reorder patterns as queries
  //  val shuffled = patts.drop(5) ++ patts.take(4)

  //write queries
  val d = Dataset("/home/davi/wcs/ucipp/uci/")("iris")
  d.open(debug = true)
  //  d.saveQueries(RandomSampling(patts), 64, 17, 0.2)

  //load queries as patterns
  val qpatts = ALDatasets.queriesFromSQLite(d)(RandomSampling(Seq()), 0, 0) match {
    case Right(x) => x
    case Left(str) => println(s"Problema: $str"); sys.exit(1)
  }
  d.close()
  qpatts foreach println

}