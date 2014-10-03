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

package clean.run.uti

import al.strategies.{RandomSampling, Strategy}
import clean.{Ds, Exp}
import ml.Pattern
import weka.filters.Filter

object printPoolSizes extends Exp {
  val arguments = superArguments
  val context = "printPoolSizesapp"
  init()

  def strats(pool: Seq[Pattern], seed: Int) = List(RandomSampling(pool))

  def isAlreadyDone(ds: Ds) = false

  def op(strat: Strategy, ds: Ds, pool: Seq[Pattern], learnerSeed: Int, testSet: Seq[Pattern], run: Int, fold: Int, binaf: Filter, zscof: Filter) = {
    print(s"$run.$fold:${pool.size} ")
  }

  def datasetClosing(ds: Ds) {
    println(s"<-$ds")
  }

  def end(): Unit ={
  }
}