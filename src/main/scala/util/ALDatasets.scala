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

package util

import java.io.{File, IOException}

import al.strategies.Strategy
import app.db.entities.Dataset
import ml.{Pattern, PatternParent}
import util.Datasets._
import weka.experiment.InstanceQuerySQLite

import scala.collection.JavaConversions._
import scala.util.Left

object ALDatasets {

  /**
   * Reads SQLite patterns in the querying order.
   * It opens a new connection, so it will not be able to open a connection under writing ops.
   */
  def queriesFromSQLite(db: Dataset)(strategy: Strategy, run: Int, fold: Int) = {
    val learner = strategy.learner
    val arq = db.dbCopy
    val queriedInstanceIds = db.exec(s"select q.instid from query as q, app.strategy as s, app.learner as l where run = $run and fold = $fold and q.strategyid=s.rowid and s.name='$strategy' and q.learnerid=l.rowid and l.name='$learner' order by position").get.map(x => x.head.toInt)
    try {
      val query = new InstanceQuerySQLite()
      query.setDatabaseURL("jdbc:sqlite:////" + arq)
      query.setQuery(s"select i.* from query as q, inst as i, app.strategy as s, app.learner as l where q.instid = i.rowid and run = $run and fold = $fold and q.strategyid=s.rowid and s.name='$strategy' and q.learnerid=l.rowid and l.name='$learner' order by position")
      query.setDebug(false)
      val instances = query.retrieveInstances()
      instances.setClassIndex(instances.numAttributes() - 1)
      instances.setRelationName(db.database)
      val parent = PatternParent(instances)
      val patterns = instances.zip(queriedInstanceIds).map { case (instance, idx) => Pattern(idx, instance, false, parent)}
      query.close()
      Right(patterns.toStream)
    } catch {
      case ex: Exception => Left("Problems reading file " + arq + ": " + ex.getMessage + "\n" + ex.getStackTraceString + "\nProblems reading file " + arq + ": " + ex.getMessage)
    }
  }

  /**
   * Reads a SQLite dataset.
   * Assigns the rowid to pattern id.
   */
  def patternsFromSQLite(path: String)(dataset: String) = {

    //get ids
    val db = Dataset(path, createOnAbsence = false, readOnly = true)(dataset)
    db.open()
    val ids = db.exec("select id from i order by id asc").get.map(_.head.toInt)
    db.close()

    val file = path + "/" + dataset + ".db"
    val arq = new File(file)
    println(s"Opening $arq")
    if (!checkExistsForNFS(arq)) Left(s"Dataset file $arq not found!")
    else {
      try {
        val patterns = {
          val query = new InstanceQuerySQLite()
          query.setDatabaseURL("jdbc:sqlite:////" + arq)
          query.setQuery("select * from i order by id asc")
          query.setDebug(false)
          val instances = query.retrieveInstances()
          instances.setClassIndex(instances.numAttributes() - 1)
          instances.setRelationName(dataset)
          val parent = PatternParent(instances)
          val res = instances.zip(ids).map { case (instance, idx) => Pattern(idx, instance, missed = false, parent)}
          query.close()
          res.toVector
        }
        Right(patterns)
      } catch {
        case ex: Exception => Left("Problems reading file " + arq + ": " + ex.getMessage + "\n" + ex.getStackTraceString + "\nProblems reading file " + arq + ": " + ex.getMessage)
      }
    }
  }

  /**
   * Reads ARFF patterns (actually from a generic map) in the querying order (aplying all preprocessing except binarization).
   * Z-score is not applied also.
   * It does not preserve labels to keep consistency with SQLite Weka Saver.
   */
  def queriesFromMap(arffFullPathAndFile: String)(db: Dataset)(strategy: Strategy, run: Int, fold: Int, mapIdToPattern: Map[Int, Pattern]) = {
    val learner = strategy.learner
    val queriedIds = db.exec(s"select q.instid from query as q, app.strategy as s, app.learner as l where run = $run and fold = $fold and q.strategyid=s.rowid and s.name='$strategy' and q.learnerid=l.rowid and l.name='$learner' order by position").get.map(x => x.head.toInt)
    val queries = queriedIds map mapIdToPattern
    Right(queries.toStream)
  }


  /**
   * Create a new Datacontainer (Instances for SVM) converted from Patterns.
   * @param patterns
   * @return
   */
  def patterns2svminstances(patterns: Seq[Pattern]) = if (patterns.isEmpty) {
    println("Empty sequence of patterns; cannot generate Weka Instances object.")
    sys.exit(1)
  } else {
    //    val new_instances = new SvmLibProblem(patterns.head.dataset, 0, 0)
    //    patterns foreach { patt =>
    //      val newInst = new Instance(patt.weight(), patt.array, patt.array.size + 1)
    //      newInst.setDataset(patt.dataset())
    //      new_instances.addInstance()
    //    }
    //    new_instances
  }
}
